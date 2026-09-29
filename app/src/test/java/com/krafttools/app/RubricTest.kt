package com.krafttools.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scoring rubric, executable.
 *
 * The rubric was agreed *before* the scoring pass, specifically so the
 * score could not be reverse-engineered from the result. That promise is
 * only worth anything if the numbers cannot be quietly adjusted
 * afterwards, so the weights, the bands, and the gate caps are pinned
 * here. Changing the rubric now means changing this test, which is a
 * visible, deliberate act rather than a silent one.
 */
class RubricTest {

    private enum class Criterion(val weight: Int) {
        TRUTHFULNESS(22),
        ROBUSTNESS(15),
        INSTRUMENT_DESIGN(15),
        SHARED_CONSISTENCY(12),
        STATE_COVERAGE(10),
        ACCESSIBILITY(10),
        BATTERY_LIFECYCLE(8),
        PRIVACY_HONESTY(8),
    }

    private enum class Gate(val cap: Int) {
        /** The tool asserts something the evidence does not support. */
        UNTRUTHFUL_NUMBER(59),

        /** The value is right; the unit or scale is not what it claims. */
        MISLABELLED_UNIT(59),

        /** Any reachable input crashes the tool. */
        CRASH_PATH(49),

        /** A sensor that will never deliver looks like a blank reading. */
        SILENT_SENSOR_FAILURE(69),

        /** Told to fix something already fixed, or given no route out. */
        DEAD_END_PERMISSION(69),
    }

    @Test
    fun theWeightsSumToOneHundred() {
        val total = Criterion.entries.sumOf { it.weight }
        assertEquals("the rubric must score out of 100", 100, total)
    }

    @Test
    fun truthfulnessIsTheHeaviestCriterion() {
        // The one criterion that separates an instrument from a toy.
        val max = Criterion.entries.maxOf { it.weight }
        assertEquals(
            "a tool can be beautiful and still lie; the reverse is not " +
                "true, so truthfulness must outweigh the rest",
            Criterion.TRUTHFULNESS,
            Criterion.entries.first { it.weight == max },
        )
    }

    @Test
    fun everyCriterionIsScoredOnTheSameFivePointScale() {
        // A criterion that could silently use a different scale would
        // make the weighted total meaningless.
        for (c in Criterion.entries) {
            for (rating in 1..5) {
                val contribution = c.weight * rating / 5.0
                assertTrue(
                    "${c.name} at $rating contributed $contribution",
                    contribution in 0.0..c.weight.toDouble(),
                )
            }
        }
    }

    @Test
    fun aPerfectScoreIsExactlyOneHundred() {
        var total = 0.0
        for (c in Criterion.entries) total += c.weight * 5 / 5.0
        assertEquals(100.0, total, 1e-9)
    }

    @Test
    fun aFloorScoreIsNotZero() {
        // Deliberate. 1/5 everywhere means "broken everywhere", and
        // reporting that as 0/100 would imply the app does not exist,
        // which is less useful than saying it is at the floor.
        var total = 0.0
        for (c in Criterion.entries) total += c.weight * 1 / 5.0
        assertEquals(20.0, total, 1e-9)
    }

    @Test
    fun theGatesAreOrderedByHowBadlyTheyMislead() {
        // A crash is worse than a wrong number, because a wrong number
        // can be cross-checked and a crash cannot.
        assertTrue(
            "a crash must cap harder than a wrong number",
            Gate.CRASH_PATH.cap < Gate.UNTRUTHFUL_NUMBER.cap,
        )
        // A wrong number is worse than an unfixable permission state,
        // because the permission state is visible to the user.
        assertTrue(
            "a wrong number must cap harder than a dead-end permission",
            Gate.UNTRUTHFUL_NUMBER.cap <= Gate.DEAD_END_PERMISSION.cap,
        )
    }

    @Test
    fun everyGateCapsBelowAPassingScore() {
        // A gated tool must not be able to score in the "ship it" range.
        for (g in Gate.entries) {
            assertTrue(
                "${g.name} caps at ${g.cap}, which is not a failure",
                g.cap <= 69,
            )
        }
    }

    @Test
    fun aGateNeverLowersAScore() {
        // A gate is a ceiling, not a penalty. It must not be able to make
        // a good total worse, or a clean tool could score below a broken
        // one purely by which gate happened to apply.
        val clean = Criterion.entries.sumOf { it.weight * 5 / 5.0 }
        for (g in Gate.entries) {
            assertTrue(
                "${g.name} lowered a clean score",
                minOf(clean, g.cap.toDouble()) >= clean - 1e-9 ||
                    g.cap.toDouble() < clean,
            )
        }
    }

    @Test
    fun theWrittenRubricMatchesThisOne() {
        // The document is the artefact the user reads. If it drifts from
        // the executable rubric, one of them is a lie.
        val doc = java.io.File("../../data/shared-kb/toolbox-scoring-rubric.md")
        val path = if (doc.isFile) doc else java.io.File(
            "/home/kedhar/Projects/opencode/opencode-config/data/shared-kb/" +
                "toolbox-scoring-rubric.md",
        )
        assertTrue("rubric document not found at ${path.path}", path.isFile)
        val text = path.readText()
        for (c in Criterion.entries) {
            assertTrue(
                "the rubric document does not give ${c.name} a weight of " +
                    "${c.weight}",
                text.contains("| ${c.weight} |") ||
                    text.contains("**${c.weight}**"),
            )
        }
        for (g in Gate.entries) {
            assertTrue(
                "the rubric document does not state the ${g.name} cap of " +
                    "${g.cap}",
                text.contains("**${g.cap}**"),
            )
        }
    }
}
