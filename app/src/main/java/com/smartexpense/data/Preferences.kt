package com.smartexpense.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
private val Context.preferences by preferencesDataStore("tracker-settings")
@Singleton class Preferences @Inject constructor(@param:ApplicationContext private val context:Context) {
    private val compact=booleanPreferencesKey("compact-amounts")
    val compactAmounts=context.preferences.data.map { it[compact]?:false }
    suspend fun setCompact(value:Boolean) { context.preferences.edit { it[compact]=value } }
}
