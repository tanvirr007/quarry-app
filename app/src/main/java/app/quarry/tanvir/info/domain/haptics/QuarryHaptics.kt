package app.quarry.tanvir.info.domain.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext

enum class QuarryHapticType {
    CLICK,
    SELECTION,
    LONG_PRESS,
    SUCCESS,
    WARNING,
    HEAVY
}

fun Context.hasVibrator(): Boolean {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    return vibrator?.hasVibrator() == true
}

fun Context.performQuarryHaptic(strength: Int, type: QuarryHapticType = QuarryHapticType.CLICK) {
    val s = strength.coerceIn(1, 100)
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (type) {
                QuarryHapticType.SELECTION -> {
                    // Subtle, micro-tick for checkboxes, switches, sliders
                    val amplitude = (s * 150 / 100).coerceIn(1, 255)
                    val durationMs = (8 + s * 0.10).toLong().coerceIn(6L, 20L)
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }
                QuarryHapticType.CLICK -> {
                    // Crisp, tactile tap for opening, navigation, buttons, cards
                    val amplitude = (s * 210 / 100).coerceIn(1, 255)
                    val durationMs = (14 + s * 0.16).toLong().coerceIn(10L, 32L)
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }
                QuarryHapticType.LONG_PRESS -> {
                    // Sustained, firm pulse for long press / entering multi-select
                    val amplitude = (s * 255 / 100).coerceIn(1, 255)
                    val durationMs = (28 + s * 0.28).toLong().coerceIn(20L, 60L)
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }
                QuarryHapticType.HEAVY -> {
                    // Strong feedback
                    val amplitude = (s * 255 / 100).coerceIn(1, 255)
                    val durationMs = (38 + s * 0.35).toLong().coerceIn(25L, 75L)
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }
                QuarryHapticType.WARNING -> {
                    // Sharp, distinct pulse for delete / critical actions
                    val amplitude = (s * 255 / 100).coerceIn(1, 255)
                    val durationMs = (22 + s * 0.20).toLong().coerceIn(15L, 45L)
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }
                QuarryHapticType.SUCCESS -> {
                    // Double micro-pulse for completed clean / scan / success
                    val amp1 = (s * 180 / 100).coerceIn(1, 255)
                    val amp2 = (s * 240 / 100).coerceIn(1, 255)
                    val timings = longArrayOf(0, 12, 45, 18)
                    val amplitudes = intArrayOf(0, amp1, 0, amp2)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            when (type) {
                QuarryHapticType.SELECTION -> vibrator.vibrate(10L)
                QuarryHapticType.CLICK -> vibrator.vibrate(18L)
                QuarryHapticType.LONG_PRESS -> vibrator.vibrate(40L)
                QuarryHapticType.HEAVY -> vibrator.vibrate(50L)
                QuarryHapticType.WARNING -> vibrator.vibrate(30L)
                QuarryHapticType.SUCCESS -> vibrator.vibrate(longArrayOf(0, 15, 50, 20), -1)
            }
        }
    } catch (_: Exception) {
    }
}

fun hapticStrengthLabel(strength: Int): String = when (strength.coerceIn(1, 100)) {
    in 1..30 -> "Low"
    in 31..60 -> "Medium"
    in 61..85 -> "High"
    else -> "Strong"
}

interface QuarryHaptics {
    fun click()
    fun selection()
    fun longPress()
    fun success()
    fun warning()
    fun heavy()
    fun perform(type: QuarryHapticType = QuarryHapticType.CLICK)
}

object NoOpQuarryHaptics : QuarryHaptics {
    override fun click() {}
    override fun selection() {}
    override fun longPress() {}
    override fun success() {}
    override fun warning() {}
    override fun heavy() {}
    override fun perform(type: QuarryHapticType) {}
}

class QuarryHapticsImpl(
    private val context: Context,
    private val enabled: Boolean,
    private val strength: Int
) : QuarryHaptics {
    override fun click() {
        if (enabled) context.performQuarryHaptic(strength, QuarryHapticType.CLICK)
    }

    override fun selection() {
        if (enabled) context.performQuarryHaptic(strength, QuarryHapticType.SELECTION)
    }

    override fun longPress() {
        if (enabled) context.performQuarryHaptic(strength, QuarryHapticType.LONG_PRESS)
    }

    override fun success() {
        if (enabled) context.performQuarryHaptic(strength, QuarryHapticType.SUCCESS)
    }

    override fun warning() {
        if (enabled) context.performQuarryHaptic(strength, QuarryHapticType.WARNING)
    }

    override fun heavy() {
        if (enabled) context.performQuarryHaptic(strength, QuarryHapticType.HEAVY)
    }

    override fun perform(type: QuarryHapticType) {
        if (enabled) context.performQuarryHaptic(strength, type)
    }
}

val LocalQuarryHaptics = staticCompositionLocalOf<QuarryHaptics> {
    NoOpQuarryHaptics
}

@Composable
fun rememberQuarryHaptics(enabled: Boolean = true, strength: Int = 60): QuarryHaptics {
    val context = LocalContext.current.applicationContext
    return remember(enabled, strength, context) {
        QuarryHapticsImpl(context, enabled, strength)
    }
}

@Composable
fun rememberQuarryComposeHapticFeedback(quarryHaptics: QuarryHaptics): HapticFeedback {
    return remember(quarryHaptics) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                when (hapticFeedbackType) {
                    HapticFeedbackType.LongPress -> quarryHaptics.longPress()
                    HapticFeedbackType.TextHandleMove -> quarryHaptics.selection()
                    else -> quarryHaptics.click()
                }
            }
        }
    }
}

@Composable
fun rememberQuarryHaptic(enabled: Boolean = true, strength: Int = 60): () -> Unit {
    val context = LocalContext.current
    return remember(enabled, strength, context) {
        {
            if (enabled) {
                context.performQuarryHaptic(strength, QuarryHapticType.LONG_PRESS)
            }
        }
    }
}
