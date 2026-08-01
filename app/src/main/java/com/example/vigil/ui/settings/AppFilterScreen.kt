package com.example.vigil.ui.settings

import android.app.Application
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vigil.ui.theme.VigilDirAAcid
import com.example.vigil.ui.theme.VigilDirABg
import com.example.vigil.ui.theme.VigilDirADim
import com.example.vigil.ui.theme.VigilDirAFaint
import com.example.vigil.ui.theme.VigilDirAInk
import com.example.vigil.ui.theme.VigilDirALine

/**
 * 「A · 一线」应用过滤页：与主屏同一语言——暗底、发丝线分区、等宽元信息、酸橙绿强调。
 * ViewModel 接口不变，仅重写 UI。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppFilterScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val isAppFilterEnabled by viewModel.isAppFilterEnabled
    val isLoadingApps by viewModel.isLoadingApps
    val searchQuery by viewModel.searchQuery
    val filteredApps = viewModel.getFilteredApps()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VigilDirABg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)
    ) {
        // ---- 顶部：返回 + 标题 + 刷新 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 返回：48dp 触控区，字形左对齐页面内容
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 48.dp)
                        .clickable(onClick = onNavigateBack),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "←",
                        fontSize = 18.sp,
                        color = VigilDirADim
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "APP FILTER // 应用过滤",
                    style = MonoTextStyle,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = VigilDirAInk
                )
            }
            Text(
                text = "REFRESH",
                style = MonoTextStyle,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = VigilDirADim,
                modifier = Modifier
                    .clickable { viewModel.loadInstalledApps() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // ---- 开关行 + 统计 ----
        Column(modifier = Modifier.padding(top = 28.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VigilDirALine)
            )
            LineRow {
                RowLabel("仅监听指定应用")
                MiniSwitch(
                    checked = isAppFilterEnabled,
                    onToggle = { viewModel.onAppFilterEnabledChange(it) }
                )
            }
            Text(
                text = "${filteredApps.size} APPS · ${filteredApps.count { it.isSelected }} SELECTED",
                style = MonoTextStyle,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = VigilDirADim,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // ---- 搜索框 ----
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = VigilDirAInk),
            placeholder = {
                Text(text = "搜索应用名或包名", fontSize = 13.sp, color = VigilDirADim)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "×",
                        fontSize = 15.sp,
                        color = VigilDirADim,
                        modifier = Modifier
                            .clickable { viewModel.onSearchQueryChange("") }
                            .padding(horizontal = 12.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VigilDirAAcid,
                unfocusedBorderColor = VigilDirAFaint,
                focusedTextColor = VigilDirAInk,
                unfocusedTextColor = VigilDirAInk,
                cursorColor = VigilDirAAcid
            ),
            shape = RoundedCornerShape(4.dp)
        )

        // ---- 应用列表 ----
        when {
            isLoadingApps -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LOADING…",
                        style = MonoTextStyle,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = VigilDirADim
                    )
                }
            }
            filteredApps.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO APPS FOUND",
                        style = MonoTextStyle,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = VigilDirADim
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppFilterRow(
                            app = app,
                            onToggle = { viewModel.toggleAppSelection(app.packageName) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }
}

private val MonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace)

/** 单行：左侧灰标签 + 右侧内容，底部 1dp 发丝线（与主屏 LineRow 同语言） */
@Composable
private fun LineRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    VigilDirALine,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            }
            .padding(horizontal = 2.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        color = VigilDirADim
    )
}

/** 主屏大开关的缩小版：描边胶囊 + 实心圆点（位置/颜色随状态平滑过渡） */
@Composable
private fun MiniSwitch(checked: Boolean, onToggle: (Boolean) -> Unit) {
    val transition = updateTransition(targetState = checked, label = "miniSwitch")
    val dotColor by transition.animateColor(label = "dotColor") { on ->
        if (on) VigilDirAAcid else VigilDirAFaint
    }
    // 圆点中心偏移：0f=左，1f=右（胶囊内径 44-3*2=38dp，圆点 16dp，行程 22dp）
    val dotOffset by transition.animateFloat(label = "dotOffset") { on -> if (on) 1f else 0f }
    Box(
        modifier = Modifier
            .size(44.dp, 24.dp)
            .border(width = 1.dp, color = dotColor, shape = CircleShape)
            .clip(CircleShape)
            .clickable { onToggle(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (22.dp * dotOffset))
                .size(16.dp)
                .background(dotColor, CircleShape)
        )
    }
}

/** 自定义勾选框：描边小方块，选中酸橙实心 + 对勾（颜色淡入、对勾缩放弹入） */
@Composable
private fun AppCheck(checked: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (checked) VigilDirAAcid else Color.Transparent,
        animationSpec = tween(180),
        label = "checkBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) VigilDirAAcid else VigilDirAFaint,
        animationSpec = tween(180),
        label = "checkBorder"
    )
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(bgColor, RoundedCornerShape(2.dp))
            .border(1.dp, borderColor, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = checked,
            enter = scaleIn(tween(180)) + fadeIn(tween(180)),
            exit = scaleOut(tween(120)) + fadeOut(tween(120))
        ) {
            Text(
                text = "✓",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VigilDirABg
            )
        }
    }
}

/** 应用行：图标 + 名称（含 SYS 标签）+ 包名，右侧勾选框，底部发丝线 */
@Composable
private fun AppFilterRow(
    app: AppInfo,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    VigilDirALine,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            }
            .clickable(onClick = onToggle)
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        } else {
            Box(modifier = Modifier.size(36.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.appName,
                    fontSize = 13.sp,
                    color = VigilDirAInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isSystemApp) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SYS",
                        style = MonoTextStyle,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        color = VigilDirADim,
                        modifier = Modifier
                            .border(1.dp, VigilDirAFaint, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = app.packageName,
                style = MonoTextStyle,
                fontSize = 9.sp,
                color = VigilDirADim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        AppCheck(checked = app.isSelected)
    }
}
