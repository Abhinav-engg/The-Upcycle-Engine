package com.abhinav.upcycleengine.ui

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhinav.upcycleengine.ai.UpcycleViewModel
import com.abhinav.upcycleengine.camera.WasteAnalyzer
import com.abhinav.upcycleengine.domain.DetectedWaste
import com.abhinav.upcycleengine.domain.UpcycleProject
import com.abhinav.upcycleengine.domain.UpcycleState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState


@Composable
fun UpcycleAppRouter(viewModel: UpcycleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var showCamera by remember { mutableStateOf(false) }

    if (!showCamera) {
        LandingScreen(
            onOpenCamera = {
                
                showCamera = true
            }
        )
    } else {
        ScannerScreen(
            viewModel = viewModel,
            onNavigateBack = { showCamera = false }
        )
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(viewModel: UpcycleViewModel, onNavigateBack: () -> Unit) { 
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var detectedWaste by remember { mutableStateOf<DetectedWaste?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val context = LocalContext.current

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

            
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopEnd) 
                    .padding(top = 48.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, contentDescription = "Go Back", tint = Color.White)
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

            
            ResultOverlay(
                uiState = uiState,
                onClose = { viewModel.resetState() },
                onReroll = { viewModel.rerollIdeas() }
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
                    val maxDim = 400f 
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
fun ResultOverlay(uiState: UpcycleState, onClose: () -> Unit, onReroll: () -> Unit) {
    val haptics = LocalHapticFeedback.current

    when (uiState) {
        is UpcycleState.Idle -> {  }

        is UpcycleState.Loading -> {
            val facts = listOf(
                "Did you know? Recycling one aluminum can saves enough energy to run a TV for 3 hours.",
                "Did you know? Glass takes over 4,000 years to decompose in a landfill.",
                "Did you know? Only 9% of all plastic waste ever produced has been recycled.",
                "Did you know? Cardboard can be recycled up to 7 times before the fibers break down.",
                "Did you know? Upcycling reduces CO2 emissions by extending the lifespan of materials."
            )

            var currentFactIndex by remember { mutableIntStateOf(0) }
            var displayedText by remember { mutableStateOf("") }

            LaunchedEffect(currentFactIndex) {
                val fullText = facts[currentFactIndex]
                displayedText = ""
                for (i in fullText.indices) {
                    displayedText += fullText[i]
                    kotlinx.coroutines.delay(40)
                }
                kotlinx.coroutines.delay(2000)
                currentFactIndex = (currentFactIndex + 1) % facts.size
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = displayedText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TextButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text("Cancel", color = Color.Gray, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        is UpcycleState.Success -> {
            LaunchedEffect(Unit) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = Color(0xFF2E7D32),
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Select a Project",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row {
                                IconButton(onClick = onReroll) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Regenerate Ideas", tint = Color.White)
                                }
                                IconButton(onClick = onClose) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.projects) { project ->
                            ExpandableProjectCard(project)
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
                confirmButton = {
                    TextButton(onClick = onClose) { Text("Try Again") }
                }
            )
        }
    }
}

@Composable
fun ExpandableProjectCard(project: UpcycleProject) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val difficultyColor = when(project.difficulty.lowercase()) {
                        "easy" -> Color(0xFF388E3C)
                        "medium" -> Color(0xFFF57C00)
                        "hard" -> Color(0xFFD32F2F)
                        else -> Color(0xFF2E7D32)
                    }
                    Text(
                        text = "Difficulty: ${project.difficulty}",
                        style = MaterialTheme.typography.labelMedium,
                        color = difficultyColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = {
                        val stepString = project.steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n")
                        val shareText = """
                            ♻️ Check out this upcycling idea!
                            
                            *${project.title}* (${project.difficulty})
                            
                            🛠️ Tools: ${project.toolsNeeded.joinToString(", ")}
                            
                            📝 Steps:
                            $stepString
                            
                            🌱 Impact: ${project.ecoImpact}
                            
                            Generated by the Upcycle Engine!
                        """.trimIndent()

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Project Idea")
                        context.startActivity(shareIntent)
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Project", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("🛠️ Tools Needed:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(project.toolsNeeded.joinToString(", "), modifier = Modifier.padding(bottom = 12.dp, start = 8.dp))

                Text("📝 Step-by-Step:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                project.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🌱 Impact: ${project.ecoImpact}",
                        modifier = Modifier.padding(12.dp),
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF1B5E20)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap to view instructions...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}


@Composable
fun LandingScreen(onOpenCamera: () -> Unit) {
    val darkBg = Color(0xFF101611)
    val cardBg = Color(0xFF1A261D)
    val accentGreen = Color(0xFF7CB342)
    val whiteCard = Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accentGreen, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Recycling, contentDescription = "Logo", tint = darkBg)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("UPCYCLE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 1.sp)
                    Text("ENGINE", color = accentGreen, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(cardBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
            }
        }

        
        val facts = listOf(
            Pair("91%", "of all plastic ever produced has never been recycled — it ends up in landfills or oceans instead."),
            Pair("500", "years is how long it takes for a standard plastic toothbrush to decompose in a landfill."),
            Pair("7x", "is the number of times cardboard can be recycled before its fibers become too short to use.")
        )

        var currentFactIndex by remember { mutableIntStateOf(0) }

        
        LaunchedEffect(currentFactIndex) {
            kotlinx.coroutines.delay(5000)
            currentFactIndex = (currentFactIndex + 1) % facts.size
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .animateContentSize(), 
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(accentGreen, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RECYCLING FACT", color = accentGreen, fontSize = 12.sp, letterSpacing = 1.sp)
                }

                Text(
                    text = facts[currentFactIndex].first, 
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontStyle = FontStyle.Italic
                )

                Text(
                    text = facts[currentFactIndex].second, 
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        facts.indices.forEach { index ->
                            val isSelected = index == currentFactIndex
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (isSelected) 20.dp else 6.dp)
                                    .background(
                                        if (isSelected) accentGreen else Color.Gray.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                    
                                    .clickable { currentFactIndex = index }
                            )
                        }
                    }
                    
                    Text(
                        text = "next fact >",
                        color = accentGreen.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            currentFactIndex = (currentFactIndex + 1) % facts.size
                        }
                    )
                }
            }
        }

        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .clickable { onOpenCamera() },
            colors = CardDefaults.cardColors(containerColor = whiteCard),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Scan an item", color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Point your camera at any waste object", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(accentGreen.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(accentGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Open Camera", tint = darkBg, modifier = Modifier.size(36.dp))
                    }
                }

                Text("TAP TO OPEN CAMERA", color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 32.dp))
            }
        }

        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.StarBorder, value = "247", label = "SCANNED")
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Recycling, value = "12kg", label = "DIVERTED")
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.LocationOn, value = "89%", label = "ACCURACY")
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A261D)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF7CB342), modifier = Modifier.size(24.dp))
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}