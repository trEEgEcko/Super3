package com.izzy2lost.super3

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Runs in its own process (":restart"): relaunches the app after the game process exited. */
class RestartHelperActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val next = intent.getParcelableExtra<Intent>(EXTRA_NEXT)
        if (next != null) startActivity(next)
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object {
        const val EXTRA_NEXT = "com.izzy2lost.super3.extra.RESTART_NEXT"
    }
}
