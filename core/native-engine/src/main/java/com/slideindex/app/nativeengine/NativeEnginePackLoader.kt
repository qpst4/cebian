package com.slideindex.app.nativeengine

import java.io.File

internal object NativeEnginePackLoader {
    private val loadedLibraries = LinkedHashSet<String>()
    private var loadedOcrPackRevision: Int? = null

    fun loadedOcrPackRevision(): Int? = loadedOcrPackRevision

    fun requiresProcessRestartForRevision(packRevision: Int): Boolean {
        val loadedRevision = loadedOcrPackRevision ?: return false
        return loadedLibraries.isNotEmpty() && loadedRevision != packRevision
    }

    @Synchronized
    fun loadLibraries(libDir: File, libraryNames: List<String>, packRevision: Int? = null) {
        if (packRevision != null && requiresProcessRestartForRevision(packRevision)) {
            throw IllegalStateException(
                "native_engine_stale: loadedRevision=$loadedOcrPackRevision requestedRevision=$packRevision",
            )
        }
        check(libDir.isDirectory) { "native_lib_dir_missing" }
        for (name in libraryNames) {
            if (name in loadedLibraries) continue
            val file = File(libDir, name)
            check(file.isFile) { "native_library_missing:$name" }
            System.load(file.absolutePath)
            loadedLibraries.add(name)
        }
        if (packRevision != null) {
            loadedOcrPackRevision = packRevision
        }
    }

    @Synchronized
    fun isLibraryLoaded(libraryName: String): Boolean = libraryName in loadedLibraries
}
