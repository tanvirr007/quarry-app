package app.quarry.tanvir.info.domain.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

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

fun Context.performQuarryHaptic(strength: Int) {
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
            val amplitude = (s * 255 / 100).coerceIn(1, 255)
            val durationMs = (20 + s * 0.35).toLong().coerceIn(10L, 80L)
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(35L)
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

@Composable
fun rememberQuarryHaptic(enabled: Boolean, strength: Int): () -> Unit {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    return remember(enabled, strength, context) {
        {
            if (enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                context.performQuarryHaptic(strength)
            }
        }
    }
}
