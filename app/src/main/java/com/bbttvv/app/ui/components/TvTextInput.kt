package com.bbttvv.app.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun TvTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = if (singleLine) 1 else 3,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
    onSubmit: (() -> Unit)? = null,
    onMoveFocusUp: (() -> Boolean)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color = Color(0x161E2732),
    focusedContainerColor: Color = Color(0xF2EEF6FB),
    contentColor: Color = Color(0xFFEAF2F8),
    focusedContentColor: Color = Color(0xFF101318),
    focusedScale: Float = 1.0f,
    minHeight: Dp = if (singleLine) 48.dp else 96.dp,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 12.dp,
    leadingContent: (@Composable (Color) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    fun leaveEditing() {
        editing = false
        keyboardController?.hide()
    }

    fun submitAndLeave() {
        onSubmit?.invoke()
        leaveEditing()
    }

    val fieldContainerColor by animateColorAsState(
        targetValue = if (focused) focusedContainerColor else containerColor,
        animationSpec = tween(durationMillis = 150),
        label = "tvTextInputContainer"
    )
    val fieldContentColor by animateColorAsState(
        targetValue = if (focused) focusedContentColor else contentColor,
        animationSpec = tween(durationMillis = 150),
        label = "tvTextInputContent"
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "tvTextInputScale"
    )
    val borderColor = if (focused) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.14f)

    Column(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        label?.let { text ->
            Text(
                text = text,
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            readOnly = !editing,
            textStyle = TextStyle(
                color = fieldContentColor,
                fontSize = 16.sp,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(fieldContentColor),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { submitAndLeave() },
                onSearch = { submitAndLeave() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    focused = focusState.isFocused || focusState.hasFocus
                    if (!focused) {
                        leaveEditing()
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) {
                        return@onPreviewKeyEvent false
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (!editing) {
                                editing = true
                                keyboardController?.show()
                                true
                            } else if (singleLine && onSubmit != null) {
                                submitAndLeave()
                                true
                            } else {
                                false
                            }
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            leaveEditing()
                            onMoveFocusUp?.invoke() ?: focusManager.moveFocus(FocusDirection.Up)
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            leaveEditing()
                            focusManager.moveFocus(FocusDirection.Down)
                        }

                        else -> false
                    }
                },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .background(fieldContainerColor, shape)
                        .border(1.dp, borderColor, shape)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    leadingContent?.invoke(fieldContentColor)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .align(if (singleLine) Alignment.CenterVertically else Alignment.Top)
                    ) {
                        if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                            Text(
                                text = placeholder,
                                color = fieldContentColor.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
        supportingText?.let { text ->
            Text(
                text = text,
                color = contentColor.copy(alpha = 0.58f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
