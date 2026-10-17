package curiousnom.dsusideloader.installer.adb

import curiousnom.dsusideloader.core.StorageManager
import curiousnom.dsusideloader.model.Session

/**
 * Generate shell script with installation
 * Used only for installing over adb commands
 */
class AdbInstallationHandler(
    private val storageManager: StorageManager,
    val session: Session,
) {
    fun generate(onGenerated: (String) -> Unit) {
        val installationScriptPath = GenerateInstallationScript(
            storageManager,
            session.getInstallationParameters(),
            session.preferences,
        ).writeToFile()
        onGenerated(installationScriptPath)
    }
}
