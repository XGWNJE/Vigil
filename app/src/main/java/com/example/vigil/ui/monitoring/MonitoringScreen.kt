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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.MainActivity
import com.example.vigil.PermissionUtils
import com.example.vigil.R
import com.example.vigil.ui.theme.VigilTheme
import com.example.vigil.ui.theme.Initializing
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val serviceEnabled by viewModel.serviceEnabled
    val serviceStatusText by viewModel.serviceStatusText
    val showRestartButton by viewModel.showRestartButton
    
    // 创建开关动画效果
    val switchScale = remember { Animatable(1f) }
    LaunchedEffect(serviceEnabled) {
        // 当状态改变时执行动画
        switchScale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(100)
        )
        switchScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    // 创建服务状态的持续性动画
    val infiniteTransition = rememberInfiniteTransition()
    
    // 呼吸效果 - 透明度动画
    val alphaAnimation = infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // 脉冲效果 - 大小动画
    val pulseAnimation = infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // 雷达扫描动画
    val radarSweepAngle = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // 波纹动画 - 多个波纹
    val wave1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 0, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val wave2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 650, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val wave3 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1300, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // 图标旋转动画
    val iconRotation = infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // 初始化状态加载动画
    val initLoadingRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // 获取字符串资源，避免在Canvas中调用
    val runningStatusText = stringResource(R.string.service_running)
    val stoppedStatusText = stringResource(R.string.service_stopped)
    val recoveringStatusText = stringResource(R.string.service_status_recovering)
    val initializingStatusText = stringResource(R.string.service_initializing)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // 服务状态指示器 - 确保纯圆形设计
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 创建纯圆形背景 - 使用多层圆形实现柔和效果
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        // 最外层圆形背景 - 非常淡的阴影效果
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                            shape = CircleShape
                        )
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 中间层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = when (serviceStatusText) {
                                        runningStatusText -> listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                                        )
                                        stoppedStatusText -> listOf(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.02f)
                                        )
                                        initializingStatusText -> listOf(
                                            Initializing.copy(alpha = 0.12f),
                                            Initializing.copy(alpha = 0.08f),
                                            Initializing.copy(alpha = 0.02f)
                                        )
                                        else -> listOf(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.02f)
                                        )
                                    }
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // 内层圆形，动画效果区域
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(pulseAnimation.value)
                                .padding(16.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // 自定义动画效果
                            if (serviceEnabled) {
                                // 针对不同状态显示不同动画
                                if (serviceStatusText == initializingStatusText) {
                                    // 初始化状态显示加载动画
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val canvasWidth = size.width
                                        val canvasHeight = size.height
                                        val center = Offset(canvasWidth / 2, canvasHeight / 2)
                                        val radius = min(canvasWidth, canvasHeight) / 2 * 0.8f
                                        
                                        // 绘制旋转的加载指示器
                                        val angle = initLoadingRotation.value * PI.toFloat() / 180f
                                        for (i in 0 until 12) {
                                            val rotateAngle = angle + (i * (2 * PI.toFloat() / 12))
                                            val startRadius = radius * 0.7f
                                            val endRadius = radius * 0.85f
                                            val alpha = 0.1f + (i % 12) * 0.075f
                                            
                                            val startPoint = Offset(
                                                x = center.x + startRadius * cos(rotateAngle),
                                                y = center.y + startRadius * sin(rotateAngle)
                                            )
                                            
                                            val endPoint = Offset(
                                                x = center.x + endRadius * cos(rotateAngle),
                                                y = center.y + endRadius * sin(rotateAngle)
                                            )
                                            
                                            drawLine(
                                                color = Initializing.copy(alpha = alpha),
                                                start = startPoint,
                                                end = endPoint,
                                                strokeWidth = 3f,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                } else {
                                    // 波纹和扫描动画（运行中或异常状态）
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val canvasWidth = size.width
                                        val canvasHeight = size.height
                                        val center = Offset(canvasWidth / 2, canvasHeight / 2)
                                        val radius = min(canvasWidth, canvasHeight) / 2
                                        
                                        // 绘制雷达扫描线
                                        val sweepAngleRadians = radarSweepAngle.value * PI.toFloat() / 180f
                                        val scanLineEnd = Offset(
                                            x = center.x + radius * 0.85f * cos(sweepAngleRadians),
                                            y = center.y + radius * 0.85f * sin(sweepAngleRadians)
                                        )
                                        
                                        drawLine(
                                            color = if (serviceStatusText == runningStatusText) 
                                                    Color(0xFF3F51B5).copy(alpha = 0.5f)
                                                else Color.Gray.copy(alpha = 0.3f),
                                            start = center,
                                            end = scanLineEnd,
                                            strokeWidth = 1.2f,
                                            cap = StrokeCap.Round
                                        )
                                        
                                        // 波纹效果
                                        val waveValues = listOf(wave1.value, wave2.value, wave3.value)
                                        waveValues.forEach { waveValue ->
                                            if (waveValue < 1) {
                                                val alpha = (1 - waveValue) * (1 - waveValue) * 0.2f
                                                val waveRadius = waveValue * radius * 0.9f
                                                
                                                drawCircle(
                                                    color = if (serviceStatusText == runningStatusText)
                                                            Color(0xFF3F51B5).copy(alpha = alpha)
                                                        else Color.Gray.copy(alpha = alpha * 0.7f),
                                                    radius = waveRadius,
                                                    center = center,
                                                    style = Stroke(
                                                        width = 0.8f,
                                                        pathEffect = PathEffect.cornerPathEffect(radius)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 中心内容
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(0.8f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // 服务图标
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_notification_icon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .rotate(iconRotation.value),
                                    tint = when (serviceStatusText) {
                                        runningStatusText -> MaterialTheme.colorScheme.primary
                                        stoppedStatusText -> MaterialTheme.colorScheme.onSurfaceVariant
                                        initializingStatusText -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // 服务状态文字
                                Text(
                                    text = serviceStatusText,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1, // 限制为单行
                                    overflow = TextOverflow.Ellipsis, // 超出部分显示省略号
                                    color = when (serviceStatusText) {
                                        runningStatusText -> MaterialTheme.colorScheme.primary
                                        stoppedStatusText -> MaterialTheme.colorScheme.onSurfaceVariant
                                        recoveringStatusText -> MaterialTheme.colorScheme.tertiary
                                        initializingStatusText -> Initializing
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // 通知开关标题
            Text(
                text = stringResource(R.string.enable_service_switch),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when (serviceEnabled) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            // 自定义大型开关 - 直接放在布局中，没有背景框
            Box(
                modifier = Modifier
                    .scale(switchScale.value) // 添加动画缩放效果
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(40.dp)
                    )
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        if (serviceEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )
                    .size(width = 120.dp, height = 60.dp)
                    .clickable(enabled = true, onClick = {
                        val isLicensed = true // TODO: 从 ViewModel 获取实际授权状态
                        viewModel.onServiceEnabledChange(
                            !serviceEnabled,
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
                    }),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 开关圆形滑块
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .offset(x = if (serviceEnabled) 30.dp else (-30).dp)
                            .shadow(elevation = 4.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(
                                if (serviceEnabled) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    Color.White
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 开关内部的指示符
                        Text(
                            text = if (serviceEnabled) "ON" else "OFF",
                            color = if (serviceEnabled) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 重启按钮 (仅在需要时显示)
            if (showRestartButton) {
                Button(
                    onClick = {
                        viewModel.onRestartServiceClick {
                            if (activity is MainActivity) {
                                activity.restartService()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.restart_service_button),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
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
