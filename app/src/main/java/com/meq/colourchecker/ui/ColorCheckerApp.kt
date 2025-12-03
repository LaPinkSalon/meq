package com.meq.colourchecker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meq.colourchecker.camera.CameraPreview
import com.meq.colourchecker.processing.AnalysisFrame
import com.meq.colourchecker.ui.model.DetectionUiState
import com.meq.colourchecker.ui.model.DetectionDebugInfo
import com.meq.colourchecker.ui.viewmodel.DetectionViewModel
import com.meq.colourchecker.ui.theme.ColourCheckerTheme
import android.Manifest

@Composable
fun ColorCheckerApp(viewModel: DetectionViewModel = hiltViewModel()) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val debugInfo = viewModel.debug.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onCameraPermissionRequested()
        } else {
            viewModel.requireCameraPermission()
        }
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DetectionScreen(
        state = uiState,
        onToggleTorch = viewModel::toggleTorch,
        onRequestPermissions = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onFrame = viewModel::onFrameCaptured,
        debugInfo = debugInfo,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionScreen(
    state: DetectionUiState,
    onToggleTorch: () -> Unit,
    onRequestPermissions: () -> Unit,
    onFrame: (AnalysisFrame) -> Unit,
    debugInfo: DetectionDebugInfo?,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Colour Checker") },
                actions = {
                    TextButton(onClick = onToggleTorch) {
                        Text(if (state.isTorchOn) "Torch on" else "Torch off")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(state = state)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onRequestPermissions = onRequestPermissions,
                    permissionsRequired = state.permissionsRequired,
                    torchEnabled = state.isTorchOn,
                    onFrame = onFrame
                )
                debugInfo?.quad?.takeIf { it.size >= 4 }?.let { quad ->
                    QuadOverlay(
                        quad = quad,
                        modifier = Modifier.fillMaxSize(),
                        frameWidth = debugInfo.frameWidth,
                        frameHeight = debugInfo.frameHeight,
                        rotationDegrees = debugInfo.rotationDegrees,
                        secondaryQuad = debugInfo.secondaryQuad
                    )
                }
            }
            val debugPanelHeight = 72.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(debugPanelHeight)
            ) {
                debugInfo?.let { DebugHud(it, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun StatusCard(state: DetectionUiState) {
    val (label, tone) = when (state.status) {
        DetectionUiState.Status.Passed -> "Pass" to Color(0xFF22C55E)
        DetectionUiState.Status.Failed -> "Fail" to Color(0xFFDC2626)
        DetectionUiState.Status.Scanning -> "Scanning" to MaterialTheme.colorScheme.primary
        DetectionUiState.Status.Error -> "Error" to Color(0xFFEA580C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                label = { Text(label) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = Color.White,
                    containerColor = tone
                )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.hint,
                    style = MaterialTheme.typography.bodySmall
                )
                state.failureReason?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
                state.errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugHud(info: DetectionDebugInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                "Area ${info.areaScore.format3()}  Aspect ${info.aspectScore.format3()}  Contrast ${info.contrastScore.format3()}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
            )
            Text(
                "Blur ${info.blurScore.format3()}  Patch ${info.patchScore.format3()}  Conf ${info.confidence.format2()}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
            )
            Text(
                "ΔE avg ${info.avgDeltaE?.format2() ?: "--"}  max ${info.maxDeltaE?.format2() ?: "--"}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
            )
        }
    }
}

@Composable
private fun QuadOverlay(
    quad: List<com.meq.colourchecker.processing.Point>,
    modifier: Modifier = Modifier,
    frameWidth: Int = 0,
    frameHeight: Int = 0,
    rotationDegrees: Int = 0,
    secondaryQuad: List<com.meq.colourchecker.processing.Point> = emptyList(),
    secondaryValid: Boolean = false
) {
    if (quad.size < 4 || frameWidth <= 0 || frameHeight <= 0) return
    Canvas(modifier = modifier) {
        // PreviewView is using FILL_CENTER (center-crop). Map buffer coords by scaling
        // to fill and then offsetting to account for the cropped margins.
        val bufferWidth = if (rotationDegrees % 180 == 0) frameWidth else frameHeight
        val bufferHeight = if (rotationDegrees % 180 == 0) frameHeight else frameWidth
        val scale = maxOf(size.width / bufferWidth.toFloat(), size.height / bufferHeight.toFloat())
        val offsetX = (size.width - bufferWidth * scale) / 2f
        val offsetY = (size.height - bufferHeight * scale) / 2f

        // Map raw buffer coords into the rotated buffer that PreviewView displays.
        // CameraX reports rotationDegrees as the clockwise rotation needed to display upright.
        fun rotate(p: com.meq.colourchecker.processing.Point): Pair<Float, Float> =
            when ((rotationDegrees % 360 + 360) % 360) {
                90 -> Pair(frameHeight - p.y, p.x) // x' = H - y, y' = x
                180 -> Pair(frameWidth - p.x, frameHeight - p.y)
                270 -> Pair(p.y, frameWidth - p.x) // x' = y, y' = W - x
                else -> Pair(p.x, p.y)
            }

        fun map(p: com.meq.colourchecker.processing.Point): androidx.compose.ui.geometry.Offset {
            val (rx, ry) = rotate(p)
            return androidx.compose.ui.geometry.Offset(rx * scale + offsetX, ry * scale + offsetY)
        }

        val path = Path().apply {
            moveTo(map(quad[0]).x, map(quad[0]).y)
            quad.drop(1).forEach { p -> lineTo(map(p).x, map(p).y) }
            close()
        }
        drawPath(path = path, color = Color(0xCC22C55E), style = Stroke(width = 6f))
        quad.forEach { p ->
            val mapped = map(p)
            drawCircle(color = Color(0xFF22C55E), radius = 8f, center = mapped)
        }
        if (secondaryQuad.size >= 4) {
            val secPath = Path().apply {
                moveTo(map(secondaryQuad[0]).x, map(secondaryQuad[0]).y)
                secondaryQuad.drop(1).forEach { p -> lineTo(map(p).x, map(p).y) }
                close()
            }
            val secColor = Color(0xCC22C55E)
            val dotColor = Color(0xFF22C55E)
            drawPath(path = secPath, color = secColor, style = Stroke(width = 6f))
            secondaryQuad.forEach { p ->
                val mapped = map(p)
                drawCircle(color = dotColor, radius = 8f, center = mapped)
            }
        }
    }
}

private fun Double.format3(): String = String.format("%.3f", this)
private fun Double.format2(): String = String.format("%.2f", this)
private fun Float.format2(): String = String.format("%.2f", this)

@Preview(showBackground = true)
@Composable
private fun PreviewDetectionScreen() {
    ColourCheckerTheme {
        DetectionScreen(
            state = DetectionUiState(
                status = DetectionUiState.Status.Scanning,
                hint = "Hold the colour checker flat and centered."
            ),
            onToggleTorch = {},
            onRequestPermissions = {},
            onFrame = {},
            debugInfo = null,
            snackbarHostState = SnackbarHostState()
        )
    }
}
