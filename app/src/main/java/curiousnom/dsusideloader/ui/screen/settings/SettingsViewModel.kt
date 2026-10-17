package curiousnom.dsusideloader.ui.screen.settings

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import curiousnom.dsusideloader.core.BaseViewModel
import curiousnom.dsusideloader.core.SlotPartitionManager
import curiousnom.dsusideloader.model.Session
import curiousnom.dsusideloader.preferences.AppPrefs
import curiousnom.dsusideloader.util.OperationMode
import curiousnom.dsusideloader.util.OperationModeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    override val dataStore: DataStore<Preferences>,
    private val session: Session,
    val application: Application,
) : BaseViewModel(dataStore) {

    private val tag = this.javaClass.simpleName

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun reloadPreferences() {
        viewModelScope.launch {
            val hasPrefFlashed = readBoolPref(AppPrefs.FLASHED_SECONDARY_BOOT_SLOT, default = false)
            val hasPhysicalSlot = hasPrefFlashed || SlotPartitionManager.hasSecondaryPhysicalSlot()
            val isDualBoot = hasPhysicalSlot || SlotPartitionManager.isAbDevice()

            val cloneMap = hashMapOf<String, Boolean>()
            cloneMap.putAll(uiState.value.preferences)
            uiState.value.preferences.keys.forEach { key ->
                val defaultVal = uiState.value.preferences[key] ?: false
                cloneMap[key] = readBoolPref(key, default = defaultVal)
            }

            _uiState.update {
                it.copy(
                    preferences = cloneMap,
                    isRoot = session.isRoot(),
                    isStickyEnforced = hasPhysicalSlot,
                    isDualBooting = isDualBoot,
                )
            }
        }
    }

    init {
        reloadPreferences()
    }

    fun togglePreference(preference: String, value: Boolean) {
        if (preference == AppPrefs.STICKY_DSU && uiState.value.isStickyEnforced) {
            return
        }
        viewModelScope.launch {
            updateBoolPref(preference, value) {
                _uiState.update {
                    val cloneMap = hashMapOf<String, Boolean>()
                    cloneMap.putAll(uiState.value.preferences)
                    cloneMap[preference] = value
                    Log.d(tag, "preference: $preference, isEnabled: $value")
                    it.copy(preferences = cloneMap)
                }
            }
        }
    }

    fun isAndroidQ(): Boolean = Build.VERSION.SDK_INT == 29

    fun updateSheetDisplay(sheet: DialogSheetState) {
        _uiState.update { it.copy(dialogSheetDisplay = sheet) }
    }

    fun checkOperationMode(): String {
        return OperationModeUtils.getOperationModeAsString(session.getOperationMode())
    }

    fun getOperationMode(): OperationMode {
        return session.getOperationMode()
    }

    fun checkDevOpt() {
        viewModelScope.launch {
            val isDevOptEnabled = readBoolPref(AppPrefs.DEVELOPER_OPTIONS)
            _uiState.update { it.copy(isDevOptEnabled = isDevOptEnabled) }
            reloadPreferences()
        }
    }
}
