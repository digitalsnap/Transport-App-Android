package com.ridevibe.feature.checkout.ocr

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Live camera preview + capture button for scanning a Student/Senior/PWD ID.
 * On capture, runs the frame through ML Kit text recognition so the caller can
 * pre-fill/validate the ID number before submitting it with the booking.
 */
@Composable
fun IdCaptureCamera(
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
