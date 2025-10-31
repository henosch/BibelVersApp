package de.henosch.bibelvers

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout

class SwipeGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var gestureDetector: GestureDetector? = null

    fun setGestureDetector(detector: GestureDetector) {
        gestureDetector = detector
        isClickable = true
        isFocusableInTouchMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector?.onTouchEvent(event) ?: false
        if (handled && event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
