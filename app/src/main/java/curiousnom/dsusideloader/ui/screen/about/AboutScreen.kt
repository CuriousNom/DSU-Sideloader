package curiousnom.dsusideloader.ui.screen.about

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.ui.cards.updater.UpdaterCard
import curiousnom.dsusideloader.ui.components.ApplicationScreen
import curiousnom.dsusideloader.ui.components.PreferenceItem
import curiousnom.dsusideloader.ui.components.SimpleCard
import curiousnom.dsusideloader.ui.components.Title
import curiousnom.dsusideloader.ui.components.TopBar
import curiousnom.dsusideloader.ui.screen.Destinations
import curiousnom.dsusideloader.util.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

object AboutLinks {
    const val CONTRIBUTORS_URL = "https://github.com/VegaBobo/DSU-Sideloader/graphs/contributors"
    const val REPOSITORY_URL = "https://github.com/VegaBobo/DSU-Sideloader"
    const val WSTXDA_GITHUB = "https://github.com/WSTxda"
    const val VEGABOBO_GITHUB = "https://github.com/VegaBobo"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navigate: (String) -> Unit,
    aboutViewModel: AboutViewModel = hiltViewModel(),
) {
    val uiState by aboutViewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        aboutViewModel.resetDeveloperOptionsCounter()
        uiState.toastDisplay.collectLatest {
            when (it) {
                DevOptToastDisplay.ENABLED_DEV_OPT ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.developer_options_enabled),
                        Toast.LENGTH_LONG,
                    ).show()

                DevOptToastDisplay.DISABLED_DEV_OPT ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.developer_options_disabled),
                        Toast.LENGTH_LONG,
                    ).show()

                else -> {}
            }
        }
    }

    ApplicationScreen(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        topBar = {
            TopBar(
                barTitle = stringResource(id = R.string.about),
                scrollBehavior = it,
                onClickBackButton = { navigate(Destinations.Up) },
            )
        },
    ) {
        UpdaterCard(
            uiState = uiState.updaterCardState,
            isUpdaterAvailable = uiState.isUpdaterAvailable,
            onClickImage = { aboutViewModel.onClickImage() },
            onClickCheckUpdates = { aboutViewModel.onClickCheckUpdates() },
            onClickDownloadUpdate = { aboutViewModel.onClickDownloadUpdate() },
            onClickViewChangelog = { uriHandler.openUri(aboutViewModel.response.changelogUrl) },
        )
        Title(
            stringResource(id = R.string.application),
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
        SimpleCard(
            addPadding = false,
        ) {
            PreferenceItem(
                title = stringResource(id = R.string.github_repo),
                description = stringResource(id = R.string.github_repo_description),
                showDivider = true,
                onClick = { uriHandler.openUri(AboutLinks.REPOSITORY_URL) },
            )
            PreferenceItem(
                title = stringResource(id = R.string.libraries_title),
                description = stringResource(id = R.string.libraries_description),
                onClick = { navigate(Destinations.Libraries) },
            )
        }
        Title(
            stringResource(id = R.string.device_info),
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
        SimpleCard(
            addPadding = false,
        ) {
            PreferenceItem(
                title = stringResource(id = R.string.kernel_page_size),
                description = stringResource(
                    id = R.string.kernel_page_size_desc,
                    curiousnom.dsusideloader.util.DevicePropUtils.getKernelPageSizeFormatted(),
                ),
            )
        }
        Title(
            stringResource(id = R.string.collaborators),
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
        SimpleCard(
            addPadding = false,
        ) {
            PreferenceItem(
                title = "VegaBobo",
                description = stringResource(id = R.string.role_developer),
                showDivider = true,
                onClick = { uriHandler.openUri(AboutLinks.VEGABOBO_GITHUB) },
            )
            PreferenceItem(
                title = "WSTxda",
                description = stringResource(id = R.string.role_design_icon),
                showDivider = true,
                onClick = { uriHandler.openUri(AboutLinks.WSTXDA_GITHUB) },
            )
            val translators = stringResource(id = R.string.translators_list)
            if (translators.isNotEmpty() && translators != "translators_list") {
                PreferenceItem(
                    title = stringResource(id = R.string.translators_title),
                    description = stringResource(id = R.string.translators_list),
                    showDivider = true,
                )
            }
            PreferenceItem(
                title = stringResource(id = R.string.contributors_title),
                description = stringResource(id = R.string.contributors_text),
                onClick = { uriHandler.openUri(AboutLinks.CONTRIBUTORS_URL) },
            )
        }
    }
}
