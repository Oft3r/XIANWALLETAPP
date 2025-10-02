package net.xian.xianwalletapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning

/**
 * Data class representing a toast notification entry.
 */
data class ToastMessage(
    val id: Long = System.nanoTime(),
    val message: String,
    val type: ToastType = ToastType.Info,
    val durationMillis: Long = 3000L
)

enum class ToastType { Success, Error, Warning, Info }

/** State holder for the toast host */
class ToastHostState {
    private val _messages = mutableStateListOf<ToastMessage>()
    val messages: List<ToastMessage> get() = _messages

    fun show(message: String, type: ToastType = ToastType.Info, durationMillis: Long = 3000L) {
        val toast = ToastMessage(message = message, type = type, durationMillis = durationMillis)
        _messages.add(toast)
    }

    fun dismiss(id: Long) {
        _messages.removeAll { it.id == id }
    }

    fun clear() { _messages.clear() }
}

@Composable
fun rememberToastHostState(): ToastHostState = remember { ToastHostState() }

/**
 * Composable that displays a stack of toasts at the top of the screen, sliding in from the right.
 */
@Composable
fun TopToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
    maxToasts: Int = 3
) {
    // Automatically remove toasts after their duration
    state.messages.take(maxToasts).forEach { toast ->
        LaunchedEffect(toast.id) {
            delay(toast.durationMillis)
            state.dismiss(toast.id)
        }
    }

    // Column overlay
    // Single Popup overlay to guarantee layering, with internal Column stacking toasts
    Popup(
        alignment = Alignment.TopEnd,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = modifier
                .statusBarsPadding()
                .wrapContentWidth()
                .zIndex(1000f)
                .padding(top = 4.dp, end = 8.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.End
        ) {
            state.messages.take(maxToasts).forEach { toast ->
                AnimatedVisibility(
                    visible = state.messages.any { it.id == toast.id },
                    enter = slideInHorizontally(
                        initialOffsetX = { full -> full / 2 },
                        animationSpec = tween(durationMillis = 350)
                    ) + fadeIn(tween(250)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { full -> -full / 3 },
                        animationSpec = tween(durationMillis = 300)
                    ) + fadeOut(tween(200))
                ) {
                    ToastCard(
                        toast = toast,
                        onDismiss = { state.dismiss(toast.id) },
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .widthIn(max = 340.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastCard(
    toast: ToastMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bg, content, icon, border) = when (toast.type) {
        ToastType.Success -> Quad(
            Color(0xFF0F3D2E),
            Color(0xFF9FF2C8),
            Icons.Default.CheckCircle,
            Color(0xFF1DBF73)
        )
        ToastType.Error -> Quad(
            Color(0xFF3D0F17),
            Color(0xFFFFB3C1),
            Icons.Default.Error,
            Color(0xFFE53935)
        )
        ToastType.Warning -> Quad(
            Color(0xFF3D2B0F),
            Color(0xFFFFE29A),
            Icons.Default.Warning,
            Color(0xFFFFB300)
        )
        ToastType.Info -> Quad(
            Color(0xFF102B3D),
            Color(0xFFB3E5FF),
            Icons.Default.Info,
            Color(0xFF0288D1)
        )
    }

    Surface(
        modifier = modifier,
        color = bg, // Fully opaque background
        contentColor = content,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 12.dp,
        tonalElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = border,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = toast.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class Quad<T1, T2, T3, T4>(val a: T1, val b: T2, val c: T3, val d: T4)
