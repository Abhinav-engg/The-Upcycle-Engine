package com.abhinav.upcycleengine.domain

// will force Gemini to return
data class UpcycleProject(
    val title: String,
    val difficulty: String,
    val toolsNeeded: List<String>,
    val steps: List<String>,
    val ecoImpact: String
)

// The UI States for screen
sealed class UpcycleState {
    object Idle : UpcycleState()
    object Loading : UpcycleState() // When Gemini is thinking
    data class Success(val projects: List<UpcycleProject>) : UpcycleState()
    data class Error(val message: String) : UpcycleState()
}