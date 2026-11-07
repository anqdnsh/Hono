package com.aniq.glassreader.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * One place for every tactile cue, so the whole app speaks the same haptic language.
 * Uses the platform's expressive constants (crisper than a generic buzz) and falls
 * back gracefully on older Android versions. Respects the system "touch feedback" setting.
 */
enum class Haptic { Tap, Tick, PageTurn, Threshold, Confirm, Reject, LongPress, TextMove, ToggleOn, ToggleOff, GestureStart, GestureEnd }

class Haptics(private val view: View) {
    fun play(h: Haptic) {
        val c = when (h) {
            Haptic.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.Tick -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            Haptic.PageTurn -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            Haptic.Threshold -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK
            Haptic.Confirm -> HapticFeedbackConstants.CONFIRM
            Haptic.Reject -> HapticFeedbackConstants.REJECT
            Haptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
            Haptic.TextMove -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            Haptic.ToggleOn -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CONFIRM
            Haptic.ToggleOff -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
            Haptic.GestureStart -> HapticFeedbackConstants.GESTURE_START
            Haptic.GestureEnd -> HapticFeedbackConstants.GESTURE_END
        }
        view.performHapticFeedback(c)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
