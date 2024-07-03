package com.example.rhodium

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "maps")

suspend fun saveMapUri(uri: String, context: Context) {
    context.dataStore.edit { preferences ->
        val mapList = preferences[stringSetPreferencesKey("map_list")]?.toMutableSet() ?: mutableSetOf()
        mapList.add(uri)
        preferences[stringSetPreferencesKey("map_list")] = mapList
    }
}

suspend fun getMapUris(context: Context): Set<String>? {
    val preferences = context.dataStore.data.first()
    return preferences[stringSetPreferencesKey("map_list")]
}
