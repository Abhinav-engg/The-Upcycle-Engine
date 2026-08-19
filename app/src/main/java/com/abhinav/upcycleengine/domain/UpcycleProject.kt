package com.abhinav.upcycleengine.domain


data class UpcycleProject(
    val title: String,
    val difficulty: String,
    val toolsNeeded: List<String>,
    val steps: List<String>,
    val ecoImpact: String
)


sealed class UpcycleState {
    object Idle : UpcycleState()
    object Loading : UpcycleState() 
    data class Success(val projects: List<UpcycleProject>) : UpcycleState()
    data class Error(val message: String) : UpcycleState()
}