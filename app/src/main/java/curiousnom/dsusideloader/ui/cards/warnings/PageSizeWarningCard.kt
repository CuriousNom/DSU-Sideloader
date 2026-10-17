package curiousnom.dsusideloader.ui.cards.warnings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.ui.components.SimpleCard
import curiousnom.dsusideloader.ui.components.buttons.SecondaryButton

@Composable
fun PageSizeWarningCard(
    onClickProceed: () -> Unit = {},
) {
    SimpleCard(
        modifier = Modifier.fillMaxWidth(),
        cardTitle = stringResource(id = R.string.page_size_16kb_warn),
        text = stringResource(id = R.string.page_size_16kb_warn_desc),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Row {
                SecondaryButton(
                    text = stringResource(id = R.string.proceed),
                    onClick = onClickProceed,
                )
            }
        }
    }
}
