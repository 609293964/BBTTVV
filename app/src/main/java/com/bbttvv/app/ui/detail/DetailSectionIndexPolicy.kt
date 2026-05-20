package com.bbttvv.app.ui.detail

internal const val DetailHeroSectionIndex = 0
internal const val DetailPagesSectionIndex = 1

internal fun detailRelatedVideosSectionIndex(hasPagesSection: Boolean): Int {
    return if (hasPagesSection) DetailPagesSectionIndex + 1 else DetailPagesSectionIndex
}
