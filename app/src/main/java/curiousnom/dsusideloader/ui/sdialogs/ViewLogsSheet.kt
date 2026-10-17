package curiousnom.dsusideloader.ui.sdialogs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.ui.cards.LogcatCard
import curiousnom.dsusideloader.ui.components.CustomBottomSheet
import curiousnom.dsusideloader.ui.components.buttons.PrimaryButton
import curiousnom.dsusideloader.ui.components.buttons.SecondaryButton
import curiousnom.dsusideloader.ui.util.launcherAcResult

@Composable
fun ViewLogsBottomSheet(
    logs: String,
    onClickSaveLogs: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val logsSavedText = stringResource(id = R.string.saved_logs)

    val saveLogsResult = launcherAcResult {
        onClickSaveLogs(it)
        Toast.makeText(context, logsSavedText, Toast.LENGTH_SHORT).show()
    }

    CustomBottomSheet(
        title = stringResource(id = R.string.installation_logs),
        icon = Icons.Outlined.Description,
        onDismiss = onDismiss,
    ) {
        LogcatCard(logs = logs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            SecondaryButton(
                text = stringResource(id = R.string.share_text),
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, logs)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
            )
            Spacer(modifier = Modifier.weight(1F))
            PrimaryButton(
                text = stringResource(id = R.string.save_logs),
                onClick = {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TITLE, "logs")
                    saveLogsResult.launch(intent)
                },
            )
        }
    }
}
