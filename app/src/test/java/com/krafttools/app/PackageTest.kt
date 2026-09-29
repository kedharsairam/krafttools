package com.krafttools.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Guards the com.toolbox -> com.krafttools rename fallout: every
 * Kotlin file's package must match its directory, or cross-file
 * references fail with misleading "unresolved" errors. */
class PackageTest {
    @Test
    fun packagesMatchPaths() {
        val root = File("src/main/java")
        val bad = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                val rel = f.relativeTo(root).parent.replace(File.separatorChar, '.')
                val decl = f.readLines().firstOrNull { it.startsWith("package ") }
                    ?.removePrefix("package ")?.trim()
                if (decl != rel) bad.add("${f.path}: declares $decl, lives in $rel")
            }
        assertEquals(emptyList<String>(), bad)
    }
}
