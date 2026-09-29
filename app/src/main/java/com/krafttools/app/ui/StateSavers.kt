package com.krafttools.app.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Custom rememberSaveable savers. Primitives survive rotation natively;
 * tuples and lists need these — without them the app either loses
 * session state on rotate or crashes trying to save an unsaveable.
 *
 * Value savers wrap MutableState so `var x by rememberSaveable`
 * keeps working; list savers wrap SnapshotStateList so .add/.remove
 * keep working.
 */
val floatTripleSaver: Saver<MutableState<Triple<Float, Float, Float>?>, Any> =
    Saver(
        save = { state ->
            val t = state.value
            if (t == null) emptyList<Float>() else listOf(t.first, t.second, t.third)
        },
        restore = { restored ->
            @Suppress("UNCHECKED_CAST")
            val v = restored as List<Float>
            mutableStateOf(
                if (v.size != 3) null else Triple(v[0], v[1], v[2]),
            )
        },
    )

val intTripleSaver: Saver<MutableState<Triple<Int, Int, Int>>, Any> =
    Saver(
        save = { state ->
            listOf(state.value.first, state.value.second, state.value.third)
        },
        restore = { restored ->
            @Suppress("UNCHECKED_CAST")
            val v = restored as List<Int>
            mutableStateOf(
                if (v.size != 3) Triple(0, 0, 0) else Triple(v[0], v[1], v[2]),
            )
        },
    )

val floatPairSaver: Saver<MutableState<Pair<Float, Float>?>, Any> =
    Saver(
        save = { state ->
            val p = state.value
            if (p == null) emptyList<Float>() else listOf(p.first, p.second)
        },
        restore = { restored ->
            @Suppress("UNCHECKED_CAST")
            val v = restored as List<Float>
            mutableStateOf(
                if (v.size != 2) null else Pair(v[0], v[1]),
            )
        },
    )

val stringListSaver: Saver<SnapshotStateList<String>, ArrayList<String>> =
    Saver(
        save = { state -> ArrayList(state) },
        restore = { restored ->
            mutableStateListOf<String>().also { it.addAll(restored) }
        },
    )

val floatListSaver: Saver<SnapshotStateList<Float>, ArrayList<Float>> =
    Saver(
        save = { state -> ArrayList(state) },
        restore = { restored ->
            mutableStateListOf<Float>().also { it.addAll(restored) }
        },
    )

/** Mutable list of RGB triples, flattened for the bundle. */
val intTripleListSaver: Saver<SnapshotStateList<Triple<Int, Int, Int>>, ArrayList<List<Int>>> =
    Saver(
        save = { list ->
            ArrayList(list.map { listOf(it.first, it.second, it.third) })
        },
        restore = { restored ->
            @Suppress("UNCHECKED_CAST")
            val chunks = restored as List<List<Int>>
            mutableStateListOf<Triple<Int, Int, Int>>().also { out ->
                chunks.forEach { c ->
                    if (c.size == 3) out.add(Triple(c[0], c[1], c[2]))
                }
            }
        },
    )

/**
 * A saver for a plain enum, keyed by its NAME.
 *
 * The band selector holds a `WifiBand` in `rememberSaveable`. A Kotlin
 * enum is neither `Parcelable` nor `Serializable`, so the default
 * autoSaver throws when the process is killed and recreated — which is
 * the one moment `rememberSaveable` exists to handle.
 *
 * It is keyed by name, never by ordinal. An ordinal is an
 * implementation detail: reordering the enum would silently remap every
 * saved selection, so a user who had chosen 6 GHz would come back to
 * 2.4 GHz after an unrelated edit. That is the enum-ordinal trap this
 * file exists to keep out of the codebase.
 */
inline fun <reified E : Enum<E>> enumSaver(default: E): Saver<E, String> = Saver(
    save = { it.name },
    restore = { saved ->
        // A name from an older build may no longer exist. Falling back
        // is right: a crash on launch is not.
        runCatching { enumValueOf<E>(saved) }.getOrDefault(default)
    },
)
