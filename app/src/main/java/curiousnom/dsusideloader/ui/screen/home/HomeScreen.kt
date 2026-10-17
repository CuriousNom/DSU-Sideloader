package curiousnom.dsusideloader.ui.screen.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.ui.cards.DsuInfoCard
import curiousnom.dsusideloader.ui.cards.DualBootCard
import curiousnom.dsusideloader.ui.cards.ImageSizeCard
import curiousnom.dsusideloader.ui.cards.UserdataCard
import curiousnom.dsusideloader.ui.cards.installation.InstallationCard
import curiousnom.dsusideloader.ui.cards.warnings.GrantingPermissionCard
import curiousnom.dsusideloader.ui.cards.warnings.PageSizeWarningCard
import curiousnom.dsusideloader.ui.cards.warnings.RequiresLogPermissionCard
import curiousnom.dsusideloader.ui.cards.warnings.SetupStorage
import curiousnom.dsusideloader.ui.cards.warnings.StorageWarningCard
import curiousnom.dsusideloader.ui.cards.warnings.UnlockedBootloaderCard
import curiousnom.dsusideloader.ui.cards.warnings.UnsupportedCard
import curiousnom.dsusideloader.ui.components.ApplicationScreen
import curiousnom.dsusideloader.ui.components.TopBar
import curiousnom.dsusideloader.ui.screen.Destinations
import curiousnom.dsusideloader.ui.sdialogs.CancelSheet
import curiousnom.dsusideloader.ui.sdialogs.ConfirmInstallationSheet
import curiousnom.dsusideloader.ui.sdialogs.DiscardDSUSheet
import curiousnom.dsusideloader.ui.sdialogs.FastbootInstructionsSheet
import curiousnom.dsusideloader.ui.sdialogs.ImageSizeWarningSheet
import curiousnom.dsusideloader.ui.sdialogs.ViewLogsBottomSheet
import curiousnom.dsusideloader.ui.util.KeepScreenOn
import curiousnom.dsusideloader.util.collectAsStateWithLifecycle
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.collectLatest

object HomeLinks {
    const val DSU_LEARN_MORE = "https://developer.android.com/topic/dsu"
    const val DSU_DOCS = "https://source.android.com/devices/tech/ota/dynamic-system-updates"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    navigate: (String) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    val operationMode by homeViewModel.session.operationMode.collectAsStateWithLifecycle()
    val modePill = remember(operationMode) {
        when (operationMode) {
            curiousnom.dsusideloader.util.OperationMode.SYSTEM_AND_ROOT -> "SYSTEM • ROOT"
            curiousnom.dsusideloader.util.OperationMode.SYSTEM -> "SYSTEM"
            curiousnom.dsusideloader.util.OperationMode.ROOT -> "ROOT"
            curiousnom.dsusideloader.util.OperationMode.SHIZUKU -> "SHIZUKU"
            curiousnom.dsusideloader.util.OperationMode.ADB -> "ADB"
        }
    }

    val minPercentageOfFreeStorage = remember(homeViewModel.allocPercentageInt) {
        homeViewModel.allocPercentageInt.toString()
    }

    if (uiState.shouldKeepScreenOn) {
        KeepScreenOn()
    }

    LaunchedEffect(Unit) {
        homeViewModel.setupUserPreferences()
        homeViewModel.session.operationMode.collectLatest {
            homeViewModel.initialChecks()
        }
    }

    ApplicationScreen(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        topBar = {
            TopBar(
                barTitle = stringResource(id = R.string.app_name),
                statusBadge = modePill,
                icon = Icons.Outlined.Settings,
                scrollBehavior = it,
                onClickIcon = { navigate(Destinations.Preferences) },
            )
        },
        content = {
            Box(modifier = Modifier.animateContentSize()) {
                when (uiState.additionalCard) {
                    AdditionalCardState.NO_DYNAMIC_PARTITIONS ->
                        UnsupportedCard(
                            onClickClose = { exitProcess(0) },
                            onClickContinueAnyway = homeViewModel::overrideDynamicPartitionCheck,
                        )

                    AdditionalCardState.SETUP_STORAGE ->
                        SetupStorage(homeViewModel::takeUriPermission)

                    AdditionalCardState.UNAVAIABLE_STORAGE ->
                        StorageWarningCard(
                            minPercentageFreeStorage = minPercentageOfFreeStorage,
                            onClick = homeViewModel::overrideUnavaiableStorage,
                        )

                    AdditionalCardState.MISSING_READ_LOGS_PERMISSION ->
                        RequiresLogPermissionCard(
                            onClickGrant = homeViewModel::grantReadLogs,
                            onClickRefuse = homeViewModel::refuseReadLogs,
                        )

                    AdditionalCardState.GRANTING_READ_LOGS_PERMISSION ->
                        GrantingPermissionCard()

                    AdditionalCardState.BOOTLOADER_UNLOCKED_WARNING ->
                        UnlockedBootloaderCard(homeViewModel::onClickBootloaderUnlockedWarning)

                    AdditionalCardState.PAGE_SIZE_16KB_WARNING ->
                        PageSizeWarningCard(homeViewModel::onClick16KbWarning)

                    AdditionalCardState.NONE -> {}
                }
            }
            if (uiState.passedInitialChecks && uiState.additionalCard == AdditionalCardState.NONE) {
                if (uiState.isDualBooting) {
                    DualBootCard(
                        isDsuRunning = uiState.isDsuRunning,
                        isDsuInstalled = uiState.isDsuInstalled,
                        isStickyDsu = uiState.isStickyDsu,
                        isStickyEnforced = uiState.isStickyEnforced,
                        isRoot = uiState.isRoot,
                        onToggleStickyDsu = homeViewModel::toggleStickyDsu,
                        onClickRebootToDynOS = homeViewModel::onClickRebootToDynOS,
                        onClickRebootToHostOS = homeViewModel::onClickRebootToHostOS,
                        onClickDiscardDsu = homeViewModel::showDiscardSheet,
                    )
                }
                InstallationCard(
                    uiState = uiState.installationCard,
                    isDualBooting = uiState.isDualBooting,
                    onClickInstall = homeViewModel::onClickInstall,
                    onClickUnmountSdCardAndRetry = homeViewModel::onClickUnmountSdCardAndRetry,
                    onClickSetSeLinuxPermissive = homeViewModel::onClickSetSeLinuxPermissive,
                    onClickRetryInstallation = homeViewModel::onClickRetryInstallation,
                    onClickClear = homeViewModel::resetInstallationCard,
                    onSelectFileSuccess = homeViewModel::onFileSelectionResult,
                    onClickCancelInstallation = homeViewModel::onClickCancel,
                    onClickDiscardInstalledGsiAndInstall = homeViewModel::onClickDiscardGsiAndStartInstallation,
                    onClickDiscardDsu = homeViewModel::showDiscardSheet,
                    onClickRebootToDynOS = homeViewModel::onClickRebootToDynOS,
                    onClickRebootToHostOS = homeViewModel::onClickRebootToHostOS,
                    onClickViewLogs = homeViewModel::showLogsWarning,
                    onClickViewCommands = { navigate(Destinations.ADBInstallation) },
                    minPercentageOfFreeStorage = minPercentageOfFreeStorage,
                )
                UserdataCard(
                    isEnabled = uiState.isInstalling(),
                    uiState = uiState.userDataCard,
                    onCheckedChange = { homeViewModel.onCheckUserdataCard() },
                    onValueChange = homeViewModel::updateUserdataSize,
                )
                ImageSizeCard(
                    isEnabled = uiState.isInstalling(),
                    uiState = uiState.imageSizeCard,
                    onCheckedChange = { homeViewModel.onCheckImageSizeCard() },
                    onValueChange = homeViewModel::updateImageSize,
                )
                DsuInfoCard(
                    onClickViewDocs = { uriHandler.openUri(HomeLinks.DSU_DOCS) },
                    onClickLearnMore = { uriHandler.openUri(HomeLinks.DSU_LEARN_MORE) },
                )
            }
        },
    )

    when (uiState.sheetDisplay) {
        SheetDisplayState.CONFIRM_INSTALLATION ->
            ConfirmInstallationSheet(
                filename = homeViewModel.obtainSelectedFilename(),
                userdata = homeViewModel.session.userSelection.getUserDataSizeAsGB(),
                fileSize = homeViewModel.session.userSelection.userSelectedImageSize,
                imageFormat = uiState.installationCard.imageFormatInfo,
                onClickConfirm = { homeViewModel.onConfirmInstallationSheet() },
                onClickCancel = { homeViewModel.dismissSheet() },
            )

        SheetDisplayState.CANCEL_INSTALLATION ->
            CancelSheet(
                onClickConfirm = { homeViewModel.onClickCancelInstallationButton() },
                onClickCancel = { homeViewModel.dismissSheet() },
            )

        SheetDisplayState.IMAGESIZE_WARNING ->
            ImageSizeWarningSheet(
                onClickConfirm = { homeViewModel.dismissSheet() },
                onClickCancel = { homeViewModel.onCheckImageSizeCard() },
            )

        SheetDisplayState.DISCARD_DSU ->
            DiscardDSUSheet(
                onClickConfirm = { homeViewModel.onClickDiscardGsi() },
                onClickCancel = { homeViewModel.dismissSheet() },
            )

        SheetDisplayState.VIEW_LOGS ->
            ViewLogsBottomSheet(
                logs = uiState.installationLogs,
                onClickSaveLogs = { homeViewModel.saveLogs(it) },
                onDismiss = { homeViewModel.dismissSheet() },
            )

        SheetDisplayState.FASTBOOT_INSTRUCTIONS ->
            FastbootInstructionsSheet(
                onDismiss = { homeViewModel.dismissSheet() },
            )

        SheetDisplayState.NONE -> {}
    }
}
