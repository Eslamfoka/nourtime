package com.nourtime.app.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * Text field colors: a focused field is marked in the text color. Material's default is the gold
 * primary, which is too faint on the cream background (1.5:1) for the label and the border.
 */
@Composable
fun nourTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.onSurface,
)
