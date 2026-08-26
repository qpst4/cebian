package com.slideindex.app.xposed.config

import android.content.Context
import java.io.File
import java.util.Properties

object XposedConfigWriter {
    private const val HIDE_RECENT_FILE = "hide_recent.properties"

    fun writeHideRecent(
        context: Context,
        hideTaskPackages: Set<String>,
        hidePreviewPackages: Set<String>,
    ) {
        val props = Properties()
        props.setProperty("hide_task", hideTaskPackages.joinToString(","))
        props.setProperty("hide_preview", hidePreviewPackages.joinToString(","))
        val file = configFile(context, HIDE_RECENT_FILE)
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "Cebian hide recent config") }
    }

    fun readHideRecent(context: Context): Pair<Set<String>, Set<String>> {
        val file = configFile(context, HIDE_RECENT_FILE)
        if (!file.exists()) return emptySet<String>() to emptySet()
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val hideTask = props.getProperty("hide_task", "").split(',').filter { it.isNotBlank() }.toSet()
        val hidePreview = props.getProperty("hide_preview", "").split(',').filter { it.isNotBlank() }.toSet()
        return hideTask to hidePreview
    }

    private fun configFile(context: Context, name: String): File =
        File(context.applicationContext.filesDir, "xposed/$name")
}
