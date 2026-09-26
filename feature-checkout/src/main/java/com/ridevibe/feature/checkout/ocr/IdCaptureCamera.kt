package com.ridevibe.feature.checkout.ocr

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File

/**
 * Live camera preview + capture button for scanning a Student/Senior/PWD ID,
 * with a gallery pick as the fallback for riders whose camera is broken or
 * permanently denied. Requests the CAMERA runtime permission before binding
 * CameraX — binding without it crashes or shows a dead preview on real devices.
 *
 * Every successful path ends in [onCaptured] with a file that exists on disk
 * plus whatever ML Kit read off it (possibly empty — the conductor still
 * verifies the physical ID). Every failure ends in [onError] with a message;
 * a failure never produces a blank path.
 */
@Composable
fun IdCaptureCamera(
    onCaptured: (imagePath: String, recognizedText: String) -> Unit,
    onError: (message: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Saveable: a rotation while the system dialog is up must not re-prompt.
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        // Denied with no rationale to show = "Don't ask again" (or a policy block):
        // only the system settings page can turn it back on.
        if (!granted) {
            val activity = context.findActivity()
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isProcessing = true
        scope.launch {
            val file = DiscountIdImageStore.importPickedImage(context, uri)
            if (file == null) {
                isProcessing = false
                onError("Couldn't read that photo. Try another one or use the camera.")
            } else {
                recognizeText(context, file) { text ->
                    isProcessing = false
                    onCaptured(file.absolutePath, text)
                }
            }
        }
    }
    val pickFromGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionDenied) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Fill the frame with the ID card",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClose, enabled = !isProcessing) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel ID capture")
            }
        }

        when {
            hasPermission -> CameraPreviewAndCapture(
                isProcessing = isProcessing,
                onCaptureStarted = { isProcessing = true },
                onCaptured = { path, text ->
                    isProcessing = false
                    onCaptured(path, text)
                },
                onError = { message ->
                    isProcessing = false
                    onError(message)
                },
                onPickFromGallery = pickFromGallery,
            )

            permissionDenied -> Column {
                Text(
                    if (permanentlyDenied) {
                        "Camera access is turned off for RideVibe. Turn it on in system settings, " +
                            "or choose a photo of the ID from your gallery."
                    } else {
                        "Camera access is needed to photograph the ID. Allow camera " +
                            "permission to continue, or choose a photo from your gallery."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (permanentlyDenied) {
                        Button(
                            onClick = { context.openAppSettings() },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Open settings") }
                    } else {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Allow camera") }
                    }
                    OutlinedButton(
                        onClick = pickFromGallery,
                        enabled = !isProcessing,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Choose from gallery") }
                }
                if (isProcessing) ProcessingIndicator()
            }

            else -> Text(
                "Requesting camera permission…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CameraPreviewAndCapture(
    isProcessing: Boolean,
    onCaptureStarted: () -> Unit,
    onCaptured: (imagePath: String, recognizedText: String) -> Unit,
    onError: (message: String) -> Unit,
    onPickFromGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    // Held so the DisposableEffect can release the camera when this leaves
    // composition (Close, retake, navigating away) instead of at Activity end.
    val boundProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val disposed = remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            disposed.value = true
            boundProvider.value?.unbindAll()
            boundProvider.value = null
        }
    }

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    // The future can complete after Close was tapped; don't bind to a dead view.
                    if (disposed.value) return@addListener
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                        boundProvider.value = cameraProvider
                    }.onFailure { onError("Couldn't start the camera. Choose a photo from your gallery instead.") }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
        if (isProcessing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                onCaptureStarted()
                captureAndRecognize(context, imageCapture, onCaptured, onError)
            },
            enabled = !isProcessing,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(if (isProcessing) "Reading ID…" else "Capture ID")
        }
        TextButton(
            onClick = onPickFromGallery,
            enabled = !isProcessing,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Choose from gallery") }
    }
}

@Composable
private fun ProcessingIndicator() {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.height(20.dp).aspectRatio(1f))
        Text("Reading ID…", style = MaterialTheme.typography.bodySmall)
    }
}

private fun captureAndRecognize(
    context: Context,
    imageCapture: ImageCapture,
    onCaptured: (imagePath: String, recognizedText: String) -> Unit,
    onError: (message: String) -> Unit,
) {
    val outputFile = DiscountIdImageStore.newCaptureFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                if (!outputFile.isFile) {
                    onError("The photo wasn't saved. Try again.")
                    return
                }
                recognizeText(context, outputFile) { text -> onCaptured(outputFile.absolutePath, text) }
            }

            override fun onError(exception: ImageCaptureException) {
                outputFile.delete()
                onError("Couldn't take the photo (${exception.imageCaptureError}). Try again.")
            }
        },
    )
}

/**
 * Runs ML Kit text recognition on a saved photo. OCR failing is not a capture
 * failure — the photo is still the proof the conductor asks for — so the
 * result is simply an empty string and the view model flags it for review.
 */
private fun recognizeText(context: Context, file: File, onResult: (String) -> Unit) {
    val image = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }
        .getOrElse {
            onResult("")
            return
        }
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        .process(image)
        .addOnSuccessListener { visionText -> onResult(visionText.text) }
        .addOnFailureListener { onResult("") }
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    runCatching { startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
