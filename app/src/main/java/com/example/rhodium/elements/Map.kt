package com.example.rhodium.elements

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.rhodium.database.MapEntity
import com.example.rhodium.elements.dataStore
import kotlin.collections.toMutableSet
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "maps")

suspend fun saveMapUri(uri: String, context: Context) {
    context.dataStore.edit { preferences ->
        val mapList = preferences[stringSetPreferencesKey("map_list")]?.toMutableSet() ?: mutableSetOf()
        mapList.add(uri)
        preferences[stringSetPreferencesKey("map_list")] = mapList
    }
}

@Composable
fun MapListItem(map: MapEntity, onClickEdit: () -> Unit, onClickView: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = map.name)
        Row {
            IconButton(onClick = { onClickEdit() }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Map")
            }
            IconButton(onClick = { onClickView() }) {
                Icon(Icons.Default.Info, contentDescription = "View Map")
            }
            IconButton(onClick = { onDelete() }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Map")
            }
        }
    }
}
