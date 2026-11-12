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
 * Standalone entry point: skips the game list and boots Sega Rally 2 directly.
 * First launch asks for srally2.zip once, copies it into app storage, then
 * every later launch goes straight into the game.
 */
class RallyLauncherActivity : AppCompatActivity() {
    private val gameName = "srally2"
    private lateinit var status: TextView

    private val pickRom =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                status.text = "No ROM selected.\n\nTap here to choose $gameName.zip"
            } else {
                importRom(uri)
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

        val rom = romFile()
        if (rom.isFile && rom.length() > 0L) {
            launchGame()
        } else {
            chooseRom()
        }
    }

    private fun userRoot(): File = File(getExternalFilesDir(null), "super3")

    private fun romFile(): File = File(File(userRoot(), "romcache"), "$gameName.zip")

    private fun chooseRom() {
        status.text = "Select your $gameName.zip"
        pickRom.launch(arrayOf("*/*"))
    }

    private fun importRom(uri: Uri) {
        status.text = "Copying $gameName.zip..."
        thread(name = "RallyRomImport") {
            val out = romFile()
            val tmp = File(out.parentFile, "$gameName.zip.tmp")
            val ok = runCatching {
                out.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)!!.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (out.exists()) out.delete()
                tmp.renameTo(out)
            }.getOrDefault(false)

            runOnUiThread {
                if (ok && out.isFile && out.length() > 0L) {
                    launchGame()
                } else {
                    tmp.delete()
                    status.text = "Failed to copy the ROM.\n\nTap here to try again"
                }
            }
        }
    }

    private fun launchGame() {
        status.text = "Starting Sega Rally 2..."
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
