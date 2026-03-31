// src/main/java/com/example/vigil/ui/dialogs/KeywordAlertDialog.kt
package com.example.vigil.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.vigil.ui.theme.*

@Composable
fun KeywordAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    matchedKeyword: String?,
    sourceApp: String? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VigilBackground.copy(alpha = 0.8f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, VigilErrorBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp, 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Alert icon ring
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(VigilErrorSubtle, CircleShape)
                            .border(1.dp, VigilErrorBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = VigilError,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Title
                    Text(
                        text = "检测到关键词",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = VigilTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    // Keyword chip
                    Row(
                        modifier = Modifier
                            .background(VigilErrorSubtle, RoundedCornerShape(12.dp))
                            .border(1.dp, VigilErrorBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tag,
                            contentDescription = null,
                            tint = VigilError,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = matchedKeyword ?: "未知",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = VigilError,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Source app
                    if (sourceApp != null) {
                        Text(
                            text = "来源：$sourceApp",
                            fontSize = 13.sp,
                            color = VigilTextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Confirm button
                    Button(
                        onClick = {
                            onConfirm()
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VigilError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = VigilTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "已知晓，停止报警",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = VigilTextPrimary
                        )
                    }
                }
            }
        }
    }
}
