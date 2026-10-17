package vegabobo.dsusideloader.ui.screen.adb

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import vegabobo.dsusideloader.model.Session

@HiltViewModel
class AdbViewModel @Inject constructor(
    private val session: Session,
) : ViewModel() {

    fun obtainScriptPath(): String = session.installationScriptPath

    fun obtainScriptContent(): String {
        val path = obtainScriptPath()
        return try {
            if (path.isNotEmpty()) java.io.File(path).readText() else ""
        } catch (e: Exception) {
            ""
        }
    }
}
