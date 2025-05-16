// src/main/java/com/example/vigil/ui/monitoring/MonitoringScreen.kt
package com.example.vigil.ui.monitoring

import android.app.Activity
import android.app.Application // 确保导入 Application
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.Color // 如果下面的颜色都用 MaterialTheme.colorScheme 替代，则可以移除
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.MainActivity
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.ui.theme.VigilTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val keywords by viewModel.keywords
    val selectedRingtoneName by viewModel.selectedRingtoneName
    val serviceEnabled by viewModel.serviceEnabled
    val serviceStatusText by viewModel.serviceStatusText
    val showRestartButton by viewModel.showRestartButton
    val environmentWarnings by viewModel.environmentWarnings

    val ringtoneSelectionTitle = stringResource(R.string.ringtone_selection_title)

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            viewModel.onRingtoneUriSelected(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.enable_service_switch),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Switch(
                        checked = serviceEnabled,
                        onCheckedChange = { isChecked ->
                            val isLicensed = true // TODO: 从 ViewModel 获取实际授权状态
                            viewModel.onServiceEnabledChange(
                                isChecked,
                                isLicensed,
                                startServiceCallback = { hasPermission ->
                                    if (activity is MainActivity) {
                                        activity.startVigilService(hasPermission)
                                    }
                                },
                                stopServiceCallback = {
                                    if (activity is MainActivity) {
                                        activity.stopVigilService()
                                    }
                                }
                            )
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.service_status_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = serviceStatusText,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = when (serviceStatusText) {
                            stringResource(R.string.service_running) -> MaterialTheme.colorScheme.primary
                            stringResource(R.string.service_stopped) -> MaterialTheme.colorScheme.onSurfaceVariant
                            stringResource(R.string.service_status_recovering) -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }

                if (showRestartButton) {
                    Button(
                        onClick = {
                            viewModel.onRestartServiceClick {
                                if (activity is MainActivity) {
                                    activity.restartService()
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.restart_service_button))
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.notification_configuration_title),
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = keywords,
                    onValueChange = { viewModel.onKeywordsChange(it) },
                    label = { Text(stringResource(R.string.keywords_label)) },
                    placeholder = { Text(stringResource(R.string.keywords_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 96.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtoneSelectionTitle)
                                viewModel.selectedRingtoneUri.value?.let { uri ->
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                                }
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            }
                            try {
                                ringtonePickerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开铃声选择器: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.select_ringtone_button))
                    }
                    Text(
                        text = selectedRingtoneName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_settings_button))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.environment_check_label),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = environmentWarnings,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (environmentWarnings == stringResource(R.string.all_clear)) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MonitoringScreenPreview() {
    VigilTheme {
        MonitoringScreen(viewModel = MonitoringViewModel(LocalContext.current.applicationContext as Application))
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MonitoringScreenDarkPreview() {
    VigilTheme(darkTheme = true) {
        MonitoringScreen(viewModel = MonitoringViewModel(LocalContext.current.applicationContext as Application))
    }
}
