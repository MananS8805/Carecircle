package com.carecircle.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carecircle.R
import com.carecircle.domain.CropMath
import com.carecircle.domain.Expiry
import com.carecircle.domain.FRect
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
import java.io.File
import java.util.concurrent.Executors

data class ScanResult(val salt: String, val needsCheck: Boolean, val expiry: String? = null)

/**
 * Live camera preview with a framing guide, torch and tap-to-focus. Only the part of the picture inside the guide is read, by
 * ML Kit on the phone, in memory; the longest side is shrunk to 2048 px first. Nothing is uploaded. If the live camera cannot
 * start, the phone's own camera app is offered instead, and its temporary file is deleted right after reading. If nothing can
 * be read the user can retry, pick a photo or type the name.
 */
@Composable
fun StripScanScreen(onResult: (ScanResult) -> Unit, onTypeInstead: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok; denied = !ok
        // "Don't ask again" shows up as: refused, and no rationale to show. Then only system settings can help, so no loop.
        permanentlyDenied = !ok && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { recognizer.close() } }

    fun handleText(result: com.google.mlkit.vision.text.Text) {
        val found = SaltParser.parse(result.text)
        val expiry = Expiry.parse(result.text)?.let { Expiry.format(it) }
        val confidences = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.map { it.confidence }
        val avg = if (confidences.isEmpty()) null else confidences.average().toFloat()
        when (ScanQuality.assess(found, avg)) {
            ScanQuality.Verdict.RETRY -> { failed = true; busy = false } // nothing readable: say so, offer a retry
            ScanQuality.Verdict.CHECK -> onResult(ScanResult(found.joinToString(" + "), needsCheck = true, expiry = expiry))
            ScanQuality.Verdict.GOOD -> onResult(ScanResult(found.joinToString(" + "), needsCheck = false, expiry = expiry))
        }
    }

    fun readUri(uri: Uri, deleteAfter: File? = null) {
        busy = true; failed = false
        runCatching { InputImage.fromFilePath(ctx, uri) }.onSuccess { img ->
            recognizer.process(img).addOnSuccessListener(::handleText).addOnFailureListener { failed = true; busy = false }
                .addOnCompleteListener { deleteAfter?.delete() }
        }.onFailure { failed = true; busy = false; deleteAfter?.delete() }
    }

    // The system photo picker needs no storage permission.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) readUri(uri) }
    val pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Fallback when the live camera cannot start: the phone's camera app writes one temporary file, read and deleted at once.
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val systemCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingFile; pendingFile = null
        if (ok && f != null) readUri(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f), deleteAfter = f) else f?.delete()
    }
    val useSystemCamera = {
        val f = File.createTempFile("scan-", ".jpg", ctx.cacheDir)
        pendingFile = f
        systemCamera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
    }

    Box(Modifier.fillMaxSize().background(Palette.Canvas)) {
        if (granted && !cameraUnavailable) {
            CameraPreview(
                recognizer = recognizer, busy = busy, failed = failed,
                onBusy = { busy = it; if (it) failed = false },
                onText = ::handleText, onFail = { failed = true; busy = false },
                onCannotStart = { cameraUnavailable = true },
                onPickGallery = pickPhoto, onCancel = onTypeInstead,
            )
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.Center) {
                SoftCard(Modifier.fillMaxWidth()) {
                    if (cameraUnavailable) {
                        Text(stringResource(R.string.scan_cam_unavailable), style = MaterialTheme.typography.titleLarge)
                        BigButton(stringResource(R.string.scan_cam_system), useSystemCamera, Modifier.padding(top = 12.dp))
                    } else {
                        Text(stringResource(R.string.scan_cam_title), style = MaterialTheme.typography.titleLarge)
                        Muted(stringResource(R.string.scan_cam_why))
                        if (denied) Text(stringResource(R.string.scan_cam_denied), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Bad)
                        if (permanentlyDenied) SoftButton(stringResource(R.string.scan_cam_settings), {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, Modifier.fillMaxWidth().padding(top = 8.dp))
                        else BigButton(stringResource(R.string.scan_cam_allow), { permission.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 12.dp))
                    }
                    SoftButton(stringResource(R.string.scan_gallery), pickPhoto, Modifier.fillMaxWidth().padding(top = 8.dp))
                    SoftButton(stringResource(R.string.scan_type_instead), onTypeInstead, Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (failed) Text(stringResource(R.string.scan_fail), Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
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
    onCannotStart: () -> Unit, onPickGallery: () -> Unit, onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; keepScreenOn = true } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val orientation = LocalConfiguration.current.orientation

    // Turning the phone only changes how the next picture is rotated; the preview keeps running.
    LaunchedEffect(orientation) { previewView.display?.rotation?.let { imageCapture.targetRotation = it } }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider!!.unbindAll()
                camera = provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.onFailure { com.carecircle.data.DiagLog.log(ctx, com.carecircle.domain.DiagCode.CAMERA_FAILED, it); onCannotStart() }
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
                // Read in memory only: turn upright, keep what is inside the guide, shrink, read. Nothing is written to storage.
                val rotation = image.imageInfo.rotationDegrees
                val full = runCatching { image.toBitmap() }.getOrNull()
                val iw = image.width; val ih = image.height
                image.close()
                if (full == null) { ContextCompat.getMainExecutor(ctx).execute(onFail); return }
                val upright = if (rotation == 0) full else Bitmap.createBitmap(full, 0, 0, full.width, full.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                if (upright !== full) full.recycle()
                val guide = CropMath.guideInView(viewSize.width.toFloat(), viewSize.height.toFloat())
                val crop = CropMath.guideToImage(guide, viewSize.width.toFloat(), viewSize.height.toFloat(), iw, ih, rotation)
                val cropped = crop?.let { r -> CropMath.toPixels(r, upright.width, upright.height).let { p -> Bitmap.createBitmap(upright, p[0], p[1], p[2], p[3]) } } ?: upright
                if (cropped !== upright) upright.recycle()
                val (tw, th) = CropMath.scaleToMax(cropped.width, cropped.height)
                val small = if (tw == cropped.width) cropped else Bitmap.createScaledBitmap(cropped, tw, th, true).also { cropped.recycle() }
                recognizer.process(InputImage.fromBitmap(small, 0))
                    .addOnSuccessListener { onText(it) }
                    .addOnFailureListener { onFail() }
                    .addOnCompleteListener { small.recycle() }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(ctx).execute(onFail)
            }
        })
    }

    val guideText = stringResource(R.string.cd_scan_guide)
    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize().onSizeChanged { viewSize = it })
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = guideText }.pointerInput(camera) {
                detectTapGestures { tap ->
                    val c = camera ?: return@detectTapGestures
                    val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                    c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build()) // tap to focus
                }
            },
        ) {
            val g: FRect = CropMath.guideInView(size.width, size.height)
            drawRoundRect(Color.White, Offset(g.left, g.top), Size(g.width, g.height), CornerRadius(28f), style = Stroke(5f))
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
                    SoftCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(stringResource(R.string.scan_fail), style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftButton(stringResource(R.string.scan_type_instead), onCancel)
                        }
                    }
                }
                BigButton(
                    if (busy) stringResource(R.string.scan_reading) else if (failed) stringResource(R.string.action_retry) else stringResource(R.string.scan_capture),
                    { if (!busy) capture() }, Modifier.heightIn(min = 64.dp), enabled = !busy,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(stringResource(R.string.scan_gallery), onPickGallery, Modifier.weight(1f))
                    SoftButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f))
                }
            }
        }
    }
}
