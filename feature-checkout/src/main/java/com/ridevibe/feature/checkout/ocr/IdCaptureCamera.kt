package com.ridevibe.feature.checkout.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Live camera preview + capture button for scanning a Student/Senior/PWD ID.
 * Requests the CAMERA runtime permission before binding CameraX — binding
 * without it crashes or shows a dead preview on real devices. On capture,
 * the frame runs through ML Kit text recognition so the caller can
 * pre-fill/validate the ID number before submitting it with the booking.
 */
@Composable
fun IdCaptureCamera(
    onCaptured: (imagePath: String, recognizedText: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    when {
        hasPermission -> CameraPreviewAndCapture(onCaptured = onCaptured, modifier = modifier)

        permissionDenied -> Column(modifier = modifier.fillMaxWidth()) {
            Text(
                "Camera access is needed to photograph the ID. Allow camera " +
                    "permission to continue, or grant it from system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Allow camera")
            }
        }

        else -> Text(
            "Requesting camera permission…",
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun CameraPreviewAndCapture(
    onCaptured: (imagePath: String, recognizedText: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
    }

    Button(onClick = { captureAndRecognize(context, imageCapture, onCaptured) }) {
        Text("Capture ID")
    }
}

private fun captureAndRecognize(
    context: Context,
    imageCapture: ImageCapture,
    onCaptured: (imagePath: String, recognizedText: String) -> Unit,
) {
    val outputFile = File(context.cacheDir, "discount_id_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(outputFile))
                recognizer.process(image)
                    .addOnSuccessListener { visionText -> onCaptured(outputFile.absolutePath, visionText.text) }
                    .addOnFailureListener { onCaptured(outputFile.absolutePath, "") }
            }

            override fun onError(exception: ImageCaptureException) {
                // Surfaced to the caller as an empty result; UI can prompt a retry.
                onCaptured("", "")
            }
        },
    )
}
