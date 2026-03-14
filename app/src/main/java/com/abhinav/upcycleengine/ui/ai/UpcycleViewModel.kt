package com.abhinav.upcycleengine.ai

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhinav.upcycleengine.domain.UpcycleProject
import com.abhinav.upcycleengine.domain.UpcycleState

// The CORRECTED Firebase AI Logic Imports
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class UpcycleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UpcycleState>(UpcycleState.Idle)
    val uiState: StateFlow<UpcycleState> = _uiState.asStateFlow()

    // 1. Initialize Gemini using the new Firebase AI Logic SDK
    private val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
            systemInstruction = content(role = "system") {
                text("You are an expert sustainability and upcycling assistant. " +
                        "The user will provide an image of waste and a detected material label. " +
                        "Return a JSON object with these exact keys: " +
                        "'title' (String), 'difficulty' (String: Easy/Medium/Hard), " +
                        "'toolsNeeded' (Array of Strings), 'steps' (Array of Strings), 'ecoImpact' (String).")
            }
        )

    fun generateUpcycleIdea(imageBitmap: Bitmap, detectedLabel: String?) {
        _uiState.value = UpcycleState.Loading

        viewModelScope.launch {
            try {
                // 1. Construct the prompt
                val promptText = "The local ML model identified this as: ${detectedLabel ?: "Unknown waste"}. " +
                        "Provide a creative upcycling project for this exact item."

                // 2. Call Gemini with Image + Text
                val response = generativeModel.generateContent(
                    content {
                        image(imageBitmap)
                        text(promptText)
                    }
                )

                // 3. Parse the JSON response
                response.text?.let { jsonString ->
                    val project = parseJsonToProject(jsonString)
                    _uiState.value = UpcycleState.Success(project)
                } ?: run {
                    _uiState.value = UpcycleState.Error("Received empty response from AI.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UpcycleState.Error("Failed to generate idea: ${e.localizedMessage}")
            }
        }
    }

    // A simple manual parser to convert the AI's JSON string into our Kotlin Data Class
    private fun parseJsonToProject(jsonString: String): UpcycleProject {
        val cleanJson = jsonString.removePrefix("```json").removeSuffix("```").trim()
        val jsonObject = JSONObject(cleanJson)

        val toolsArray = jsonObject.getJSONArray("toolsNeeded")
        val toolsList = List(toolsArray.length()) { i -> toolsArray.getString(i) }

        val stepsArray = jsonObject.getJSONArray("steps")
        val stepsList = List(stepsArray.length()) { i -> stepsArray.getString(i) }

        return UpcycleProject(
            title = jsonObject.getString("title"),
            difficulty = jsonObject.getString("difficulty"),
            toolsNeeded = toolsList,
            steps = stepsList,
            ecoImpact = jsonObject.getString("ecoImpact")
        )
    }

    fun resetState() {
        _uiState.value = UpcycleState.Idle
    }
}