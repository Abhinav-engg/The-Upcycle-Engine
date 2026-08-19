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

    
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification() 
        .build()

    private val objectDetector = ObjectDetection.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    if (detectedObjects.isNotEmpty()) {
                        
                        val topObject = detectedObjects.first()

                        val labels = topObject.labels.map { it.text }

                        
                        onObjectDetected(
                            DetectedWaste(
                                boundingBox = topObject.boundingBox,
                                trackingId = topObject.trackingId,
                                labels = labels
                            )
                        )
                    } else {
                        onObjectDetected(null) 
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
                .addOnCompleteListener {
                    
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}