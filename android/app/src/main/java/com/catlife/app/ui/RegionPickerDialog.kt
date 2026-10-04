package com.catlife.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.catlife.app.settings.LocationCatalog
import com.catlife.app.settings.RegionSelectionDraft
import com.catlife.app.settings.WeatherLocation

@Composable
internal fun RegionPickerDialog(
    catalog: LocationCatalog,
    savedName: String?,
    savedLocation: WeatherLocation?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (WeatherLocation) -> Unit,
) {
    val initial = remember(catalog, savedName, savedLocation) {
        savedLocation?.let { catalog.find(it.prefectureCode, it.municipalityCode) }
            ?: catalog.findDisplayName(savedName)
    }
    var prefectureCode by rememberSaveable { mutableStateOf(initial?.prefectureCode) }
    var municipalityCode by rememberSaveable { mutableStateOf(initial?.municipalityCode) }
    var choosingPrefecture by rememberSaveable { mutableStateOf(initial == null) }
    var query by rememberSaveable { mutableStateOf("") }
    val draft = RegionSelectionDraft(prefectureCode, municipalityCode)
    val selected = draft.selection(catalog)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("地域を選択") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null && !savedName.isNullOrBlank()) {
                    Text("保存済み：$savedName", style = MaterialTheme.typography.bodySmall)
                }
                if (choosingPrefecture) {
                    Text("都道府県を選択")
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = catalog.prefectures.indexOfFirst { it.code == prefectureCode }.coerceAtLeast(0))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), state = state) {
                        items(catalog.prefectures, key = { it.code }) { prefecture ->
                            Text(
                                prefecture.name,
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    val next = draft.selectPrefecture(prefecture.code)
                                    prefectureCode = next.prefectureCode
                                    municipalityCode = next.municipalityCode
                                    query = ""
                                    choosingPrefecture = false
                                }.padding(horizontal = 12.dp, vertical = 14.dp),
                            )
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(catalog.prefectures.firstOrNull { it.code == prefectureCode }?.name.orEmpty(), Modifier.weight(1f))
                        TextButton(enabled = !busy, onClick = { choosingPrefecture = true }) { Text("都道府県を変更") }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("市区町村を検索") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val municipalities = catalog.searchMunicipalities(prefectureCode.orEmpty(), query)
                    if (municipalities.isEmpty()) Text("該当する市区町村がありません")
                    key(prefectureCode, query) {
                        val state = rememberLazyListState(initialFirstVisibleItemIndex = municipalities.indexOfFirst { it.municipalityCode == municipalityCode }.coerceAtLeast(0))
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), state = state) {
                            items(municipalities, key = { it.municipalityCode }) { municipality ->
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !busy) { municipalityCode = municipality.municipalityCode }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = municipalityCode == municipality.municipalityCode, onClick = null)
                                    Text(municipality.municipalityName, Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
                selected?.let { Text("選択：${it.displayName}") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !choosingPrefecture && selected != null, onClick = { selected?.let(onSave) }) { Text("保存") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("キャンセル") } },
    )
}
