// src/main/java/com/example/vigil/ui/theme/Color.kt
package com.example.vigil.ui.theme

import androidx.compose.ui.graphics.Color

// Shared design tokens follow the current Material theme.
val VigilBackground: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.background
val VigilTextPrimary: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
val VigilTextSecondary: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
val VigilBorder: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.outline
val VigilPrimary: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
val VigilDivider: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
val VigilError: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.error

val VigilSurface: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.surface
