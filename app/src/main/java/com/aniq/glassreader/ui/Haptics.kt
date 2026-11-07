package com.aniq.glassreader.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.aniq.glassreader.data.Store

/**
 * One place for every tactile cue. Drives the vibration motor directly with the
 * platform's "primitives" (crisp clicks, ticks and thuds, the closest Android gets
 * to a Taptic Engine), scaled by the user's strength setting, and falls back to
 * predefined effects, then to view haptics, on hardware that lacks them.
 */
enum class Haptic { Tap, Tick, PageTurn, Threshold, Confirm, Reject, LongPress, TextMove, ToggleOn, ToggleOff, GestureStart, GestureEnd }

class Haptics(private val view: View) {
    private val vibrator: Vibrator? = run {
        val ctx = view.context
        if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }
    private val primitives: Boolean = vibrator?.let {
        it.hasVibrator() && it.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        )
    } ?: false
    private val thud: Boolean = vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) ?: false

    private class P(val id: Int, val scale: Float, val delay: Int = 0)

    private fun pattern(h: Haptic): List<P> {
        val CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
        val TICK = VibrationEffect.Composition.PRIMITIVE_TICK
        val HEAVY = if (thud) VibrationEffect.Composition.PRIMITIVE_THUD else CLICK
        return when (h) {
            Haptic.Tap -> listOf(P(CLICK, 0.75f))
            Haptic.Tick -> listOf(P(TICK, 1f))
            Haptic.PageTurn -> listOf(P(CLICK, 0.9f))
            Haptic.Threshold -> listOf(P(CLICK, 1f))
            Haptic.Confirm -> listOf(P(CLICK, 1f), P(CLICK, 0.7f, 70))
            Haptic.Reject -> listOf(P(HEAVY, 1f), P(CLICK, 0.6f, 60), P(CLICK, 0.4f, 60))
            Haptic.LongPress -> listOf(P(HEAVY, 1f))
            Haptic.TextMove -> listOf(P(TICK, 0.85f))
            Haptic.ToggleOn -> listOf(P(CLICK, 1f))
            Haptic.ToggleOff -> listOf(P(TICK, 1f))
            Haptic.GestureStart -> listOf(P(TICK, 0.8f))
            Haptic.GestureEnd -> listOf(P(TICK, 0.6f))
        }
    }

    fun play(h: Haptic) {
        val s = Store.settings.value
        if (!s.haptics) return
        val k = when (s.hapticLevel) { 0 -> 0.45f; 1 -> 0.72f; else -> 1f }
        val v = vibrator
        try {
            if (v != null && primitives) {
                val comp = VibrationEffect.startComposition()
                pattern(h).forEach { comp.addPrimitive(it.id, (it.scale * k).coerceIn(0f, 1f), it.delay) }
                vibrate(v, comp.compose())
                return
            }
            if (v != null && v.hasVibrator()) {
                val e = when (h) {
                    Haptic.Tick, Haptic.TextMove, Haptic.GestureStart, Haptic.GestureEnd, Haptic.ToggleOff -> VibrationEffect.EFFECT_TICK
                    Haptic.LongPress, Haptic.Reject, Haptic.Threshold -> VibrationEffect.EFFECT_HEAVY_CLICK
                    Haptic.Confirm -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }
                vibrate(v, VibrationEffect.createPredefined(e))
                return
            }
        } catch (_: Exception) { }
        view.performHapticFeedback(
            when (h) {
                Haptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
                Haptic.Confirm -> HapticFeedbackConstants.CONFIRM
                Haptic.Reject -> HapticFeedbackConstants.REJECT
                else -> HapticFeedbackConstants.CLOCK_TICK
            }
        )
    }

    private fun vibrate(v: Vibrator, e: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= 33) v.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        else v.vibrate(e)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
