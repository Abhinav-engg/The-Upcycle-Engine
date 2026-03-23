package com.abhinav.upcycleengine.ai

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhinav.upcycleengine.domain.UpcycleProject
import com.abhinav.upcycleengine.domain.UpcycleState

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

class UpcycleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UpcycleState>(UpcycleState.Idle)
    val uiState: StateFlow<UpcycleState> = _uiState.asStateFlow()
    private var currentBitmap: Bitmap? = null
    private var currentLabel: String? = null

    // 1. Initialize Gemini
    private val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = "gemini-2.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content(role = "system") {
                text("""
                    You are an expert sustainability and upcycling assistant. 
                    The user will provide an image of waste and a detected material label. 
                    Return a JSON array containing exactly 4 distinct ideas. 
                    
                    CRITICAL RULES:
                    1. E-WASTE: If it is an electronic device, at least two ideas MUST focus on safely dismantling it to harvest components (RAM, hard drives, motors). Put safety warnings in step 1.
                    2. DISPOSAL & SORTING: Assess the item's salvageability. One of the 4 ideas MUST always be a guide on "Proper Disposal / Recycling", telling the user exactly how to prep it (e.g., rinsing) and which bin to use (e.g., Green for organic/wet, Blue for dry/recyclable, Red/hazardous).
                    3. DYNAMIC ORDERING: If the item is pure waste/organic (e.g., banana peel, crushed glass), place the "Proper Disposal" idea at the VERY TOP (Index 0) of the JSON array. If the item is highly salvageable (e.g., clean cardboard, laptop), place the "Proper Disposal" idea at the VERY BOTTOM (Index 3) as a last resort.
                    
                    Each object in the array must have these exact keys: 'title' (String), 'difficulty' (String: Easy/Medium/Hard), 'toolsNeeded' (Array of Strings), 'steps' (Array of Strings), 'ecoImpact' (String).
                """.trimIndent())
            }
        )

    fun generateUpcycleIdea(imageBitmap: Bitmap, detectedLabel: String?) {
        currentBitmap = imageBitmap
        currentLabel = detectedLabel
        _uiState.value = UpcycleState.Loading

        viewModelScope.launch {
            try {
                //  prompt for multiple ideas
                val promptText = "The local ML model identified this as: ${detectedLabel ?: "Unknown waste"}. " +
                        "Provide exactly 4 distinct, creative upcycling project ideas for this exact item. " +
                        "Vary the difficulty (e.g., 1 Easy, 2 Medium, 1 Hard)."

                // call Gemini
                val response = generativeModel.generateContent(
                    content {
                        image(imageBitmap)
                        text(promptText)
                    }
                )

                // parse the JSON array response
                response.text?.let { jsonString ->
                    val projectsList = parseJsonToProjectList(jsonString)
                    _uiState.value = UpcycleState.Success(projectsList)
                } ?: run {
                    _uiState.value = UpcycleState.Error("Received empty response from AI.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UpcycleState.Error("Failed to generate ideas: ${e.localizedMessage}")
            }
        }
    }

    // UPDATED: Parses a JSON Array and returns a List of UpcycleProjects
    private fun parseJsonToProjectList(jsonString: String): List<UpcycleProject> {
        val cleanJson = jsonString.removePrefix("```json").removeSuffix("```").trim()
        val jsonArray = JSONArray(cleanJson)
        val projects = mutableListOf<UpcycleProject>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)

            val toolsArray = jsonObject.getJSONArray("toolsNeeded")
            val toolsList = List(toolsArray.length()) { j -> toolsArray.getString(j) }

            val stepsArray = jsonObject.getJSONArray("steps")
            val stepsList = List(stepsArray.length()) { j -> stepsArray.getString(j) }

            projects.add(
                UpcycleProject(
                    title = jsonObject.getString("title"),
                    difficulty = jsonObject.getString("difficulty"),
                    toolsNeeded = toolsList,
                    steps = stepsList,
                    ecoImpact = jsonObject.getString("ecoImpact")
                )
            )
        }
        return projects
    }

    fun resetState() {
        _uiState.value = UpcycleState.Idle
    }
    // 🌟 NEW: The function the UI calls when you hit the refresh button
    fun rerollIdeas() {
        currentBitmap?.let { savedBitmap ->
            // Pass a slightly different prompt to force new ideas
            _uiState.value = UpcycleState.Loading
            viewModelScope.launch {
                try {
                    val promptText = "The local ML model identified this as: ${currentLabel ?: "Unknown waste"}. " +
                            "Provide exactly 4 BRAND NEW, highly unique upcycling project ideas for this item. " +
                            "Do not repeat standard ideas. Vary the difficulty."

                    val response = generativeModel.generateContent(
                        com.google.firebase.ai.type.content {
                            image(savedBitmap)
                            text(promptText)
                        }
                    )

                    response.text?.let { jsonString ->
                        val projectsList = parseJsonToProjectList(jsonString)
                        _uiState.value = UpcycleState.Success(projectsList)
                    } ?: run {
                        _uiState.value = UpcycleState.Error("Received empty response from AI.")
                    }
                } catch (e: Exception) {
                    _uiState.value = UpcycleState.Error("Failed to regenerate ideas: ${e.localizedMessage}")
                }
            }
        }
    }
}