package com.krafttools.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest is the app's privacy claim, and a privacy claim nobody
 * can check is not a claim worth making.
 *
 * Four permissions this app never asks for and never uses — a wake
 * lock, network state, boot completed and foreground service — arrived
 * silently through `androidx.work`, which comes in transitively via
 * Glance. They showed up in Android's permission list and in every
 * privacy scanner, directly contradicting both the manifest's own
 * comment and the README's "no background work of any kind". They are
 * removed with `tools:node="remove"`.
 *
 * The danger is that this is silent. A dependency bump can bring them
 * back with no build error, no failing test, and nothing visible in the
 * source — the removal only shows up in the *merged* manifest, which no
 * unit test normally reads. So this test pins both halves: the exact
 * declared set, and the presence of the four removals that make the
 * merged manifest come out right.
 */
class ManifestTest {

    private val manifest: String = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun theManifestDeclaresExactlyThePermissionsTheAppUses() {
        val declared = Regex(
            """<uses-permission\s+android:name="([^"]+)"([^>]*>?)""",
        )
            .findAll(manifest)
            // A removal is still a `<uses-permission>` element, so it
            // has to be excluded explicitly or the injected set and
            // the real set are indistinguishable.
            .filterNot { it.groupValues[2].contains("""tools:node="remove"""") }
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("com.krafttools.app.") }
            .toSet()

        val expected = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.VIBRATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )
        assertEquals(
            "the declared permission set changed. If a new one is " +
                "genuinely needed, add it here and say why in the " +
                "README; if it is not, this is a privacy regression",
            expected,
            declared,
        )
    }

    @Test
    fun theFourInjectedWorkManagerPermissionsStayRemoved() {
        // Each must appear exactly once, as a removal. A second
        // occurrence as a plain declaration would mean the merge could
        // resolve either way depending on the manifest merger version.
        val injected = listOf(
            "android.permission.WAKE_LOCK",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
        )
        for (permission in injected) {
            val entries = Regex(
                """<uses-permission[^>]*android:name="""" + permission +
                    """"[^>]*>""",
            ).findAll(manifest).toList()
            assertEquals(
                "$permission should appear exactly once, as a removal",
                1,
                entries.size,
            )
            assertTrue(
                "$permission is declared but not removed, so " +
                    "androidx.work puts it back in the merged manifest",
                entries.single().value.contains("""tools:node="remove""""),
            )
        }
    }

    @Test
    fun theAppStillCannotReachTheNetwork() {
        // Not "the app does not make network calls" — that is a claim
        // about code, and code changes. This is a claim about the
        // platform: without INTERNET the kernel refuses every socket, so
        // egress is impossible no matter what the code does. If this
        // ever needs to become false, the app has changed identity and
        // the README's privacy section has to be rewritten.
        assertTrue(
            "INTERNET is declared. KraftTools is a local instrument " +
                "with no server component; a permission here is not a " +
                "feature, it is a contradiction of every privacy claim " +
                "the app and its README make",
            !manifest.contains("android.permission.INTERNET"),
        )
    }

    @Test
    fun backupsAreOff() {
        // The app writes almost nothing, but "almost" is not a privacy
        // property. `allowBackup=false` is.
        assertTrue(
            "android:allowBackup is not false, so the app's data can " +
                "leave the device in a cloud backup",
            Regex("""android:allowBackup="false"""").containsMatchIn(manifest),
        )
    }
}
