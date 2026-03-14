package com.abhinav.upcycleengine.ui

import android.Manifest
import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhinav.upcycleengine.ai.UpcycleViewModel
import com.abhinav.upcycleengine.camera.WasteAnalyzer
import com.abhinav.upcycleengine.domain.DetectedWaste
import com.abhinav.upcycleengine.domain.UpcycleState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(viewModel: UpcycleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var detectedWaste by remember { mutableStateOf<DetectedWaste?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val context = LocalContext.current

    // The missing link: The UI is now officially listening to Gemini's state!
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (cameraPermissionState.status.isGranted) {
        Box(modifier = Modifier.fillMaxSize()) {

            CameraPreview(
                imageCapture = imageCapture,
                onWasteDetected = { waste -> detectedWaste = waste }
            )

            detectedWaste?.let { waste ->
                DrawBoundingBoxOverlay(waste)
            }

            FloatingActionButton(
                onClick = {
                    captureAndProcessImage(imageCapture, context) { bitmap ->
                        val label = detectedWaste?.labels?.firstOrNull() ?: "Unknown material"
                        viewModel.generateUpcycleIdea(bitmap, label)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .size(72.dp),
                shape = CircleShape,
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Capture Waste", modifier = Modifier.size(36.dp))
            }

            // The Result Screen Overlay
            ResultOverlay(
                uiState = uiState,
                onClose = { viewModel.resetState() }
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Grant Camera Permission")
            }
        }
    }
}

@Composable
fun CameraPreview(imageCapture: ImageCapture, onWasteDetected: (DetectedWaste?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx), WasteAnalyzer { onWasteDetected(it) })
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer, imageCapture)
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
fun DrawBoundingBoxOverlay(waste: DetectedWaste) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val rect = waste.boundingBox
        val scaleX = size.width / 480f
        val scaleY = size.height / 640f

        drawRect(
            color = Color(0xFF2E7D32),
            topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
            size = Size(rect.width() * scaleX, rect.height() * scaleY),
            style = Stroke(width = 8f)
        )
    }
    if (waste.labels.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
            Surface(color = Color(0xFF2E7D32).copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp)) {
                Text("Detected: ${waste.labels.first()}", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

private fun captureAndProcessImage(
    imageCapture: ImageCapture,
    context: android.content.Context,
    onBitmapReady: (Bitmap) -> Unit
) {
    Toast.makeText(context, "Capturing image...", Toast.LENGTH_SHORT).show()

    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rawBitmap = image.toBitmap()
                    val maxDim = 800f
                    val scale = kotlin.math.min(maxDim / rawBitmap.width, maxDim / rawBitmap.height)
                    val resizedBitmap = if (scale < 1) {
                        Bitmap.createScaledBitmap(rawBitmap, (rawBitmap.width * scale).toInt(), (rawBitmap.height * scale).toInt(), true)
                    } else rawBitmap

                    onBitmapReady(resizedBitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }
            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}

@Composable
fun ResultOverlay(uiState: UpcycleState, onClose: () -> Unit) {
    when (uiState) {
        is UpcycleState.Idle -> { }
        is UpcycleState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("AI is analyzing materials...", color = Color.White)
                }
            }
        }
        is UpcycleState.Success -> {
            val project = uiState.project
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).padding(24.dp), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(project.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                        }
                        Text("Difficulty: ${project.difficulty}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2E7D32))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("Tools Needed:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            project.toolsNeeded.forEach { Text("• $it", modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Step-by-Step Guide:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            project.steps.forEachIndexed { index, step -> Text("${index + 1}. $step", modifier = Modifier.padding(top = 8.dp)) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), modifier = Modifier.fillMaxWidth()) {
                                Text("🌱 Eco Impact: ${project.ecoImpact}", modifier = Modifier.padding(12.dp), fontStyle = FontStyle.Italic, color = Color(0xFF1B5E20))
                            }
                        }
                    }
                }
            }
        }
        is UpcycleState.Error -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("Oops!") },
                text = { Text(uiState.message) },
                confirmButton = { TextButton(onClick = onClose) { Text("Try Again") } }
            )
        }
    }
}