package curiousnom.dsusideloader.ui.cards.updater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import curiousnom.dsusideloader.BuildConfig
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.ui.components.PreferenceItem
import curiousnom.dsusideloader.ui.components.SimpleCard
import curiousnom.dsusideloader.ui.components.buttons.PrimaryButton
import curiousnom.dsusideloader.ui.components.buttons.SecondaryButton
import curiousnom.dsusideloader.ui.screen.about.UpdateStatus
import curiousnom.dsusideloader.ui.screen.about.UpdaterCardState

@Composable
fun UpdaterCard(
    uiState: UpdaterCardState,
    isUpdaterAvailable: Boolean,
    onClickImage: () -> Unit,
    onClickCheckUpdates: () -> Unit,
    onClickDownloadUpdate: () -> Unit,
    onClickViewChangelog: () -> Unit,
) {
    fun isDownloading(): Boolean =
        uiState.isDownloading || uiState.updateStatus == UpdateStatus.CHECKING_FOR_UPDATES

    fun isCheckingForUpdates(): Boolean =
        uiState.updateStatus == UpdateStatus.CHECKING_FOR_UPDATES

    fun isUpdateFound(): Boolean =
        uiState.updateStatus == UpdateStatus.UPDATE_FOUND

    SimpleCard(
        addPadding = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val progressBarModifier = Modifier
                    .size(104.dp)
                    .align(Alignment.Center)
                if (isCheckingForUpdates()) {
                    CircularProgressIndicator(
                        modifier = progressBarModifier,
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }
                if (uiState.isDownloading) {
                    CircularProgressIndicator(
                        progress = uiState.progressBar,
                        modifier = progressBarModifier,
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }

                val selected = remember { mutableStateOf(false) }
                val scale = animateFloatAsState(if (selected.value) 0.85f else 1f)
                selected.value = isDownloading()

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(scale.value)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onClickImage() }
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.app_icon_mono),
                        contentDescription = "App icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(88.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Surface(
                shape = RoundedCornerShape(100),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(
                        id = R.string.version_info,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.padding(4.dp))

        if (isUpdaterAvailable) {
            PreferenceItem(
                title = stringResource(id = R.string.check_updates_title),
                description =
                when (uiState.updateStatus) {
                    UpdateStatus.NO_UPDATE_FOUND ->
                        stringResource(id = R.string.check_updates_text_updated)

                    UpdateStatus.UPDATE_FOUND ->
                        stringResource(R.string.check_updates_text_found, uiState.updateVersion)

                    else ->
                        stringResource(id = R.string.check_updates_text_idle)
                },
                onClick = { onClickCheckUpdates() },
            )
            AnimatedVisibility(visible = isUpdateFound()) {
                Row(
                    modifier = Modifier
                        .padding(all = 16.dp),
                ) {
                    Spacer(modifier = Modifier.weight(1F))
                    SecondaryButton(
                        text = stringResource(id = R.string.changelog),
                        onClick = { onClickViewChangelog() },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    PrimaryButton(
                        text = stringResource(id = R.string.download),
                        onClick = { onClickDownloadUpdate() },
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.padding(6.dp))
        }
    }
}
