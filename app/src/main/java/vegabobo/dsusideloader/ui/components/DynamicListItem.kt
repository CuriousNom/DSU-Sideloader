package vegabobo.dsusideloader.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun DynamicListItem(
    listLength: Int,
    currentValue: Int,
    content: @Composable () -> Unit,
) {
    val cornerLarge = 20.dp
    val cornerSmall = 4.dp
    val shape = when {
        listLength == 0 -> RoundedCornerShape(cornerLarge)
        currentValue == 0 -> RoundedCornerShape(
            topStart = cornerLarge,
            topEnd = cornerLarge,
            bottomStart = cornerSmall,
            bottomEnd = cornerSmall,
        )
        currentValue == listLength -> RoundedCornerShape(
            topStart = cornerSmall,
            topEnd = cornerSmall,
            bottomStart = cornerLarge,
            bottomEnd = cornerLarge,
        )
        else -> RoundedCornerShape(cornerSmall)
    }
    CardBox(
        addPadding = false,
        roundedCornerShape = shape,
    ) {
        content()
    }
}
