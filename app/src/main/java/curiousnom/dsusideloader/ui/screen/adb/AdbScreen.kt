package curiousnom.dsusideloader.ui.screen.adb

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.ui.cards.CopyableTextCard
import curiousnom.dsusideloader.ui.components.ApplicationScreen
import curiousnom.dsusideloader.ui.components.SimpleCard
import curiousnom.dsusideloader.ui.components.TopBar
import curiousnom.dsusideloader.ui.components.buttons.PrimaryButton
import curiousnom.dsusideloader.ui.screen.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbScreen(
    navigate: (String) -> Unit,
    adbViewModel: AdbViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scriptPath = adbViewModel.obtainScriptPath()
    val scriptContent = adbViewModel.obtainScriptContent()

    val startInstallationCommand = "sh \"$scriptPath\""
    val startInstallationCommandAdb = "adb shell $startInstallationCommand"
    ApplicationScreen(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        topBar = {
            TopBar(
                barTitle = stringResource(id = R.string.installation),
                scrollBehavior = it,
                onClickIcon = { navigate(Destinations.Preferences) },
                onClickBackButton = { navigate(Destinations.Up) },
            )
        },
        content = {
            Text(
                text = stringResource(id = R.string.adb_how_to_adb_shell),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CopyableTextCard(text = startInstallationCommandAdb)
            Text(
                text = stringResource(id = R.string.adb_how_to_shell),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CopyableTextCard(text = startInstallationCommand)
            if (scriptContent.isNotEmpty()) {
                SimpleCard(
                    cardTitle = stringResource(id = R.string.share_script_title),
                    text = stringResource(id = R.string.share_script_description),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.weight(1F))
                        PrimaryButton(
                            text = stringResource(id = R.string.share_text),
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, scriptContent)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                        )
                    }
                }
            }
            Text(
                text = stringResource(id = R.string.adb_how_to_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
