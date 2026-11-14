package com.izzy2lost.super3

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.libsdl.app.SDLActivity
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Sega Rally 2 touch overlay (replaces the stock Super3 overlay for this build):
 *  - analog stick bottom-left: left/right = proportional steering,
 *    flick up/down = shift up/down (also up/down in menus)
 *  - gas / brake / view on the right, coin / start at the bottom centre
 *  - play/pause + menu buttons
 * Images are optional: if a PNG is missing from res/drawable a plain shape is used.
 */
class RallyOverlay(
    private val root: FrameLayout,
    private val overlay: View,
    private val onMenu: () -> Unit,
) {
    private companion object {
        const val F_COIN = 1101
        const val F_START = 1102
        const val F_GAS = 1103
        const val F_BRAKE = 1104
        const val F_WHEEL = 1107
        const val F_SHIFT = 1108
        const val F_SERVICE = 1105
        const val F_TEST = 1106
        const val F_VIEW = 1152

        const val STEER_DEADZONE = 0.06f
        const val SHIFT_TRIGGER = 0.65f
        const val SHIFT_REARM = 0.30f
        const val DEFAULT_OPACITY = 0.85f
        const val PREFS = "rally_overlay"
        const val KEY_OPACITY = "opacity"
        const val KEY_COIN_START = "coin_start_visible"
    }

    private val density = root.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = root.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var idleAlpha = prefs.getFloat(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(0.2f, 1.0f)
    private val pressedAlpha: Float get() = idleAlpha * 0.7f
    private val faders = ArrayList<View>()
    private var shiftArmed = true
    private var operatorRow: LinearLayout? = null
    private val coinStartViews = ArrayList<View>()
    private val touchViews = ArrayList<View>() // joystick, gas, brake, view

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    private fun touch(action: Int, finger: Int, x: Float, y: Float) {
        SDLActivity.onNativeTouch(0, finger, action, x, y, 1.0f)
    }

    private fun fallbackShape(): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(14).toFloat()
        d.setColor(Color.argb(120, 200, 200, 200))
        return d
    }

    private fun makeImage(name: String, wDp: Int, hDp: Int): ImageView {
        val iv = ImageView(root.context)
        val id = root.resources.getIdentifier(name, "drawable", root.context.packageName)
        if (id != 0) iv.setImageResource(id) else iv.setImageDrawable(fallbackShape())
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.alpha = idleAlpha
        faders.add(iv)
        iv.layoutParams = FrameLayout.LayoutParams(dp(wDp), dp(hDp))
        return iv
    }

    private fun place(v: View, gravity: Int, start: Int, top: Int, end: Int, bottom: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        lp.gravity = gravity
        lp.setMargins(dp(start), dp(top), dp(end), dp(bottom))
        root.addView(v, lp)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindPress(v: View, finger: Int, x: Float, y: Float) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.alpha = pressedAlpha
                    touch(MotionEvent.ACTION_DOWN, finger, x, y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = idleAlpha
                    touch(MotionEvent.ACTION_UP, finger, x, y)
                }
            }
            true
        }
    }

    fun attach() {
        // Hide the stock overlay widgets; this class provides its own.
        for (i in 0 until root.childCount) {
            root.getChildAt(i).visibility = View.GONE
        }
        addJoystick()
        addPedals()
        addCoinStart()
        addTopButtons()
        addOperatorButtons()
    }

    /** Show/hide the gameplay touch controls (stick, gas, brake, view). Pause/menu stay visible. */
    fun setTouchControlsVisible(visible: Boolean) {
        for (v in touchViews) v.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Show/hide the arcade SERVICE and TEST buttons (hidden by default). Returns the new state. */
    fun toggleOperator(): Boolean {
        val row = operatorRow ?: return false
        row.visibility = if (row.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        return row.visibility == View.VISIBLE
    }

    fun isOperatorVisible(): Boolean = operatorRow?.visibility == View.VISIBLE

    /** Overall opacity of all on-screen controls (0.2 .. 1.0). Saved between launches. */
    fun setOpacity(value: Float) {
        idleAlpha = value.coerceIn(0.2f, 1.0f)
        for (v in faders) v.alpha = idleAlpha
        prefs.edit().putFloat(KEY_OPACITY, idleAlpha).apply()
    }

    fun showOpacityDialog(activity: Activity) {
        val bar = SeekBar(activity)
        bar.max = 80
        bar.progress = ((idleAlpha * 100f).toInt() - 20).coerceIn(0, 80)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setOpacity((progress + 20) / 100f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val pad = dp(24)
        val box = FrameLayout(activity)
        box.setPadding(pad, pad, pad, 0)
        box.addView(bar)
        MaterialAlertDialogBuilder(
            activity,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog,
        )
            .setTitle("Controls opacity")
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------------------------------------------------------- joystick

    private fun fireShift(y: Float) {
        touch(MotionEvent.ACTION_DOWN, F_SHIFT, 0.5f, y)
        handler.postDelayed({ touch(MotionEvent.ACTION_UP, F_SHIFT, 0.5f, 0.5f) }, 80L)
    }

    private fun handleShift(ny: Float) {
        if (shiftArmed) {
            if (ny < -SHIFT_TRIGGER) {
                shiftArmed = false
                fireShift(0.0f)
            } else if (ny > SHIFT_TRIGGER) {
                shiftArmed = false
                fireShift(1.0f)
            }
        } else if (abs(ny) < SHIFT_REARM) {
            shiftArmed = true
        }
    }

    private fun sendSteer(action: Int, nx: Float) {
        val s = if (abs(nx) < STEER_DEADZONE) 0f else nx.coerceIn(-1f, 1f)
        touch(action, F_WHEEL, (s + 1f) / 2f, 0.5f)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addJoystick() {
        val size = 160
        val knobSize = 80

        val box = FrameLayout(root.context)
        box.layoutParams = FrameLayout.LayoutParams(dp(size), dp(size))

        val base = makeImage("joystick_base", size, size)
        val knob = makeImage("joystick_knob", knobSize, knobSize)
        (knob.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
        box.addView(base)
        box.addView(knob)

        var active = false
        box.setOnTouchListener { v, ev ->
            val cx = v.width / 2f
            val cy = v.height / 2f
            val maxR = (v.width / 2f - dp(knobSize) / 2f).coerceAtLeast(1f)
            var dx = ev.x - cx
            var dy = ev.y - cy
            val dist = hypot(dx, dy)
            if (dist > maxR) {
                dx = dx / dist * maxR
                dy = dy / dist * maxR
            }
            val nx = dx / maxR
            val ny = dy / maxR

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    active = true
                    base.alpha = 1.0f
                    knob.alpha = 1.0f
                    knob.translationX = dx
                    knob.translationY = dy
                    sendSteer(MotionEvent.ACTION_DOWN, nx)
                    handleShift(ny)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active) {
                        knob.translationX = dx
                        knob.translationY = dy
                        sendSteer(MotionEvent.ACTION_MOVE, nx)
                        handleShift(ny)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (active) {
                        active = false
                        base.alpha = idleAlpha
                        knob.alpha = idleAlpha
                        knob.translationX = 0f
                        knob.translationY = 0f
                        shiftArmed = true
                        touch(MotionEvent.ACTION_UP, F_WHEEL, 0.5f, 0.5f)
                    }
                }
            }
            true
        }

        place(box, Gravity.BOTTOM or Gravity.START, 20, 0, 0, 12)
        touchViews.add(box)
    }

    // ------------------------------------------------------- gas / brake / view

    private fun addPedals() {
        val gas = makeImage("btn_gas", 96, 96)
        val brake = makeImage("btn_brake", 96, 96)
        val view = makeImage("btn_view", 96, 54)

        bindPress(gas, F_GAS, 0.85f, 0.35f)
        bindPress(brake, F_BRAKE, 0.85f, 0.80f)
        bindPress(view, F_VIEW, 0.90f, 0.50f)

        place(gas, Gravity.BOTTOM or Gravity.END, 0, 0, 16, 16)
        place(brake, Gravity.BOTTOM or Gravity.END, 0, 0, 16 + 96 + 12, 16)
        place(view, Gravity.CENTER_VERTICAL or Gravity.END, 0, 0, 16, 0)
        touchViews.add(gas)
        touchViews.add(brake)
        touchViews.add(view)
    }

    // ---------------------------------------------------------------- coin/start

    private fun addCoinStart() {
        val coin = makeImage("btn_coin", 96, 48)
        val start = makeImage("btn_start", 96, 48)

        bindPress(coin, F_COIN, 0.10f, 0.90f)
        bindPress(start, F_START, 0.50f, 0.90f)

        coin.translationX = -dp(52).toFloat()
        start.translationX = dp(52).toFloat()

        place(coin, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14)
        place(start, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14)

        coinStartViews.add(coin)
        coinStartViews.add(start)
        val shown = prefs.getBoolean(KEY_COIN_START, false)
        for (v in coinStartViews) v.visibility = if (shown) View.VISIBLE else View.GONE
    }

    /** Show/hide the COIN and START buttons (hidden by default, saved). Returns the new state. */
    fun toggleCoinStart(): Boolean {
        val show = !isCoinStartVisible()
        for (v in coinStartViews) v.visibility = if (show) View.VISIBLE else View.GONE
        prefs.edit().putBoolean(KEY_COIN_START, show).apply()
        return show
    }

    fun isCoinStartVisible(): Boolean = coinStartViews.firstOrNull()?.visibility == View.VISIBLE

    // ------------------------------------------------------- play/pause + menu

    private fun roundButton(iconRes: Int, onClick: () -> Unit): ImageView {
        val iv = ImageView(root.context)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.argb(90, 0, 0, 0))
        iv.background = bg
        iv.setImageResource(iconRes)
        iv.setColorFilter(Color.argb(230, 255, 255, 255))
        iv.setPadding(dp(10), dp(10), dp(10), dp(10))
        iv.alpha = idleAlpha
        faders.add(iv)
        iv.setOnClickListener { onClick() }
        return iv
    }

    private fun addTopButtons() {
        val row = LinearLayout(root.context)
        row.orientation = LinearLayout.HORIZONTAL

        val pause = roundButton(R.drawable.play_pause_24px) {
            // Reuse the stock pause toggle (kept hidden in the layout).
            overlay.findViewById<View>(R.id.overlay_pause)?.performClick()
        }
        val menu = roundButton(R.drawable.ic_menu_24) { onMenu() }

        val lp1 = LinearLayout.LayoutParams(dp(44), dp(44))
        val lp2 = LinearLayout.LayoutParams(dp(44), dp(44))
        lp2.marginStart = dp(10)
        row.addView(pause, lp1)
        row.addView(menu, lp2)

        row.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        place(row, Gravity.TOP or Gravity.END, 0, 12, 12, 0)
    }

    // ------------------------------------------------------- service / test (hidden)

    private fun textButton(label: String): TextView {
        val tv = TextView(root.context)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), dp(8), dp(14), dp(8))
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(Color.argb(140, 0, 0, 0))
        bg.setStroke(dp(2), Color.argb(200, 255, 255, 255))
        tv.background = bg
        tv.alpha = idleAlpha
        faders.add(tv)
        return tv
    }

    private fun addOperatorButtons() {
        val row = LinearLayout(root.context)
        row.orientation = LinearLayout.HORIZONTAL

        val service = textButton("SERVICE")
        val test = textButton("TEST")
        bindPress(service, F_SERVICE, 0.10f, 0.10f)
        bindPress(test, F_TEST, 0.90f, 0.10f)

        val lp1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lp2.marginStart = dp(10)
        row.addView(service, lp1)
        row.addView(test, lp2)

        row.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        row.visibility = View.GONE
        operatorRow = row
        place(row, Gravity.TOP or Gravity.START, 12, 12, 0, 0)
    }
}
