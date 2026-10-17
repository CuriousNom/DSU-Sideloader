package curiousnom.dsusideloader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import curiousnom.dsusideloader.model.Session
import curiousnom.dsusideloader.preferences.AppPrefs
import curiousnom.dsusideloader.service.PrivilegedProvider
import curiousnom.dsusideloader.service.PrivilegedRootService
import curiousnom.dsusideloader.service.PrivilegedService
import curiousnom.dsusideloader.service.PrivilegedSystemService
import curiousnom.dsusideloader.ui.screen.Navigation
import curiousnom.dsusideloader.ui.theme.DSUHelperTheme
import curiousnom.dsusideloader.util.OperationMode
import curiousnom.dsusideloader.util.OperationModeUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {

    @Inject
    lateinit var session: Session

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private val tag = this.javaClass.simpleName

    private var shouldCheckShizuku = false

    private fun setupSessionOperationMode() {
        val operationMode = OperationModeUtils.getOperationMode(application, shouldCheckShizuku)
        session.setOperationMode(operationMode)
        Log.d(tag, "Operation mode is: $operationMode")
    }

    //
    // Shizuku
    //

    val userServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, PrivilegedService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private val SHIZUKU_REQUEST_CODE = 1000
    private val REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionResult

    private fun addShizukuListeners() {
        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED_LISTENER)
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }

    private fun removeShizukuListeners() {
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
        Shizuku.removeBinderReceivedListener(BINDER_RECEIVED_LISTENER)
    }

    private val BINDER_RECEIVED_LISTENER = Shizuku.OnBinderReceivedListener {
        if (!OperationModeUtils.isShizukuPermissionGranted(this)) {
            askShizukuPermission()
            return@OnBinderReceivedListener
        }
        bindShizuku()
    }

    private fun askShizukuPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            requestPermissions(arrayOf(ShizukuProvider.PERMISSION), SHIZUKU_REQUEST_CODE)
        } else {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED && requestCode == SHIZUKU_REQUEST_CODE) {
            bindShizuku()
        }
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }

    fun bindShizuku() {
        Shizuku.bindUserService(userServiceArgs, PrivilegedProvider.connection)
        shouldCheckShizuku = true
        setupSessionOperationMode()
    }

    //
    // Root
    //

    companion object {
        init {
            // Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10),
            )
        }
    }

    private fun setupService() {
        if (session.isRoot()) {
            val privRootService = Intent(this, PrivilegedRootService::class.java)
            RootService.bind(privRootService, PrivilegedProvider.connection)
            return
        }

        if (session.getOperationMode() == OperationMode.SYSTEM) {
            val service = Intent(this, PrivilegedSystemService::class.java)
            bindService(service, PrivilegedProvider.connection, Context.BIND_AUTO_CREATE)
            return
        }

        addShizukuListeners()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shell.getShell {}
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            DSUHelperTheme {
                val hapticsEnabled by dataStore.data.map { preferences ->
                    preferences[booleanPreferencesKey(AppPrefs.HAPTIC_FEEDBACK)] ?: true
                }.collectAsState(initial = true)

                val systemHaptic = LocalHapticFeedback.current
                val customHaptic = remember(hapticsEnabled, systemHaptic) {
                    object : HapticFeedback {
                        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                            if (hapticsEnabled) {
                                systemHaptic.performHapticFeedback(hapticFeedbackType)
                            }
                        }
                    }
                }

                CompositionLocalProvider(LocalHapticFeedback provides customHaptic) {
                    Navigation()
                }
            }
        }

        if (savedInstanceState == null) {
            setupSessionOperationMode()
            setupService()
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        HiddenApiBypass.addHiddenApiExemptions("")
        super.attachBaseContext(newBase)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isChangingConfigurations) {
            return
        }
        when (session.getOperationMode()) {
            OperationMode.ROOT, OperationMode.SYSTEM_AND_ROOT ->
                RootService.unbind(PrivilegedProvider.connection)

            OperationMode.SYSTEM ->
                applicationContext.unbindService(PrivilegedProvider.connection)

            OperationMode.SHIZUKU -> {
                removeShizukuListeners()
                Shizuku.unbindUserService(userServiceArgs, PrivilegedProvider.connection, true)
            }

            else -> {}
        }
    }
}
