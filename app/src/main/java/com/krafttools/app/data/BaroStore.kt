package com.krafttools.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.baroStore by preferencesDataStore("baro")

/**
 * Last barometer reading, shared with the home-screen widget.
 * Written by the Barometer screen as it samples (~15 s cadence);
 * the widget only ever READS. No background work anywhere — the
 * widget refreshes on tap, never on a timer. Zero battery cost
 * beyond the screen the user already opened.
 */
class BaroStore(context: Context) {
    private val app = context.applicationContext
    private val store get() = app.baroStore

    val reading: Flow<BaroReading> = app.baroStore.data.map { p ->
        BaroReading(
            hpa = p[Keys.HPA],
            trend = p[Keys.TREND],
            atMillis = p[Keys.AT] ?: 0L,
        )
    }

    suspend fun save(hpa: Float, trend: String?) {
        store.edit {
            it[Keys.HPA] = hpa
            it[Keys.AT] = System.currentTimeMillis()
            if (trend != null) it[Keys.TREND] = trend else it.remove(Keys.TREND)
        }
    }

    private object Keys {
        val HPA = floatPreferencesKey("hpa")
        val TREND = stringPreferencesKey("trend")
        val AT = longPreferencesKey("at")
    }
}

data class BaroReading(
    val hpa: Float?,
    val trend: String?,
    val atMillis: Long,
)
