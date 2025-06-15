package com.fingerprintrecog.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Camera
import android.view.MotionEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    private var fingerprintModel: Interpreter? = null
    private var showError by mutableStateOf(false)
    private var errorMessage by mutableStateOf("")
    private val embeddingFileName = "fingerprint_embeddings.dat"
    private var registrationCount = 0
    private var matchedFingerprintId = -1
    private val DISTANCE_THRESHOLD = 0.5f  // Threshold for fingerprint matching

    @Composable
    fun MainScreen() {
        var result by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var selectedFingerprintIndex by remember { mutableStateOf<Int?>(null) }
        var currentMode by remember { mutableStateOf<Mode?>(null) }
        var showError by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
        var processedImage by remember { mutableStateOf<Bitmap?>(null) }
        var showAboutDialog by remember { mutableStateOf(false) }
        var showHelpDialog by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val fingerprints = remember { mutableStateListOf<String>() }
        val context = LocalContext.current

        // Load fingerprints when the screen is first displayed
        LaunchedEffect(Unit) {
            loadFingerprints(context, fingerprints)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Fingerprint Recognition",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Divider()
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = { Text("About") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                showAboutDialog = true
                            }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Help, contentDescription = null) },
                        label = { Text("Help") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                showHelpDialog = true
                            }
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Divider()
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Watermark fingerprint image - adjusted position
                Image(
                    painter = painterResource(id = R.drawable.ic_thumbprint),
                    contentDescription = "Fingerprint Watermark",
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.Center)
                        .offset(y = (0).dp)
                        .alpha(0.1f)
                )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top bar with title and menu
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fingerprint Recognition",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    }

            // Mode selection buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        currentMode = Mode.Register
                        result = ""
                        capturedImage = null
                        processedImage = null
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("Register")
                }
                Button(
                    onClick = { 
                        currentMode = Mode.Verify
                        result = ""
                        capturedImage = null
                        processedImage = null
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text("Verify")
                }
                Button(
                    onClick = { 
                        currentMode = Mode.Manage
                        result = ""
                        capturedImage = null
                        processedImage = null
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text("Manage")
                }
            }

            // Display captured and processed images
            if (capturedImage != null || processedImage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Captured Images",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            capturedImage?.let { bitmap ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "Original",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Captured Image",
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                            
                            processedImage?.let { bitmap ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "Processed",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Processed Image",
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

                    // Display result in card format
                    if (result.isNotEmpty() && (currentMode == null || isProcessing)) {
                Card(
                        modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                                    if (currentMode == Mode.Register) "Registration Successful" else "Result",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            result,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentMode) {
                            Mode.Manage -> {
                                if (fingerprints.isEmpty()) {
                            Text(
                                "No fingerprints registered",
                                modifier = Modifier.align(Alignment.Center)
                            )
                                } else {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                        fingerprints.forEachIndexed { index, name ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                        Text(
                                            "Fingerprint ${index + 1}",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                                Button(
                                                    onClick = {
                                                        selectedFingerprintIndex = index
                                                        showDeleteDialog = true
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error
                                                    )
                                                ) {
                                                    Text("Delete")
                                                }
                                            }
                                        }
                                    }
                                }
                                Button(
                            onClick = { currentMode = null },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(top = 16.dp)
                                ) {
                                    Text("Back")
                                }
                            }
                            Mode.Register, Mode.Verify -> {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            CameraCaptureWindow(
                                        onImageCaptured = { rotatedBitmap ->
                                            // Set processing state and original image immediately
                                    isProcessing = true
                                            capturedImage = rotatedBitmap

                                            // Launch processing in coroutine
                                            scope.launch {
                                                try {
                                                    // Process image in background
                                                    val processed = withContext(Dispatchers.Default) {
                                                        preprocessWithOpenCV(rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true))
                                                    }

                                                    // Update UI and run model on main thread
                                                    withContext(Dispatchers.Main) {
                                        processedImage = processed
                                        Log.d("Registration", "Image preprocessed: ${processed.width}x${processed.height}")
                                        
                                                        try {
                                        val embedding = runModel(processed)
                                        Log.d("Registration", "Model executed successfully")
                                        
                                        if (currentMode == Mode.Register) {
                                            Log.d("Registration", "Mode: Register")
                                                saveEmbedding(context, embedding)
                                            result = "Fingerprint registered successfully! (ID: $registrationCount)"
                                            loadFingerprints(context, fingerprints)
                                            Log.d("Registration", "Registration completed successfully")
                                        } else {
                                            Log.d("Registration", "Mode: Verify")
                                            verifyEmbedding(context, embedding) { newResult ->
                                                result = newResult
                                        }
                                            Log.d("Registration", "Verification completed")
                                    }
                                    } catch (e: Exception) {
                                                            Log.e("Registration", "Model execution failed", e)
                                                            errorMessage = "Processing failed: ${e.localizedMessage}"
                                                            showError = true
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                        Log.e("Registration", "Processing failed", e)
                                        errorMessage = "Processing failed: ${e.localizedMessage}"
                                        showError = true
                                                    }
                                    } finally {
                                                    withContext(Dispatchers.Main) {
                                    isProcessing = false
                                        currentMode = null
                                                    }
                                                }
                                    }
                                },
                                onCancel = { 
                                    Log.d("Registration", "Registration cancelled")
                                    currentMode = null 
                                },
                                enableFlash = true,
                                enableAutoFocus = true
                            )
                        }
                        }
                            null -> {
                                if (result.isEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                    Text(
                                                text = "Choose an action",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            Text(
                                                text = "Register, Verify, or Manage fingerprints",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Footer text
                    Text(
                        text = "Built with TensorFlow Lite and OpenCV",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 16.dp)
                                    )
                            }
                        }
                    }

            // Show error dialog if needed
            if (showError) {
                ErrorDialog(
                    message = errorMessage,
                    onDismiss = { showError = false }
                )
        }

        // Show delete confirmation dialog
        if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Delete Fingerprint") },
                            text = { Text("Are you sure you want to delete this fingerprint?") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                            selectedFingerprintIndex?.let { index ->
                            deleteFingerprint(context, index, fingerprints)
                                loadFingerprints(context, fingerprints)
                            }
                                        showDeleteDialog = false
                                    }
                                ) {
                        Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
        }

        // About Dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("About") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Fingerprint Recognition",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "An efficient fingerprint recognition system using TensorFlow Lite and OpenCV. " +
                            "Built on a FaceNet-inspired architecture with contrastive loss, it enables reliable " + 
                            "fingerprint registration, verification, and management directly on the device. ",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Key Features",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "• Register and verify fingerprints securely\n" +
                            "• Manage registered fingerprints\n" +
                            "• High-quality camera capture with flash and autofocus\n" +
                            "• Advanced image processing with OpenCV\n" +
                            "• Modern UI built with Jetpack Compose\n" +
                            "• Local storage for enhanced privacy",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Technical Details",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "• Model: FaceNet architecture with Contrastive loss\n" +
                            "• Input: 96x96 grayscale images\n" +
                            "• Output: 128-dimensional embeddings\n" +
                            "• Training: SOCOFing dataset\n" +
                            "• Accuracy: 98.34% (Training), 98.29% (Testing)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Version 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Help Dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("Help") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Getting Started",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "The app offers three main functions: Register, Verify, and Manage fingerprints. " +
                            "Each function is designed to work with your device's camera to capture and process fingerprint images.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Registration Process",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "1. Tap the 'Register' button\n" +
                            "2. Position your finger within the camera frame\n" +
                            "3. Tap on your finger to focus - hold steady after focusing\n" +
                            "4. Ensure your finger is centered and clearly visible\n" +
                            "5. Hold steady and tap 'Capture'\n" +
                            "6. Wait for the processing to complete\n" +
                            "7. You'll receive a confirmation with your fingerprint ID",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Verification Process",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "1. Tap the 'Verify' button\n" +
                            "2. Place your finger in the same position as during registration\n" +
                            "3. Tap on your finger to focus - hold steady after focusing\n" +
                            "4. Hold steady and tap 'Capture'\n" +
                            "5. The system will compare your fingerprint with registered ones\n" +
                            "6. You'll see a detailed match report showing similarity scores",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Managing Fingerprints",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "1. Tap the 'Manage' button to view all registered fingerprints\n" +
                            "2. Each fingerprint is listed with its ID\n" +
                            "3. Use the 'Delete' button to remove unwanted fingerprints\n" +
                            "4. Confirm deletion in the popup dialog",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Camera Tips",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "• Ensure good lighting conditions\n" +
                            "• Keep your finger clean and dry\n" +
                            "• Position your finger in the center of the frame\n" +
                            "• Tap on your finger to focus - don't move while camera zooms out to focus\n" +
                            "• Hold your hand steady during capture\n" +
                            "• Avoid shadows or glare on your finger\n" +
                            "• Make sure your finger covers the entire frame",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            "Image Processing",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "The app processes your fingerprint in two steps:\n" +
                            "1. Original capture: Full color RGB image from the camera\n" +
                            "2. Processed image: Enhanced grayscale for better recognition\n" +
                            "Both images are displayed for your reference",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Close")
                                }
                            }
                        )
        }
    }

    private fun runModel(bitmap: Bitmap): FloatArray {
        val input = imageToByteBuffer(bitmap)
        val output = Array(1) { FloatArray(128) } // Siamese model output size
        try {
            if (fingerprintModel == null) {
                Log.e("Registration", "Model is null")
                throw RuntimeException("Model not initialized")
            }
            Log.d("Registration", "Running model with input size: ${input.capacity()}")
            fingerprintModel?.run(input, output)
            Log.d("Registration", "Model output size: ${output[0].size}")
        return output[0]
        } catch (e: Exception) {
            Log.e("Registration", "Model execution failed", e)
            throw RuntimeException("Model execution failed: ${e.localizedMessage}")
        }
    }

    private fun saveEmbedding(context: Context, embedding: FloatArray) {
        try {
            Log.d("Registration", "Starting embedding save process")
            val file = File(context.filesDir, embeddingFileName)
            Log.d("Registration", "Embedding file path: ${file.absolutePath}")
            
            val embeddings = loadAllEmbeddings(context).toMutableList()
            Log.d("Registration", "Current embeddings count: ${embeddings.size}")
            
            embeddings.add(embedding)
            Log.d("Registration", "Added new embedding, new count: ${embeddings.size}")
            
            ObjectOutputStream(FileOutputStream(file)).use { it.writeObject(embeddings) }
            registrationCount++
            Log.d("Registration", "Embedding saved successfully. New registration count: $registrationCount")
        } catch (e: Exception) {
            Log.e("Registration", "Failed to save embedding", e)
            throw RuntimeException("Failed to save embedding: ${e.localizedMessage}")
        }
    }

    private fun loadAllEmbeddings(context: Context): List<FloatArray> {
        val file = File(context.filesDir, embeddingFileName)
        if (!file.exists()) {
            Log.d("Registration", "No embeddings file found, creating new one")
            return emptyList()
        }
        return try {
            val embeddings = ObjectInputStream(FileInputStream(file)).use { it.readObject() as List<FloatArray> }
            Log.d("Registration", "Loaded ${embeddings.size} embeddings")
            embeddings
        } catch (e: Exception) {
            Log.e("Registration", "Failed to load embeddings", e)
            emptyList()
        }
    }

    private fun verifyEmbedding(context: Context, embedding: FloatArray, onResult: (String) -> Unit): Boolean {
        val storedEmbeddings = loadAllEmbeddings(context)
        if (storedEmbeddings.isEmpty()) {
            Log.d("Registration", "No stored embeddings found")
            onResult("No stored fingerprints found.")
            return false
        }

        var minDistance = Float.MAX_VALUE
        var matchedIndex = -1
        val distances = mutableListOf<Pair<Int, Float>>()

        // Calculate distances with all stored embeddings
        for (i in storedEmbeddings.indices) {
            val distance = calculateDistance(embedding, storedEmbeddings[i])
            distances.add(Pair(i + 1, distance))  // Store ID (index + 1) and distance
            if (distance < minDistance) {
                minDistance = distance
                matchedIndex = i
            }
        }

        // Sort distances for display
        distances.sortBy { it.second }

        // Log verification details
        Log.d("Registration", "Minimum distance: $minDistance")
        Log.d("Registration", "Matched index: $matchedIndex")
        Log.d("Registration", "Threshold: $DISTANCE_THRESHOLD")
        
        // Create detailed distance report
        val distanceReport = StringBuilder()
        distanceReport.append("Distance Report:\n")
        distances.forEach { (id, distance) ->
            distanceReport.append("Fingerprint $id: $distance\n")
        }
        Log.d("Registration", distanceReport.toString())
        
        if (minDistance < DISTANCE_THRESHOLD) {
            // Store the matched ID for display
            matchedFingerprintId = matchedIndex + 1
            onResult("Fingerprint verified successfully! (Matched ID: $matchedFingerprintId)\n\n$distanceReport")
            return true
        } else {
            onResult("No matching fingerprint found.\n\n$distanceReport")
            return false
        }
    }

    private fun calculateDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            Log.e("App", "Mismatched embedding sizes: ${embedding1.size} vs ${embedding2.size}")
            return Float.MAX_VALUE
        }
        return kotlin.math.sqrt(embedding1.zip(embedding2).sumOf { (x, y) -> ((x - y) * (x - y)).toDouble() }).toFloat()
    }

    enum class Mode { Register, Verify, Manage }

    fun preprocessWithOpenCV(bitmap: Bitmap): Bitmap {
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Convert to grayscale
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
            
            // Apply adaptive thresholding
            Imgproc.adaptiveThreshold(
                mat, mat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11, 2.0
            )
            
            // Convert back to bitmap
            val resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, resultBitmap)
            
            mat.release()
            return resultBitmap
        } catch (e: Exception) {
            Log.e("Registration", "Preprocessing failed", e)
            return bitmap
        }
    }

    private fun getAppContext(): Context {
        return FingerprintApplication.getInstance().applicationContext
    }

    private fun imageToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val modelInputSize = 96 // Model expects 96x96 input
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(pixels, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)
        
        for (pixel in pixels) {
            // Extract grayscale value and normalize to [0, 1]
            val gray = (pixel and 0xFF)
            val normalizedValue = gray / 255.0f
            byteBuffer.putFloat(normalizedValue)
        }
        
        resizedBitmap.recycle()
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun loadFingerprints(context: Context, fingerprints: MutableList<String>) {
        try {
            val file = File(context.filesDir, embeddingFileName)
            if (file.exists()) {
                val embeddings = ObjectInputStream(FileInputStream(file)).use { it.readObject() as List<FloatArray> }
                registrationCount = embeddings.size
                fingerprints.clear()
                for (i in 1..registrationCount) {
                    fingerprints.add("Fingerprint $i")
                }
                Log.d("Registration", "Loaded $registrationCount fingerprints")
            }
        } catch (e: Exception) {
            Log.e("Registration", "Failed to load fingerprints", e)
        }
    }

    private fun deleteFingerprint(context: Context, index: Int, fingerprints: MutableList<String>) {
        try {
            val file = File(context.filesDir, embeddingFileName)
            if (file.exists()) {
                val embeddings = ObjectInputStream(FileInputStream(file)).use { it.readObject() as List<FloatArray> }.toMutableList()
                if (index in embeddings.indices) {
                    embeddings.removeAt(index)
                    ObjectOutputStream(FileOutputStream(file)).use { it.writeObject(embeddings) }
                    registrationCount = embeddings.size
                    loadFingerprints(context, fingerprints)
                    Log.d("Registration", "Deleted fingerprint at index $index")
                }
            }
        } catch (e: Exception) {
            Log.e("Registration", "Failed to delete fingerprint", e)
            errorMessage = "Failed to delete fingerprint: ${e.localizedMessage}"
            showError = true
        }
    }

    @Composable
    private fun ErrorDialog(
        message: String,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the model
        if (fingerprintModel == null) {
            val assetFileDescriptor = assets.openFd("siamese_model.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            fingerprintModel = Interpreter(modelBuffer)
        }
        setContent {
            MainScreen()
        }
    }
}

@Composable
private fun CameraCaptureWindow(
    onImageCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    enableFlash: Boolean = false,
    enableAutoFocus: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> 
            hasCameraPermission = granted
            Log.d("CameraX", "Camera permission granted: $granted")
        }
    )
    if (!hasCameraPermission) {
        LaunchedEffect(true) {
            Log.d("CameraX", "Requesting camera permission")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        Text("Requesting camera permission...")
        return
    }
    var imageCapture: ImageCapture? = remember { null }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera: Camera? = null // CameraX Camera reference
        
        // Define fingerprint guide size (70x109, portrait orientation)
        val guideWidth = 70.dp
        val guideHeight = 109.dp
    val density = LocalDensity.current
        val guideWidthPx = with(density) { guideWidth.toPx().roundToInt() }
        val guideHeightPx = with(density) { guideHeight.toPx().roundToInt() }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                Log.d("CameraX", "Creating PreviewView")
                val previewView = PreviewView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Use FIT_CENTER to show the full preview without cropping
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                    try {
                        Log.d("CameraX", "Initializing camera")
                val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                            .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val builder = ImageCapture.Builder()
                            .setTargetRotation(previewView.display.rotation)
                            // Set capture mode to maximize quality
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            // Set flash mode to ON for consistent lighting
                            .setFlashMode(ImageCapture.FLASH_MODE_ON)
                            // Set maximum resolution
                            .setTargetResolution(android.util.Size(3024, 4032)) // 12MP in portrait
                imageCapture = builder.build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        
                        // Unbind any previous use cases
                        cameraProvider.unbindAll()
                        
                try {
                            Log.d("CameraX", "Binding camera use cases")
                            camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageCapture
                    )
                            
                            // Enable torch/flash mode for consistent lighting
                            camera?.cameraControl?.enableTorch(true)
                            
                            // Set initial zoom for closer capture (0.0 to 1.0, where 1.0 is max zoom)
                            camera?.cameraControl?.setLinearZoom(0.3f) // 30% zoom for wider view
                            
                            // Set initial focus mode to continuous picture with closer focus
                            val focusMeteringAction = FocusMeteringAction.Builder(
                                previewView.meteringPointFactory.createPoint(
                                    previewView.width / 2f,
                                    previewView.height / 2f
                                )
                            )
                                .addPoint(
                                    previewView.meteringPointFactory.createPoint(
                                        previewView.width / 2f,
                                        previewView.height / 2f
                                    ),
                                    FocusMeteringAction.FLAG_AF
                                )
                                .setAutoCancelDuration(5, TimeUnit.SECONDS) // Increased focus duration
                                .build()
                            camera?.cameraControl?.startFocusAndMetering(focusMeteringAction)
                            
                            // Add tap-to-focus with visual feedback and zoom
                            previewView.setOnTouchListener { v, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(event.x, event.y)
                                    
                                    // Create focus action with longer duration
                                    val action = FocusMeteringAction.Builder(point)
                                        .addPoint(point, FocusMeteringAction.FLAG_AF)
                                        .setAutoCancelDuration(5, TimeUnit.SECONDS)
                                        .build()
                                    
                                    // Start focus and show feedback
                                    camera?.cameraControl?.startFocusAndMetering(action)
                                    Toast.makeText(context, "Focusing...", Toast.LENGTH_SHORT).show()
                                    
                                    // Adjust zoom based on touch position
                                    val zoomFactor = 0.0f // Can be adjusted
                                    camera?.cameraControl?.setLinearZoom(zoomFactor)
                                }
                                true
                            }
                            
                            Log.d("CameraX", "Camera preview started successfully")
                } catch (exc: Exception) {
                    Log.e("CameraX", "Use case binding failed", exc)
                            exc.printStackTrace()
                        }
                    } catch (exc: Exception) {
                        Log.e("CameraX", "Camera initialization failed", exc)
                        exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                Log.d("CameraX", "Updating PreviewView")
            }
        )

        // Fingerprint guide overlay (rectangular, 70x109 portrait)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Semi-transparent background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            
            // Clear center area for fingerprint
            Box(
                modifier = Modifier
                    .size(guideWidth, guideHeight)
                    .background(Color.Transparent)
            )
            
            // Guide border
            Box(
                modifier = Modifier
                    .size(guideWidth, guideHeight)
                    .background(Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                )
            )
        }

            // Capture and Cancel buttons
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = {
                    Log.d("CameraX", "Capture button clicked")
                    val file = File.createTempFile("capture", ".jpg", context.cacheDir)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
                        .build()
                    val currentImageCapture = imageCapture
                    if (currentImageCapture != null) {
                        Log.d("CameraX", "Taking picture")
                        currentImageCapture.takePicture(
                            outputOptions,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    Log.d("CameraX", "Image saved to: ${file.absolutePath}")
                                    try {
                                        Log.d("CameraX", "Attempting to decode bitmap")
                                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                        
                                        Log.d("CameraX", "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")
                                        
                                        // Calculate crop dimensions to match guide window
                                        val previewWidth = bitmap.width
                                        val previewHeight = bitmap.height

                                        // Calculate crop coordinates for vertical orientation
                                        // Use larger crop area to ensure full fingertip capture
                                        val cropWidth = (guideHeightPx * 1.1).toInt()  // 10% larger
                                        val cropHeight = (guideWidthPx * 1.1).toInt()   // 10% larger
                                        
                                        val cropLeft = (previewWidth - cropWidth) / 2
                                        val cropTop = (previewHeight - cropHeight) / 2
                                        
                                        // Ensure crop coordinates are within bounds
                                        val left = max(0, cropLeft)
                                        val top = max(0, cropTop)
                                        val right = min(previewWidth, left + cropWidth)
                                        val bottom = min(previewHeight, top + cropHeight)
                                        
                                        // Crop the bitmap to match the guide window
                                        val croppedBitmap = Bitmap.createBitmap(
                                            bitmap,
                                            left,
                                            top,
                                            right - left,
                                            bottom - top
                                        )
                                        
                                        // Rotate the bitmap to ensure vertical orientation
                                        val rotatedBitmap = if (croppedBitmap.width > croppedBitmap.height) {
                                            val matrix = android.graphics.Matrix()
                                            matrix.postRotate(90f)
                                            Bitmap.createBitmap(
                                                croppedBitmap,
                                                0,
                                                0,
                                                croppedBitmap.width,
                                                croppedBitmap.height,
                                                matrix,
                                                true
                                            )
                                        } else {
                                            croppedBitmap
                                        }
                                        
                                        // Pass the rotated bitmap to MainActivity for processing
                                        (context as? MainActivity)?.let { activity ->
                                            try {
                                                // Create a copy of the bitmap to prevent recycling issues
                                                val bitmapCopy = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                                                activity.runOnUiThread {
                                                    try {
                                                        onImageCaptured(bitmapCopy)
                                                    } catch (e: Exception) {
                                                        Log.e("CameraX", "Error in onImageCaptured callback", e)
                                                        Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("CameraX", "Error copying bitmap", e)
                                                e.printStackTrace()
                                                activity.runOnUiThread {
                                                    Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
                                                }
                                            } finally {
                                                try {
                                                bitmap.recycle()
                                                rotatedBitmap.recycle()
                                                } catch (e: Exception) {
                                                    Log.e("CameraX", "Error recycling bitmaps", e)
                                                }
                                            }
                                        } ?: run {
                                            Log.e("CameraX", "Context is not MainActivity")
                                            try {
                                            bitmap.recycle()
                                            rotatedBitmap.recycle()
                                            } catch (e: Exception) {
                                                Log.e("CameraX", "Error recycling bitmaps", e)
                                            }
                                            (context as? ComponentActivity)?.runOnUiThread {
                                                Toast.makeText(context, "Internal error: wrong context type", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CameraX", "Error processing captured image", e)
                                        e.printStackTrace()
                                        (context as? ComponentActivity)?.runOnUiThread {
                                            Toast.makeText(context, "Error processing image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        file.delete()
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraX", "Image capture failed", exception)
                                    exception.printStackTrace()
                                    (context as? ComponentActivity)?.runOnUiThread {
                                        Toast.makeText(context, "Failed to capture image: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } else {
                        Log.e("CameraX", "ImageCapture is not initialized!")
                        (context as? ComponentActivity)?.runOnUiThread {
                            Toast.makeText(context, "Camera not initialized", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Capture") }
        }
    }
}


