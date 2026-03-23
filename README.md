# ♻️ The Upcycle Engine

**An AI-powered Android assistant that turns everyday waste into creative DIY projects using edge ML and generative AI.**

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()

## 💡 About The Project
The Upcycle Engine is a mobile application designed to promote sustainability and circular economy practices. By simply pointing a phone camera at an item of waste (like a plastic bottle or cardboard box), the app uses on-device machine learning to recognize the object and cloud-based generative AI to instantly provide a step-by-step upcycling guide, complete with tools needed and the project's eco-impact.

### ✨ Key Features
* **Real-time Object Detection:** Uses Google ML Kit to draw live bounding boxes around recyclable materials right in the camera viewfinder.
* **Generative Upcycling Ideas:** Powered by the **Gemini 2.5 Flash-Lite** model via Firebase AI Logic to generate fast, structured JSON instructions.
* **Modern UI:** Built entirely with Jetpack Compose for a smooth, reactive, and beautiful user experience.

## 🏗️ Architecture & Tech Stack
This project follows **Clean Architecture** principles and the **MVI (Model-View-Intent)** pattern for predictable state management.

* **UI Layer:** Jetpack Compose, Material Design 3
* **Camera Vision:** Android CameraX, Google ML Kit (Object Detection & Tracking)
* **AI Backend:** Firebase Vertex AI (Gemini Developer API)
* **Concurrency:** Kotlin Coroutines & Flows
* **Architecture:** ViewModels, StateFlow, Reactive UIs

### Project Structure
* `camera/` - Edge ML processing and live frame analysis.
* `domain/` - Core data models (`DetectedWaste`, `UpcycleProject`).
* `ui/` - Jetpack Compose screens and MVI state management (`UpcycleViewModel`).

