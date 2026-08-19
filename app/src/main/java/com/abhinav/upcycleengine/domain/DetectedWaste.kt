package com.abhinav.upcycleengine.domain

import android.graphics.Rect


data class DetectedWaste(
    val boundingBox: Rect,
    val trackingId: Int? = null,
    val labels: List<String> = emptyList()
)