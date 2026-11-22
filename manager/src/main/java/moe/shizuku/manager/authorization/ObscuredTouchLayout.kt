package moe.shizuku.manager.authorization

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout

/**
 * Rejects touches that Android reports as obscured, including partially obscured touches
 * (API 29+). The dialog window is separate from the activity window, so this check has to be
 * on the dialog content rather than only in [RequestPermissionActivity.dispatchTouchEvent].
 */
class ObscuredTouchLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.flags and OBSCURED_MASK != 0) return true
        return super.dispatchTouchEvent(event)
    }

    private companion object {
        const val OBSCURED_MASK =
            MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
    }
}
