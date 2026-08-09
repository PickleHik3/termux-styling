package com.termux.styling

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.AtomicFile
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

const val DEFAULT_FILENAME = "Default"

/**
 * The Termux Launcher editions this app knows about, in the order they are probed when the package
 * pinned at build time ([BuildConfig.TERMUX_LAUNCHER_PACKAGE_NAME]) is not installed.
 *
 * Note that only the edition matching [BuildConfig.TERMUX_LAUNCHER_SHARED_USER_ID] can actually be
 * written to: the shared user id in the manifest is fixed at build time, so probing the rest only
 * serves to produce a precise message instead of a cryptic write failure.
 *
 * Keep in sync with the `<queries>` entries in `AndroidManifest.xml`, otherwise the
 * PackageManager lookups below report "not installed" on Android 11 and later.
 */
val LAUNCHER_PACKAGE_CANDIDATES: List<String> = listOf(
        BuildConfig.TERMUX_LAUNCHER_PACKAGE_NAME,
        "com.termux",
        "io.vaj.tl",
        "com.termux.launcher",
        "com.termux.launcher.dev",
        "com.termux.launcher.nix"
).distinct()

/** The launcher derives this from its own package name in `TermuxConstants.ACTION_RELOAD_STYLE`. */
fun reloadStyleAction(launcherPackageName: String): String = "$launcherPackageName.app.reload_style"

fun capitalize(str: String): String {
    var lastWhitespace = true
    val chars = str.toCharArray()
    for (i in chars.indices) {
        if (Character.isLetter(chars[i])) {
            if (lastWhitespace) chars[i] = Character.toUpperCase(chars[i])
            lastWhitespace = false
        } else {
            lastWhitespace = Character.isWhitespace(chars[i])
        }
    }
    return String(chars)
}

class TermuxStyleActivity : Activity() {

    internal class Selectable(val fileName: String) {
        val displayName: String

        init {
            var name = fileName.replace('-', ' ')
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex != -1) name = name.substring(0, dotIndex)

            this.displayName = capitalize(name)
        }

        override fun toString(): String {
            return displayName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Avoid dim behind:
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContentView(R.layout.layout)

        val colorSpinner = findViewById<Button>(R.id.color_spinner)
        val fontSpinner = findViewById<Button>(R.id.font_spinner)

        val colorAdapter = ArrayAdapter<Selectable>(this, android.R.layout.simple_spinner_dropdown_item)

        colorSpinner.setOnClickListener {
            val dialog = AlertDialog.Builder(this@TermuxStyleActivity).setAdapter(colorAdapter) { _, which -> copyFile(colorAdapter.getItem(which), true) }.create()
            dialog.setOnShowListener {
                val lv = dialog.listView
                lv.setOnItemLongClickListener { _, _, position, _ ->
                    showLicense(colorAdapter.getItem(position) as Selectable, true)
                    true
                }
            }
            dialog.show()
        }

        val fontAdapter = ArrayAdapter<Selectable>(this, android.R.layout.simple_spinner_dropdown_item)
        fontSpinner.setOnClickListener {
            val dialog = AlertDialog.Builder(this@TermuxStyleActivity).setAdapter(fontAdapter) { _, which -> copyFile(fontAdapter.getItem(which), false) }.create()
            dialog.setOnShowListener {
                val lv = dialog.listView
                lv.setOnItemLongClickListener { _, _, position, _ ->
                    showLicense(fontAdapter.getItem(position) as Selectable, false)
                    true
                }
            }
            dialog.show()
        }

        val colorList = ArrayList<Selectable>()
        val fontList = ArrayList<Selectable>()
        for (assetType in arrayOf("colors", "fonts")) {
            val isColors = assetType == "colors"
            val assetsFileExtension = if (isColors) ".properties" else ".ttf"
            val currentList = if (isColors) colorList else fontList

            currentList.add(Selectable(if (isColors) DEFAULT_FILENAME else DEFAULT_FILENAME))

            try {
                assets.list(assetType)!!
                        .filter { it.endsWith(assetsFileExtension) }
                        .forEach { currentList.add(Selectable(it)) }
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

        }

        colorAdapter.addAll(colorList)
        fontAdapter.addAll(fontList)
    }

    private fun showLicense(mCurrentSelectable: Selectable, colors: Boolean) {
        try {
            val assetsFolder = if (colors) "colors" else "fonts"
            var fileName = mCurrentSelectable.fileName
            val dotIndex = fileName.lastIndexOf('.')
            if (dotIndex != -1) fileName = fileName.substring(0, dotIndex)
            fileName += ".txt"

            assets.open("$assetsFolder/$fileName").use { `in` ->
                val buffer = ByteArray(`in`.available())
                `in`.read(buffer)
                val license = SpannableString(String(buffer))
                Linkify.addLinks(license, Linkify.ALL)
                val dialog = AlertDialog.Builder(this)
                        .setTitle(mCurrentSelectable.displayName)
                        .setMessage(license)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                (dialog.findViewById<View>(android.R.id.message) as TextView).movementMethod = LinkMovementMethod.getInstance()
            }
        } catch (e: IOException) {
            // Ignore.
        }

    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * The installed launcher edition to style: the package pinned at build time if it is present,
     * otherwise the first installed entry of [LAUNCHER_PACKAGE_CANDIDATES]. `null` when none of them
     * is installed.
     */
    private fun resolveLauncherPackageName(): String? {
        return LAUNCHER_PACKAGE_CANDIDATES.firstOrNull { isPackageInstalled(it) }
    }

    private fun copyFile(mCurrentSelectable: Selectable?, colors: Boolean) {
        val outputFile = if (colors) "colors.properties" else "font.ttf"

        val launcherPackageName = resolveLauncherPackageName()
        if (launcherPackageName == null) {
            Log.w("termux", "No Termux Launcher package installed out of $LAUNCHER_PACKAGE_CANDIDATES")
            Toast.makeText(this, R.string.launcher_not_installed, Toast.LENGTH_LONG).show()
            return
        }

        // The shared user id is baked into the manifest at build time, so an installed launcher of a
        // different edition is visible but unreachable. Say so instead of failing on the write.
        val sharedUserId = BuildConfig.TERMUX_LAUNCHER_SHARED_USER_ID
        if (launcherPackageName != sharedUserId) {
            Log.w("termux", "Installed launcher $launcherPackageName is not reachable: " +
                    "this build shares user id $sharedUserId")
            val message = resources.getString(R.string.launcher_edition_unreachable, launcherPackageName, sharedUserId)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        try {
            val assetsFolder = if (colors) "colors" else "fonts"

            val context = createPackageContext(launcherPackageName, Context.CONTEXT_IGNORE_SECURITY)
            val homeDir = File(context.filesDir, "home")
            val termuxDir = File(homeDir, ".termux")
            if (!(termuxDir.isDirectory || termuxDir.mkdirs()))
                throw RuntimeException("Cannot create termux dir=" + termuxDir.absolutePath)

            // Use canonicalFile to follow a possible symlink:
            val destinationFile = File(termuxDir, outputFile).canonicalFile
            // Fix for if the user has messed up with chmod:
            destinationFile.setWritable(true)
            destinationFile.parentFile?.setWritable(true)
            destinationFile.parentFile?.setExecutable(true)

            val defaultChoice = mCurrentSelectable!!.fileName == DEFAULT_FILENAME
            val atomicFile = AtomicFile(destinationFile)
            val out = atomicFile.startWrite()
            if (defaultChoice) {
                if (colors) {
                    val comment = "# Using default color theme.".toByteArray(StandardCharsets.UTF_8)
                    out.write(comment)
                } else {
                    // Just leave an empty font file as a marker.
                }
            } else {
                assets.open(assetsFolder + "/" + mCurrentSelectable.fileName).use {
                    it.copyTo(out)
                }
            }
            atomicFile.finishWrite(out)

            // Note: Must match TermuxConstants.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE of the resolved
            // launcher edition, which derives it from its own package name.
            val actionReload = reloadStyleAction(launcherPackageName)
            val executeIntent = Intent(actionReload)
            executeIntent.putExtra(actionReload, if (colors) "colors" else "font")
            sendBroadcast(executeIntent)
        } catch (e: Exception) {
            Log.w("termux", "Failed to write $outputFile", e)
            val message = resources.getString(R.string.writing_failed) + e.message
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

    }

}
