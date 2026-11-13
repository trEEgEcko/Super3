package com.izzy2lost.super3

import android.app.Activity
import android.content.Intent

/** Fully restarts the app (new process) so Supermodel.ini changes are re-read. */
object AppRestart {
    fun restart(activity: Activity) {
        val next =
            activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                ?: return
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val helper = Intent(activity, RestartHelperActivity::class.java)
        helper.putExtra(RestartHelperActivity.EXTRA_NEXT, next)
        activity.startActivity(helper)
        activity.finishAffinity()
        // The helper runs in a separate process, so the game process can exit right away.
        Runtime.getRuntime().exit(0)
    }
}
