package com.slideindex.app.nativeengine

import java.io.File

internal object NativeEnginePackLoader {
    private val loadedLibraries = LinkedHashSet<String>()

    @Synchronized
    fun loadLibraries(libDir: File, libraryNames: List<String>) {
        check(libDir.isDirectory) { "native_lib_dir_missing" }
        for (name in libraryNames) {
            if (name in loadedLibraries) continue
            val file = File(libDir, name)
            check(file.isFile) { "native_library_missing:$name" }
            System.load(file.absolutePath)
            loadedLibraries.add(name)
        }
    }

    @Synchronized
    fun isLibraryLoaded(libraryName: String): Boolean = libraryName in loadedLibraries
}
