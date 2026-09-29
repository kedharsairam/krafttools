package com.krafttools.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source lints for mistakes that compile cleanly and reach a user's
 * screen. They exist because each of these cost real debugging time and
 * none of them produce a compiler warning or a failing build.
 */
class SourceLintTest {

    private fun uiSources(): List<File> {
        val root = File("src/main/java/com/krafttools/app")
        assertTrue("no sources found at ${root.absolutePath}", root.isDirectory)
        return root.walkTopDown()
            .filter { it.extension == "kt" }
            .toList()
    }

    private fun linesOf(file: File): List<String> = file.readLines()

    /**
     * Every user-visible format gets an explicit locale.
     *
     * `"%.1f".format(x)` uses the default locale, so on a device set to
     * German or French the decimal point becomes a comma: the speed
     *ometer reads "23,5", the dial says 23.5, and the tool is
     * self-contradictory. 76 call sites were affected.
     *
     * The exemption is deliberate and narrow: `SimpleDateFormat` has
     * already been given its locale at construction, and its `format`
     * takes a Date, not a format string. A regex sweep that "fixes"
     * those produces code that does not compile, which is at least
     * loud, but it is not worth having.
     */
    @Test
    fun everyStringFormatNamesItsLocale() {
        val offenders = mutableListOf<String>()
        for (file in uiSources()) {
            linesOf(file).forEachIndexed { i, line ->
                val at = line.indexOf(".format(")
                if (at < 0) return@forEachIndexed
                // A date or number formatter, not a String format.
                val before = line.substring(0, at)
                // A date or number formatter is constructed with its
                // own locale and its format() takes a Date, not a
                // format string: "SimpleDateFormat("HH:mm",
                // Locale.getDefault()).format(date)".
                if (before.contains("SimpleDateFormat(") ||
                    before.contains("DateTimeFormatter(") ||
                    before.contains("NumberFormat(")
                ) {
                    return@forEachIndexed
                }
                val rest = line.substring(at)
                if (!rest.contains("Locale.")) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(
            "these .format() calls use the default locale, so a German " +
                "or French device gets comma decimal separators: " +
                offenders,
            offenders.isEmpty(),
        )
    }

    /**
     * The house tabular-figures setting reaches every style that shows
     * a changing number.
     *
     * In a proportional face "1" is narrower than "8", so a live
     * readout shifts horizontally as the value changes: a speedometer
     * counting 9 -> 10 visibly jumps, and a clocked value appears to
     * crawl. Every style in this app shows a number that changes, so
     * they all carry `tnum`.
     */
    @Test
    fun everyTypographyStyleCarriesTabularFigures() {
        val theme = uiSources().first { it.name == "Theme.kt" }
        val text = theme.readText()
        val block = text.substringAfter("val ToolboxTypography").substringBefore("\n)")
        for (style in listOf(
            "displayLarge", "headlineMedium", "titleLarge",
            "titleMedium", "labelMedium",
        )) {
            val line = block.lines()
                .firstOrNull { it.trimStart().startsWith("$style =") }
            assertTrue("no $style in the typography block", line != null)
            assertTrue(
                "$style has no tnum, so its digits shift width as the " +
                    "value changes: ${line!!.trim()}",
                line.contains("\"tnum\""),
            )
        }
    }

    /**
     * Canvas text uses the house typeface.
     *
     * Four of the five inline `TextStyle`s built for canvas measurement
     * pull the family from the theme. The fifth — the speedometer's
     * dial numerals — did not, so the most-read numbers in the app
     * rendered in the system default while every other gauge label used
     * the house face.
     */
    @Test
    fun canvasTextStylesCarryTheHouseTypeface() {
        val offenders = mutableListOf<String>()
        for (file in uiSources()) {
            val lines = linesOf(file)
            lines.forEachIndexed { i, line ->
                if (!line.contains("TextStyle(")) return@forEachIndexed
                // Look at the whole constructor, which may span lines.
                val window = lines.drop(i).take(8).joinToString("\n")
                if (!window.contains("fontSize")) return@forEachIndexed
                if ("fontFamily" in window) return@forEachIndexed
                if (!window.contains("fontSize")) return@forEachIndexed
                // Only canvas measurement styles are affected: a TextStyle
                // with no colour or size is not one of ours.
                if (window.contains("color =")) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(
            "these canvas TextStyles drop the house typeface: $offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * Per-sample accumulation never happens in a keyed effect.
     *
     * Compose cancels and relaunches a `LaunchedEffect` whenever its key
     * changes, so `LaunchedEffect(tick)` where `tick` increments per
     * sensor sample runs its body at the DISPLAY rate, not the sample
     * rate — and any decay or accumulator inside it advances far too
     * slowly. Two such sites shipped in this codebase already.
     */
    /** Strip `//` comments and the contents of `/* */` blocks. */
    private fun codeOnly(lines: List<String>): List<String> {
        var inBlock = false
        return lines.map { raw ->
            var line = raw
            if (inBlock) {
                val end = line.indexOf("*/")
                if (end < 0) return@map ""
                line = line.substring(end + 2)
                inBlock = false
            }
            while (true) {
                val start = line.indexOf("/*")
                if (start < 0) break
                val end = line.indexOf("*/", start + 2)
                if (end < 0) {
                    line = line.substring(0, start)
                    inBlock = true
                    break
                }
                line = line.substring(0, start) + " " + line.substring(end + 2)
            }
            line.substringBefore("//")
        }
    }

    /**
     * The body of a call whose opening brace is on line [i], matched by
     * counting braces. Returns null when the braces do not balance,
     * which means the line is not a call body at all.
     */
    private fun braceBody(lines: List<String>, i: Int): String? {
        var depth = 0
        val out = StringBuilder()
        var started = false
        for (line in lines.drop(i)) {
            for (c in line) {
                when (c) {
                    '{' -> {
                        depth++
                        started = true
                    }
                    '}' -> {
                        depth--
                        if (started && depth == 0) return out.toString()
                    }
                }
            }
            // The line's TEXT, not just the separator. Without this the
            // body is a run of newlines and the lint silently matches
            // nothing — which is worse than having no lint, because it
            // looks like it is passing.
            if (started) out.append(line).append('\n')
        }
        return null
    }

    @Test
    fun accumulatorsDoNotLiveInKeyedEffects() {
        // Accumulation verbs. `reset()` is NOT one of them: clearing a
        // latch when a threshold changes is a one-shot invalidation
        // keyed on exactly the right thing, and it must fire whenever
        // the key does.
        val acc = Regex(
            "\\.(add|addLast|addFirst|removeFirst|removeAt|update|offer)\\(|" +
                "\\+\\+|\\bmaxOf\\(|\\bminOf\\(|maxOfOrNull\\(|minOfOrNull\\(",
        )
        val offenders = mutableListOf<String>()
        for (file in uiSources()) {
            val lines = codeOnly(linesOf(file))
            lines.forEachIndexed { i, line ->
                if (!line.contains("LaunchedEffect(")) return@forEachIndexed
                val key = line.substringAfter("LaunchedEffect(")
                    .substringBefore(')')
                    .trim()
                // A per-sample key is a bare identifier. `Unit` is the
                // fire-once form, and a nav/route key is a navigation
                // event, not a sample counter.
                if (!key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return@forEachIndexed
                if (key == "Unit" || key.startsWith("nav") || key.startsWith("route")) {
                    return@forEachIndexed
                }
                val body = braceBody(lines, i) ?: return@forEachIndexed
                if (acc.containsMatchIn(body)) {
                    offenders += "${file.name}:${i + 1} (key: $key)"
                }
            }
        }
        assertTrue(
            "these keyed effects accumulate per-sample state, which " +
                "advances at the display rate instead of the sample " +
                "rate: $offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * No literal pixel widths in a draw scope.
     *
     * `strokeWidth = 2f` is two PHYSICAL pixels: 0.67dp on a 3x screen
     * and 2dp on a 1x one, so the same code draws a hairline on a
     * flagship and a visible line on a cheap handset. Every such literal
     * has to be `N.dp.toPx()`.
     *
     * The exemption is a bare float inside `Stroke(...)` that is already
     * a computed dp conversion, and any expression mentioning `size` —
     * a proportion of the panel is the correct unit for those, and
     * converting them would make the drawing ignore its own box.
     */
    @Test
    fun drawScopesDoNotUseLiteralPixelWidths() {
        val literal = Regex(
            "(strokeWidth\\s*=\\s*[0-9.]+f\\b)|" +
                "(Stroke\\([^)]*[0-9.]+f[^)]*\\))|" +
                "(CornerRadius\\([0-9.]+f)|" +
                "(\\bradius\\s*=\\s*[0-9.]+f\\b)",
        )
        val offenders = mutableListOf<String>()
        for (file in uiSources()) {
            codeOnly(linesOf(file)).forEachIndexed { i, line ->
                if (".toPx()" in line) return@forEachIndexed
                if (!Regex("draw|stroke|Stroke|CornerRadius").containsMatchIn(line)) {
                    return@forEachIndexed
                }
                // A proportion of the panel is the right unit already.
                if (Regex("size\\.|\\*\\s*r\\b|\\*\\s*radius|radius\\s*\\*").containsMatchIn(line)) {
                    return@forEachIndexed
                }
                if (literal.containsMatchIn(line)) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(
            "these draw calls use literal pixels, which is 0.67dp on a " +
                "3x screen and 2dp on a 1x one: $offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * A tested pure function is actually called by the app.
     *
     * This one is the most important lint in the file, because it
     * catches a failure mode that looks like SUCCESS. This codebase
     * ended up with `SpeedMath.haversineKm` and
     * `SpeedMath.isPlausibleStep` carrying twelve unit tests between
     * them while the live odometer ran byte-identical private copies
     * one file over. The suite was green, the functions were verified
     * against closed forms, and the code the user actually executes had
     * none of that coverage. A green test suite and a tested function
     * are not the same thing.
     *
     * The check is: a top-level function in a `*Math.kt` / pure-helper
     * file, referenced by a test but by no production source. Dead
     * helpers with no tests at all are a lesser problem (they are
     * merely clutter) and are reported separately.
     */
    @Test
    fun testedPureFunctionsAreTheOnesTheAppRuns() {
        val main = uiSources()
        val mainText = main.joinToString("\n") { it.readText() }
        val testDir = File("src/test/java/com/krafttools/app")
        if (!testDir.isDirectory) return
        val testText = testDir.listFiles()
            ?.filter { it.extension == "kt" }
            ?.joinToString("\n") { it.readText() }
            ?: return

        val orphans = mutableListOf<String>()
        for (file in main) {
            if (!file.name.endsWith("Math.kt") && !file.name.endsWith("Scale.kt") &&
                !file.name.endsWith("Fft.kt") && !file.name.endsWith("AcFilter.kt") &&
                !file.name.endsWith("AudioKit.kt") && !file.name.endsWith("LumaPlane.kt")
            ) {
                continue
            }
            // MULTILINE is essential and its absence is why the first
            // version of this lint passed vacuously: without it `^`
            // matches only at the start of the whole file, so no
            // function declaration was ever found and the orphan list
            // was always empty.
            val decl = Regex(
                "^(?:internal |private |public )?fun ([a-zA-Z][A-Za-z0-9_]*)\\(",
                RegexOption.MULTILINE,
            )
                .findAll(file.readText())
                .map { it.groupValues[1] }
                .toList()
            for (name in decl) {
                if (name in KOTLIN_NOISE) continue
                // Referenced by a test, and by production code other
                // than its own declaration.
                val tested = Regex("\\b$name\\s*\\(").containsMatchIn(testText)
                if (!tested) continue
                val uses = Regex("\\b$name\\s*\\(").findAll(mainText).count()
                // One hit = the declaration itself.
                if (uses <= 1) orphans += "${file.name}::$name"
            }
        }
        assertTrue(
            "these functions are covered by tests but never called by the " +
                "app, so the tested code is not the code that runs: " +
                "$orphans",
            orphans.isEmpty(),
        )
    }

    /** Names that look like helpers but are language or Compose idioms. */
    private val KOTLIN_NOISE = setOf(
        "map", "filter", "forEach", "let", "also", "apply", "run", "with",
        "maxOf", "minOf", "require", "check", "listOf", "setOf", "buildList",
        "format", "joinToString", "sortedBy", "associateBy", "distinctBy",
        "takeLast", "dropLast", "coerceAtLeast", "coerceIn", "let",
    )

    /**
     * An enum is never held in `rememberSaveable` without a saver.
     *
     * A Kotlin enum is neither `Parcelable` nor `Serializable`, so the
     * default auto-saver throws when the process is recreated — the one
     * moment `rememberSaveable` exists to handle.
     */
    @Test
    fun savedStateNeverHoldsAnUnsavedEnum() {
        val offenders = mutableListOf<String>()
        for (file in uiSources()) {
            val lines = linesOf(file)
            lines.forEachIndexed { i, line ->
                if (!line.contains("rememberSaveable")) return@forEachIndexed
                val window = lines.drop(i).take(4).joinToString("\n")
                // A type that is a known enum in this codebase, held
                // without an explicit saver.
                if (Regex("mutableStateOf\\([A-Z][A-Za-z]*\\.").containsMatchIn(window) &&
                    !window.contains("stateSaver") &&
                    !window.contains("saver =")
                ) {
                    offenders += "${file.name}:${i + 1}"
                }
            }
        }
        assertTrue(
            "an enum is in rememberSaveable with no saver, which throws " +
                "on process death: $offenders",
            offenders.isEmpty(),
        )
    }
}
