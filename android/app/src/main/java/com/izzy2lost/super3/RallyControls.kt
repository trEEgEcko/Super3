package com.izzy2lost.super3

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.libsdl.app.SDLActivity

/**
 * Sega Rally 2 touch layout with two modes, switched by a small button at the top:
 *  - MENU mode:    left/right + coin + start (+ test/service)
 *  - DRIVING mode: left/right + gas/brake (+ view/shifter), no coin/start
 * Left/right replace the steering wheel. Steering ramps smoothly and re-centres on release.
 */
class RallyControls(private val root: FrameLayout, private val overlay: View) {
    private companion object {
        const val WHEEL_FINGER = 1107
        const val TICK_MS = 16L
        const val PRESS_STEP = 0.10f
        const val RETURN_STEP = 0.20f
        const val BTN_SIZE_DP = 110
    }

    private val handler = Handler(Looper.getMainLooper())
    private var drivingMode = false
    private var leftHeld = false
    private var rightHeld = false
    private var steer = 0f
    private var steerActive = false
    private var running = false

    private lateinit var leftBtn: TextView
    private lateinit var rightBtn: TextView
    private lateinit var modeBtn: TextView

    private val ticker = object : Runnable {
        override fun run() {
            val target = when {
                leftHeld && !rightHeld -> -1f
                rightHeld && !leftHeld -> 1f
                else -> 0f
            }
            val step = if (target == 0f) RETURN_STEP else PRESS_STEP
            steer = when {
                steer < target -> minOf(steer + step, target)
                steer > target -> maxOf(steer - step, target)
                else -> steer
            }

            val idle = !leftHeld && !rightHeld && steer == 0f
            if (idle) {
                if (steerActive) {
                    steerActive = false
                    sendTouch(MotionEvent.ACTION_UP, 0.5f)
                }
                running = false
                return
            }

            val x = ((steer + 1f) / 2f).coerceIn(0f, 1f)
            if (!steerActive) {
                steerActive = true
                sendTouch(MotionEvent.ACTION_DOWN, x)
            } else {
                sendTouch(MotionEvent.ACTION_MOVE, x)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun sendTouch(action: Int, x: Float) {
        SDLActivity.onNativeTouch(0, WHEEL_FINGER, action, x, 0.5f, 1.0f)
    }

    private fun kick() {
        if (!running) {
            running = true
            handler.post(ticker)
        }
    }

    private fun dp(v: Int): Int = (v * root.resources.displayMetrics.density + 0.5f).toInt()

    private fun makeButton(label: String, textSp: Float): TextView =
        TextView(root.context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = textSp
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(110, 30, 30, 30))
                setStroke(dp(2), Color.argb(180, 255, 255, 255))
            }
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindSteer(v: View, left: Boolean) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.7f
                    if (left) leftHeld = true else rightHeld = true
                    kick()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1.0f
                    if (left) leftHeld = false else rightHeld = false
                    kick()
                }
            }
            true
        }
    }

    private fun setVisible(id: Int, visible: Boolean) {
        overlay.findViewById<View>(id)?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun placeSteerButtons() {
        val bottom = if (drivingMode) 28 else 112 // keep clear of the coin button in menu mode
        (leftBtn.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(dp(28), 0, 0, dp(bottom))
        }
        (rightBtn.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(dp(28 + BTN_SIZE_DP + 16), 0, 0, dp(bottom))
        }
        leftBtn.requestLayout()
        rightBtn.requestLayout()
    }

    private fun applyMode() {
        // The old on-screen wheel is replaced by the left/right buttons.
        setVisible(R.id.overlay_wheel, false)
        setVisible(R.id.overlay_pedals, drivingMode)
        setVisible(R.id.overlay_coin, !drivingMode)
        setVisible(R.id.overlay_start, !drivingMode)
        setVisible(R.id.overlay_service, !drivingMode)
        setVisible(R.id.overlay_test, !drivingMode)
        modeBtn.text = if (drivingMode) "MENU" else "DRIVE"
        placeSteerButtons()
    }

    fun attach() {
        leftBtn = makeButton("\u25C0", 32f)
        rightBtn = makeButton("\u25B6", 32f)
        modeBtn = makeButton("DRIVE", 14f)

        root.addView(leftBtn, FrameLayout.LayoutParams(dp(BTN_SIZE_DP), dp(BTN_SIZE_DP)))
        root.addView(rightBtn, FrameLayout.LayoutParams(dp(BTN_SIZE_DP), dp(BTN_SIZE_DP)))
        root.addView(
            modeBtn,
            FrameLayout.LayoutParams(dp(96), dp(40)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
            },
        )

        bindSteer(leftBtn, left = true)
        bindSteer(rightBtn, left = false)

        modeBtn.setOnClickListener {
            drivingMode = !drivingMode
            leftHeld = false
            rightHeld = false
            kick()
            applyMode()
        }

        applyMode()
    }
}
