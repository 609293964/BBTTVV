package com.bbttvv.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CategoryChipRow(
    categories: List<String>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onCategoryFocused: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    HomeSecondaryTabRow(
        tabs = categories,
        selectedIndex = selectedIndex,
        onTabSelected = onCategorySelected,
        onSelectedTabConfirmed = onCategorySelected,
        onTabFocused = onCategoryFocused,
        modifier = modifier
    )
}
