package com.abhinav.upcycleengine.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.abhinav.upcycleengine.domain.DetectedWaste
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class WasteAnalyzer(
    private val onObjectDetected: (DetectedWaste?) -> Unit
) : ImageAnalysis.Analyzer {

    // 1. Configure ML Kit to prioritize the most prominent object in the frame
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification() // Tries to label it (e.g., "Fashion good", "Food")
        .build()

    private val objectDetector = ObjectDetection.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // 2. Convert CameraX frame to ML Kit format
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // 3. Process the frame
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    if (detectedObjects.isNotEmpty()) {
                        // Grab the most prominent object
                        val topObject = detectedObjects.first()

                        val labels = topObject.labels.map { it.text }

                        // 4. Send the result back to the UI
                        onObjectDetected(
                            DetectedWaste(
                                boundingBox = topObject.boundingBox,
                                trackingId = topObject.trackingId,
                                labels = labels
                            )
                        )
                    } else {
                        onObjectDetected(null) // Nothing detected
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
                .addOnCompleteListener {
                    // 5. VERY IMPORTANT: Close the frame so CameraX can send the next one!
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}