package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView

internal object HomeRecyclerViewTypes {
    const val VideoCard = 1001
}

internal class HomeRecyclerPools {
    val videoCardPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(HomeRecyclerViewTypes.VideoCard, 24)
    }
}
