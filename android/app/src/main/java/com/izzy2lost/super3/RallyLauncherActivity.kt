package com.izzy2lost.super3

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

/**
 * Standalone entry point: skips the game list and boots the game this APK was built for
 * (BuildConfig.GAME_NAME). First launch asks for the ROM zip(s) once (parent sets included,
 * e.g. scudplusa.zip + scud.zip), copies them into app storage, then every later launch
 * goes straight into the game.
 */
class RallyLauncherActivity : AppCompatActivity() {
    private val gameName = BuildConfig.GAME_NAME
    private val romNames = BuildConfig.ROM_FILES.split(",").map { it.trim() + ".zip" }
    private lateinit var status: TextView

    private val pickRom =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isEmpty()) {
                status.text = "No ROM selected.\n\nTap here to choose ${missing().joinToString(" + ")}"
            } else {
                importRoms(uris)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()

        status = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 18f
            setPadding(48, 48, 48, 48)
            setOnClickListener { chooseRom() }
        }
        setContentView(status)

        if (missing().isEmpty()) {
            launchGame()
        } else {
            chooseRom()
        }
    }

    private fun userRoot(): File = File(getExternalFilesDir(null), "super3")

    private fun romDir(): File = File(userRoot(), "romcache")

    private fun romFile(): File = File(romDir(), "$gameName.zip")

    private fun missing(): List<String> =
        romNames.filter { n -> File(romDir(), n).let { !(it.isFile && it.length() > 0L) } }

    private fun chooseRom() {
        status.text = "Select your ${missing().joinToString(" + ")}\n\n(you can pick several files at once)"
        pickRom.launch(arrayOf("*/*"))
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun importRoms(uris: List<Uri>) {
        status.text = "Copying ROM files..."
        thread(name = "RallyRomImport") {
            romDir().mkdirs()
            for (uri in uris) {
                val name = runCatching { displayName(uri) }.getOrNull()?.lowercase() ?: continue
                if (name !in romNames) continue
                val out = File(romDir(), name)
                val tmp = File(romDir(), "$name.tmp")
                runCatching {
                    contentResolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (out.exists()) out.delete()
                    tmp.renameTo(out)
                }.onFailure { tmp.delete() }
            }

            runOnUiThread {
                val left = missing()
                if (left.isEmpty()) {
                    launchGame()
                } else {
                    status.text = "Still missing: ${left.joinToString(" + ")}\n\nTap here to choose"
                }
            }
        }
    }

    private fun launchGame() {
        status.text = "Starting ${BuildConfig.GAME_TITLE}..."
        thread(name = "RallyPrep") {
            val root = userRoot()
            AssetInstaller.ensureInstalled(this, root)
            RallyVideoSettings.applyFirstRunDefaults(this)
            val romPath = romFile().absolutePath
            val gamesXml = File(root, "Config/Games.xml").absolutePath

            runOnUiThread {
                val intent = Intent(this, Super3Activity::class.java).apply {
                    putExtra("romZipPath", romPath)
                    putExtra("gameName", gameName)
                    putExtra("gamesXmlPath", gamesXml)
                    putExtra("userDataRoot", root.absolutePath)
                }
                startActivity(intent)
                finish()
            }
        }
    }
}
