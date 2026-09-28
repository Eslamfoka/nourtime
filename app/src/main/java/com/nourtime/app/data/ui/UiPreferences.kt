package com.nourtime.app.data.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** How time is picked (U1). Chosen per phone: the parent's phone keeps its own. */
enum class TimePickerStyle {
    DIAL, TOKENS, LIQUID,

    /** A different real theme each time a picker appears. */
    SURPRISE;

    fun resolve(random: Random = Random.Default): TimePickerStyle =
        if (this == SURPRISE) listOf(DIAL, TOKENS, LIQUID).random(random) else this
}

/** Look-and-feel choices that belong to this phone only (not synced to the other phone). */
@Singleton
class UiPreferences @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val timePickerStyle: Flow<TimePickerStyle> = store.data.map { prefs ->
        prefs[TIME_PICKER_STYLE]?.let { name -> TimePickerStyle.entries.firstOrNull { it.name == name } } ?: TimePickerStyle.DIAL
    }.distinctUntilChanged()

    suspend fun setTimePickerStyle(style: TimePickerStyle) {
        store.edit { it[TIME_PICKER_STYLE] = style.name }
    }

    private companion object {
        val TIME_PICKER_STYLE = stringPreferencesKey("ui_time_picker_style")
    }
}
