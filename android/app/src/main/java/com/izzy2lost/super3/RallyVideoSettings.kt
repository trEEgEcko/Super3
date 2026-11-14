package com.izzy2lost.super3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * In-game video settings for the standalone Sega Rally 2 build.
 * Reads/writes the Global section of Config/Supermodel.ini (same keys the Super3 drawer used).
 * Settings are read by the core at start-up, so applying them restarts the app.
 */
object RallyVideoSettings {
    private const val PREFS = "rally_video"
    private const val KEY_DEFAULTS_APPLIED = "defaults_applied_v2"

    private fun userRoot(ctx: Context): File = File(ctx.getExternalFilesDir(null), "super3")

    private fun iniFile(ctx: Context): File = File(File(userRoot(ctx), "Config"), "Supermodel.ini")

    private fun deviceRes(ctx: Context): Pair<Int, Int> {
        val dm = ctx.resources.displayMetrics
        return Pair(max(dm.widthPixels, dm.heightPixels), min(dm.widthPixels, dm.heightPixels))
    }

    private fun isHeader(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("[") && t.endsWith("]")
    }

    private fun globalRange(lines: List<String>): IntRange? {
        val start = lines.indexOfFirst {
            isHeader(it) && it.trim().removePrefix("[").removeSuffix("]").trim().equals("global", ignoreCase = true)
        }
        if (start < 0) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (isHeader(lines[i])) {
                end = i
                break
            }
        }
        return (start + 1) until end
    }

    private fun readValue(file: File, key: String): String? {
        if (!file.exists()) return null
        val lines = file.readLines()
        val range = globalRange(lines) ?: return null
        val re = Regex("^\\s*" + Regex.escape(key) + "\\s*=\\s*(.*?)\\s*(;.*)?$")
        for (i in range) {
            val m = re.find(lines[i])
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun isTrue(v: String?): Boolean = v == "1" || v.equals("true", ignoreCase = true)

    private val VIDEO_KEYS = setOf(
        "XResolution", "YResolution", "WideScreen", "WideBackground",
        "New3DEngine", "PowerPCFrequency", "GPUMultiThreaded",
    )

    private fun writeValues(file: File, updates: Map<String, String>) {
        val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
        if (globalRange(lines) == null) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("[ Global ]")
        }
        for ((k, v) in updates) {
            val r = globalRange(lines) ?: continue
            val re = Regex("^\\s*" + Regex.escape(k) + "\\s*=")
            // Replace every occurrence (the core keeps the last one if a key is duplicated).
            val hits = r.filter { re.containsMatchIn(lines[it]) }
            if (hits.isNotEmpty()) {
                for (i in hits) lines[i] = "$k = $v"
            } else {
                var pos = r.first
                for (i in r) {
                    if (lines[i].isNotBlank()) pos = i + 1
                }
                lines.add(pos, "$k = $v")
            }
        }
        // A per-game section overrides Global: update video keys already present there too.
        var inGame = false
        for (i in lines.indices) {
            val line = lines[i]
            if (isHeader(line)) {
                val name = line.trim().removePrefix("[").removeSuffix("]").trim()
                inGame = !name.equals("global", ignoreCase = true)
                continue
            }
            if (!inGame) continue
            for ((k, v) in updates) {
                if (k in VIDEO_KEYS && Regex("^\\s*" + Regex.escape(k) + "\\s*=").containsMatchIn(line)) {
                    lines[i] = "$k = $v"
                }
            }
        }
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n") + "\n")
    }

    /** First launch after install/update: classic Native 4:3 (496x384), no widescreen. */
    fun applyFirstRunDefaults(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEFAULTS_APPLIED, false)) return
        writeValues(
            iniFile(ctx),
            mapOf(
                "XResolution" to "496",
                "YResolution" to "384",
                "WideScreen" to "0",
                "WideBackground" to "0",
                "New3DEngine" to "1",
            ),
        )
        prefs.edit().putBoolean(KEY_DEFAULTS_APPLIED, true).apply()
    }

    fun show(activity: Activity) {
        val ini = iniFile(activity)
        val dev = deviceRes(activity)
        val labels = listOf(
            "Native 4:3 (496x384)",
            "Light (${dev.first / 2}x${dev.second / 2})",
            "Full device (${dev.first}x${dev.second})",
        )
        val sizes = listOf(
            Pair(496, 384),
            Pair(dev.first / 2, dev.second / 2),
            Pair(dev.first, dev.second),
        )

        val curX = readValue(ini, "XResolution")?.toIntOrNull() ?: 496
        val curY = readValue(ini, "YResolution")?.toIntOrNull() ?: 384

        val density = activity.resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad / 2, pad, 0)

        val group = RadioGroup(activity)
        val radios = ArrayList<RadioButton>()
        for (i in labels.indices) {
            val rb = RadioButton(activity)
            rb.id = View.generateViewId()
            rb.text = labels[i]
            rb.isChecked = sizes[i].first == curX && sizes[i].second == curY
            group.addView(rb)
            radios.add(rb)
        }
        box.addView(group)

        val cbWide = CheckBox(activity)
        cbWide.text = "Widescreen"
        cbWide.isChecked = isTrue(readValue(ini, "WideScreen"))
        box.addView(cbWide)

        val cbBg = CheckBox(activity)
        cbBg.text = "Wide background"
        cbBg.isChecked = isTrue(readValue(ini, "WideBackground"))
        box.addView(cbBg)

        val cb3d = CheckBox(activity)
        cb3d.text = "3D renderer (Real3D)"
        cb3d.isChecked = readValue(ini, "New3DEngine")?.let { isTrue(it) } ?: true
        box.addView(cb3d)

        // Performance: emulated CPU speed (lower = faster, but may cause glitches) + GPU thread.
        val perfTitle = TextView(activity)
        perfTitle.text = "Performance (if the game lags)"
        perfTitle.setPadding(0, (12 * density).toInt(), 0, 0)
        box.addView(perfTitle)

        val ppcValues = listOf(50, 40)
        val ppcLabels = listOf("CPU speed 50 (default)", "CPU speed 40 (faster)")
        val curPpc = readValue(ini, "PowerPCFrequency")?.toIntOrNull() ?: 50
        val ppcGroup = RadioGroup(activity)
        val ppcRadios = ArrayList<RadioButton>()
        val ppcChecked = ppcValues.minByOrNull { kotlin.math.abs(it - curPpc) }
        for (i in ppcValues.indices) {
            val rb = RadioButton(activity)
            rb.id = View.generateViewId()
            rb.text = ppcLabels[i]
            rb.isChecked = ppcValues[i] == ppcChecked
            ppcGroup.addView(rb)
            ppcRadios.add(rb)
        }
        box.addView(ppcGroup)

        val cbGpuMt = CheckBox(activity)
        cbGpuMt.text = "GPU multi-threading"
        cbGpuMt.isChecked = isTrue(readValue(ini, "GPUMultiThreaded"))
        box.addView(cbGpuMt)

        val scroll = ScrollView(activity)
        scroll.addView(box)

        MaterialAlertDialogBuilder(
            activity,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog,
        )
            .setTitle("Video settings")
            .setView(scroll)
            .setPositiveButton("Apply & restart") { _, _ ->
                val updates = LinkedHashMap<String, String>()
                val selected = radios.indexOfFirst { it.isChecked }
                if (selected >= 0) {
                    updates["XResolution"] = sizes[selected].first.toString()
                    updates["YResolution"] = sizes[selected].second.toString()
                }
                updates["WideScreen"] = if (cbWide.isChecked) "1" else "0"
                updates["WideBackground"] = if (cbBg.isChecked) "1" else "0"
                updates["New3DEngine"] = if (cb3d.isChecked) "1" else "0"
                val ppcSel = ppcRadios.indexOfFirst { it.isChecked }
                if (ppcSel >= 0) updates["PowerPCFrequency"] = ppcValues[ppcSel].toString()
                updates["GPUMultiThreaded"] = if (cbGpuMt.isChecked) "1" else "0"
                writeValues(ini, updates)
                AppRestart.restart(activity)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restartApp(activity: Activity) {
        val intent = Intent(activity, RallyLauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 250)
    }
}
