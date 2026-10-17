package curiousnom.dsusideloader.ui.components.buttons

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    colorButton: Color? = null,
    colorText: Color? = null,
    textButton: Boolean = false,
    isEnabled: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    if (textButton) {
        TextButton(
            modifier = modifier,
            onClick = onClick,
            enabled = isEnabled,
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Medium,
            )
        }
    } else {
        val buttonColors = if (colorButton != null) {
            ButtonDefaults.buttonColors(
                containerColor = colorButton,
                contentColor = colorText ?: MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = colorText ?: MaterialTheme.colorScheme.onPrimary,
            )
        }
        Button(
            modifier = modifier,
            onClick = onClick,
            enabled = isEnabled,
            shape = RoundedCornerShape(100),
            colors = buttonColors,
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
            content()
        }
    }
}
