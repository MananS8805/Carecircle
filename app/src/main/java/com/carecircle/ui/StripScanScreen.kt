package com.carecircle.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carecircle.R
import com.carecircle.domain.Expiry
import com.carecircle.domain.SaltParser
import com.carecircle.domain.ScanQuality
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.Muted
import com.carecircle.ui.theme.Palette
import com.carecircle.ui.theme.SoftButton
import com.carecircle.ui.theme.SoftCard
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

data class ScanResult(val salt: String, val needsCheck: Boolean, val expiry: String? = null)

/**
 * Live camera preview with a framing guide. The captured frame is read by ML Kit on the phone, kept in memory only
 * and never saved. If no composition can be read the user can retry, pick from the gallery or type the name.
 */
@Composable
fun StripScanScreen(onResult: (ScanResult) -> Unit, onTypeInstead: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { recognizer.close() } }

    fun handleText(result: com.google.mlkit.vision.text.Text) {
        val found = SaltParser.parse(result.text)
        val expiry = Expiry.parse(result.text)?.let { Expiry.format(it) }
        val confidences = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.map { it.confidence }
        val avg = if (confidences.isEmpty()) null else confidences.average().toFloat()
        when (ScanQuality.assess(found, avg)) {
            ScanQuality.Verdict.RETRY -> { failed = true; busy = false }
            ScanQuality.Verdict.CHECK -> onResult(ScanResult(found.joinToString(" + "), needsCheck = true, expiry = expiry))
            ScanQuality.Verdict.GOOD -> onResult(ScanResult(found.joinToString(" + "), needsCheck = false, expiry = expiry))
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true; failed = false
            runCatching { InputImage.fromFilePath(ctx, uri) }.onSuccess { img ->
                recognizer.process(img).addOnSuccessListener(::handleText).addOnFailureListener { failed = true; busy = false }
            }.onFailure { failed = true; busy = false }
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Canvas)) {
        if (granted) {
            CameraPreview(
                recognizer = recognizer, busy = busy, failed = failed,
                onBusy = { busy = it; if (it) failed = false },
                onText = ::handleText, onFail = { failed = true; busy = false },
                onPickGallery = { gallery.launch("image/*") }, onTypeInstead = onTypeInstead,
            )
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.Center) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_cam_title), style = MaterialTheme.typography.titleLarge)
                    Muted(stringResource(R.string.scan_cam_why))
                    if (denied) Text(stringResource(R.string.scan_cam_denied), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Bad)
                    if (!denied) BigButton(stringResource(R.string.scan_cam_allow), { asked = true; permission.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 12.dp))
                    SoftButton(stringResource(R.string.scan_gallery), { gallery.launch("image/*") }, Modifier.fillMaxWidth().padding(top = 8.dp))
                    SoftButton(stringResource(R.string.scan_type_instead), onTypeInstead, Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (failed) Text(stringResource(R.string.scan_fail), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 10.dp), color = Palette.Orange)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    recognizer: com.google.mlkit.vision.text.TextRecognizer, busy: Boolean, failed: Boolean,
    onBusy: (Boolean) -> Unit, onText: (com.google.mlkit.vision.text.Text) -> Unit, onFail: () -> Unit,
    onPickGallery: () -> Unit, onTypeInstead: () -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            runCatching {
                provider!!.unbindAll()
                camera = provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.onFailure { onFail() }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { camera?.cameraControl?.enableTorch(false) }
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    fun capture() {
        onBusy(true)
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Processed in memory only: nothing is written to storage.
                val rotation = image.imageInfo.rotationDegrees
                val bmp = runCatching { image.toBitmap() }.getOrNull()
                image.close()
                if (bmp == null) { ContextCompat.getMainExecutor(ctx).execute(onFail); return }
                recognizer.process(InputImage.fromBitmap(bmp, rotation))
                    .addOnSuccessListener { onText(it) }
                    .addOnFailureListener { onFail() }
                    .addOnCompleteListener { bmp.recycle() }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(ctx).execute(onFail)
            }
        })
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * 0.86f
            val h = size.height * 0.2f
            val tl = Offset((size.width - w) / 2f, size.height * 0.34f)
            drawRoundRect(Color.White, tl, Size(w, h), CornerRadius(28f), style = Stroke(5f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.scan_guide), Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) {
                        Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, stringResource(R.string.cd_torch), tint = Color.White)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failed) {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_fail), style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftButton(stringResource(R.string.scan_type_instead), onTypeInstead)
                        }
                    }
                }
                BigButton(if (busy) stringResource(R.string.scan_reading) else if (failed) stringResource(R.string.action_retry) else stringResource(R.string.scan_capture), { if (!busy) capture() }, enabled = !busy)
                SoftButton(stringResource(R.string.scan_gallery), onPickGallery, Modifier.fillMaxWidth())
            }
        }
    }
}
