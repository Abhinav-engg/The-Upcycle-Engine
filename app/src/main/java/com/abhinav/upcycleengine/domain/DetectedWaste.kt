package com.abhinav.upcycleengine.domain

import android.graphics.Rect

// This represents the data our UI needs to draw the green box
data class DetectedWaste(
    val boundingBox: Rect,
    val trackingId: Int? = null,
    val labels: List<String> = emptyList()
)