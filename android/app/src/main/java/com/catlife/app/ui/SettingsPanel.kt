package com.catlife.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catlife.app.PanelShell
import com.catlife.app.backup.AndroidBackupService
import com.catlife.app.backup.UserBackup
import com.catlife.app.settings.LocationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun SettingsPanel(
    catVoiceEnabled: Boolean,
    onCatVoiceEnabledChange: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    backupService: AndroidBackupService,
    locationSettings: LocationSettings,
    operationScope: CoroutineScope,
    onShowTutorial: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val location by locationSettings.location.collectAsState(initial = null)
    val selectedLocation by locationSettings.selectedLocation.collectAsState(initial = null)
    var editingLocation by rememberSaveable { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var localBusy by remember { mutableStateOf(false) }
    var waitingForPicker by rememberSaveable { mutableStateOf(false) }
    val serviceWorking by backupService.isWorking.collectAsState()
    val busy = localBusy || waitingForPicker || serviceWorking
    var pendingImport by remember { mutableStateOf<UserBackup?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun notify(text: String) {
        message = text
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        waitingForPicker = false
        if (uri == null) localBusy = false
        else {
            localBusy = true
            operationScope.launch {
                try {
                    backupService.export(uri)
                    notify("バックアップを保存しました")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    notify("バックアップに失敗しました: ${error.message.orEmpty()}")
                } finally { localBusy = false }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        waitingForPicker = false
        if (uri == null) localBusy = false
        else {
            localBusy = true
            operationScope.launch {
                try {
                    pendingImport = backupService.readImport(uri)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    notify("インポートできません: ${error.message.orEmpty()}")
                } finally { localBusy = false }
            }
        }
    }
    PanelShell("設定", { if (!busy) onClose() }) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("猫の音声", modifier = Modifier.weight(1f))
                Switch(catVoiceEnabled, onCatVoiceEnabledChange, enabled = !busy)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("効果音", modifier = Modifier.weight(1f))
                Switch(soundEnabled, onSoundEnabledChange, enabled = !busy)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = !busy) {
                    if (locationSettings.catalog == null) notify("地域データを読み込み中です")
                    else { locationError = null; editingLocation = true }
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("現在地", style = MaterialTheme.typography.labelLarge)
                    Text(location ?: "未設定", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("›")
            }
            HorizontalDivider()
            TextButton(onClick = {
                waitingForPicker = true
                try { export.launch("KorokkeLife-${LocalDate.now()}.klbackup") }
                catch (error: Exception) { waitingForPicker = false; notify("保存先の選択画面を開けません") }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text("バックアップ")
                    Text("Google Driveなどにデータを保存", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = {
                waitingForPicker = true
                try { import.launch(arrayOf("*/*")) }
                catch (error: Exception) { waitingForPicker = false; notify("ファイルの選択画面を開けません") }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text("インポート")
                    Text("Google Driveなどからデータを復元", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (editingLocation) {
        locationSettings.catalog?.let { catalog ->
            RegionPickerDialog(
                catalog = catalog,
                savedName = location,
                savedLocation = selectedLocation,
                busy = busy,
                error = locationError,
                onDismiss = { editingLocation = false },
                onSave = { selected ->
                    localBusy = true
                    operationScope.launch {
                        try { locationSettings.save(selected); editingLocation = false }
                        catch (error: Exception) {
                            if (error is CancellationException) throw error
                            locationError = error.message ?: "現在地を保存できませんでした"
                        } finally { localBusy = false }
                    }
                },
            )
        }
    }
    pendingImport?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingImport = null },
            title = { Text("バックアップを復元") },
            text = { Text("TODO ${snapshot.todos.size}件・買い物 ${snapshot.shopping.size}件と設定を復元します。現在のデータを置き換えます。") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    localBusy = true
                    operationScope.launch {
                        try {
                            backupService.restore(snapshot)
                            pendingImport = null
                            notify("バックアップを復元しました")
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            pendingImport = null
                            notify("復元に失敗しました。必要な回復は次回起動時にも実行します: ${error.message.orEmpty()}")
                        } finally { localBusy = false }
                    }
                }) { Text("復元") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingImport = null }) { Text("キャンセル") } },
        )
    }
}
