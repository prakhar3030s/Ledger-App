package com.example.ledger.ui.components

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ledger.ui.theme.AccentYellow
import com.example.ledger.ui.theme.OledBlack
import com.example.ledger.ui.theme.SlateGrey
import com.example.ledger.ui.theme.StarkWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun BrutalistCameraView(
    onImageCaptured: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isFlashing by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 1f else 0f,
        animationSpec = tween(durationMillis = 50),
        label = "flashAlpha"
    )

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val preview = remember {
        Preview.Builder().build()
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("BrutalistCamera", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                Log.e("BrutalistCamera", "Failed to unbind camera on dispose", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .systemBarsPadding()
    ) {
        // 2. THE VIEWFINDER (The Camera Feed)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    preview.setSurfaceProvider(this.surfaceProvider)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 120.dp, start = 24.dp, end = 24.dp)
                .border(2.dp, SlateGrey, RectangleShape)
        )

        // Flash Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 120.dp, start = 24.dp, end = 24.dp)
                .background(StarkWhite.copy(alpha = flashAlpha))
        )

        // 3. THE CROSSHAIRS
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[   ]",
                style = TextStyle(
                    color = StarkWhite,
                    fontSize = 72.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp
                )
            )
        }

        // 4. THE ABORT BUTTON (Pinned strictly to Top-Left)
        Text(
            text = "[ ABORT ]",
            style = MaterialTheme.typography.labelMedium.copy(
                color = SlateGrey,
                letterSpacing = 2.sp
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .clickable { onCancel() }
        )

        // 5. THE ACQUIRE BUTTON (Pinned strictly to the Bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .height(72.dp)
                .background(AccentYellow)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        isFlashing = true
                        delay(50)
                        isFlashing = false
                    }

                    val receiptsDir = File(context.filesDir, "receipts")
                    receiptsDir.mkdirs()
                    val photoFile = File(receiptsDir, "${UUID.randomUUID()}.jpg")

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onImageCaptured(photoFile.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("BrutalistCamera", "Photo capture failed", exception)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[ ACQUIRE TARGET ]",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = OledBlack,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp
                )
            )
        }
    }
}
