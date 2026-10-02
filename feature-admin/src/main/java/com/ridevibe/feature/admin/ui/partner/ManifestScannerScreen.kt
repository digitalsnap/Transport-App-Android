package com.ridevibe.feature.admin.ui.partner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ridevibe.feature.admin.ui.components.MicroLabel
import com.ridevibe.feature.admin.ui.formatPhTime
import com.ridevibe.feature.admin.viewmodel.CheckInOutcome
import java.util.concurrent.Executors

/** The same QR is in front of the lens for many frames; one decode per ticket per pause is enough. */
private const val REPEAT_SCAN_COOLDOWN_MS = 2_500L

/**
 * Full-screen QR check-in: CameraX preview plus an ML Kit barcode analyzer
 * limited to QR codes. Every decode goes to [onScanned]; the view model
 * matches it against the manifest and hands back [outcome] to show.
 *
 * Camera permission is requested here, with a rationale on refusal and a
 * Settings shortcut once the system stops asking (permanent denial).
 */
@Composable
fun ManifestScannerScreen(
    outcome: CheckInOutcome?,
    onScanned: (payload: String) -> Unit,
    onDismissOutcome: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var deniedPermanently by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        asked = true
        // After a refusal, a false rationale flag means "Don't ask again" (or a policy): only Settings can fix it.
        if (!granted) {
            val activity = context.findActivity()
            deniedPermanently = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { if (!hasPermission && !asked) permissionLauncher.launch(Manifest.permission.CAMERA) }
    // Coming back from Settings with the camera allowed must start the scanner without reopening it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) deniedPermanently = false
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.inverseSurface)) {
        if (hasPermission) {
            QrCameraPreview(onScanned = onScanned, modifier = Modifier.fillMaxSize())
        } else {
            PermissionFallback(
                deniedPermanently = deniedPermanently,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
            Text(
                "Scan the rider's QR ticket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }

        if (hasPermission) {
            // Viewfinder frame: a plain outline so the conductor knows where to hold the phone.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .background(Color.Transparent, RoundedCornerShape(16.dp))
                    .border(2.dp, MaterialTheme.colorScheme.inverseOnSurface, RoundedCornerShape(16.dp)),
            )
        }

        outcome?.let {
            OutcomeCard(
                outcome = it,
                onDismiss = onDismissOutcome,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

/** Result banner, coloured by outcome: OK = primary container, warning = tertiary, refusal = error container. */
@Composable
private fun OutcomeCard(outcome: CheckInOutcome, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val (container, content) = when (outcome) {
        is CheckInOutcome.Boarded -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        is CheckInOutcome.AlreadyBoarded -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        is CheckInOutcome.Cancelled, is CheckInOutcome.NotOnManifest ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    val (title, detail) = when (outcome) {
        is CheckInOutcome.Boarded -> "Boarded ✓ ${outcome.passengerName}" to
            (if (outcome.seatLabels.isEmpty()) "Open seating" else "Seats ${outcome.seatLabels.joinToString(", ")}")
        is CheckInOutcome.AlreadyBoarded -> "Already boarded" to
            "${outcome.passengerName} was checked in at ${formatPhTime(outcome.boardedAtEpochMillis)}"
        is CheckInOutcome.Cancelled -> "Cancelled ticket" to "${outcome.passengerName} — this booking was cancelled; do not board"
        is CheckInOutcome.NotOnManifest -> "Not on this manifest" to outcome.reason
    }
    Surface(shape = RoundedCornerShape(16.dp), color = container, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = content)
            }
            TextButton(onClick = onDismiss) { Text("OK", color = content) }
        }
    }
}

@Composable
private fun PermissionFallback(
    deniedPermanently: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            MicroLabel("Camera needed")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Checking passengers in means scanning the QR on their ticket, so the console needs the camera. " +
                    "Nothing is recorded or uploaded — frames are read for a QR code and dropped.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (deniedPermanently) {
                Text(
                    "Camera access was turned off for RideVibe. Allow it under App permissions in Settings, then come back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Settings", fontWeight = FontWeight.Bold) }
            } else {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Allow camera", fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // The way out without a camera: back to the list, where each row has "Mark boarded".
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Use \"Mark boarded\" on the list instead")
            }
        }
    }
}

/**
 * CameraX preview bound to the composition's lifecycle with an ImageAnalysis
 * use case feeding ML Kit. Analysis runs on its own single thread; the
 * camera, scanner and executor are released when the preview leaves
 * composition.
 */
@Composable
private fun QrCameraPreview(onScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnScanned by rememberUpdatedState(onScanned)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val recent = remember { RecentScanFilter(REPEAT_SCAN_COOLDOWN_MS) }
    val camera = remember { CameraBinding() }

    DisposableEffect(Unit) {
        onDispose {
            // Unbind before closing the scanner: an analyzer still running against a closed
            // ML Kit client would throw on the analysis thread.
            camera.release()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val mainExecutor = ContextCompat.getMainExecutor(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                // The provider can answer after the scanner was closed (fast back press);
                // binding then would hold the camera with nothing to release it.
                if (camera.isReleased) return@addListener
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    decodeQr(proxy, scanner) { payload ->
                        // ML Kit answers on its own thread; state changes belong on main.
                        if (recent.accept(payload)) mainExecutor.execute { if (!camera.isReleased) latestOnScanned(payload) }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                camera.bound(provider, analysis)
            }, mainExecutor)
            previewView
        },
    )
}

/**
 * What the provider future bound, kept so the composable can unbind it on
 * dispose. Touched on the main thread only (the future's listener and
 * `onDispose` both run there), so plain fields are enough.
 */
private class CameraBinding {
    var isReleased = false
        private set
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null

    fun bound(provider: ProcessCameraProvider, analysis: ImageAnalysis) {
        this.provider = provider
        this.analysis = analysis
    }

    fun release() {
        isReleased = true
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        analysis = null
        provider = null
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun decodeQr(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onPayload: (String) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }?.let(onPayload)
        }
        .addOnCompleteListener { proxy.close() }
}

/** Suppresses re-decodes of the same payload inside the cooldown; a different ticket goes straight through. */
private class RecentScanFilter(private val cooldownMs: Long) {
    private var lastPayload: String? = null
    private var lastAtMillis = 0L

    @Synchronized
    fun accept(payload: String): Boolean {
        val now = System.currentTimeMillis()
        if (payload == lastPayload && now - lastAtMillis < cooldownMs) return false
        lastPayload = payload
        lastAtMillis = now
        return true
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
