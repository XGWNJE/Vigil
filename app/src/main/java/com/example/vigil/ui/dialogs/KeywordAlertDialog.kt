// src/main/java/com/example/vigil/ui/dialogs/KeywordAlertDialog.kt
package com.example.vigil.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
// import androidx.compose.foundation.layout.Row // 如果未使用可以移除
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
// import androidx.compose.foundation.layout.height // 如果未使用可以移除
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
// import androidx.compose.material3.AlertDialogDefaults // 如果未使用可以移除
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults // *** 添加此 import ***
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
// import androidx.compose.material3.TextButton // 如果未使用可以移除
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.vigil.R
import com.example.vigil.ui.theme.VigilTheme

@Composable
fun KeywordAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    matchedKeyword: String?,
    dialogTitle: String = stringResource(R.string.alert_dialog_title),
    icon: ImageVector = Icons.Filled.Warning
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(all = 24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = dialogTitle,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                val message = if (matchedKeyword != null) {
                    stringResource(R.string.alert_dialog_message_keyword_format, matchedKeyword)
                } else {
                    stringResource(R.string.alert_dialog_message_default)
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize) // 现在 ButtonDefaults 应该能被解析
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing)) // 现在 ButtonDefaults 应该能被解析
                    Text(stringResource(R.string.alert_dialog_confirm_button_long))
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun KeywordAlertDialogPreview() {
    VigilTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            KeywordAlertDialog(
                onDismissRequest = {},
                onConfirm = {},
                matchedKeyword = "测试"
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun KeywordAlertDialogDarkPreview() {
    VigilTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            KeywordAlertDialog(
                onDismissRequest = {},
                onConfirm = {},
                matchedKeyword = "紧急"
            )
        }
    }
}
