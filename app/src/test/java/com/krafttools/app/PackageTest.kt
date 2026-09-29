package com.krafttools.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the com.toolbox -> com.krafttools rename fallout: every
 * Kotlin file's package must match its directory, or cross-file
 * references fail with misleading "unresolved" errors. */
class PackageTest {
    @Test
    fun packagesMatchPaths() {
        val root = File("src/main/java")
        // Without this the test passes vacuously: walkTopDown on a
        // path that does not exist yields just the root, `isFile` is
        // false, `bad` stays empty and the assertion is green having
        // checked nothing. Delete the source tree and the guard for the
        // package rename still "passes".
        assertTrue(
            "no sources found at ${root.absolutePath}",
            root.isDirectory,
        )
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
