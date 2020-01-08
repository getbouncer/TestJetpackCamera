package com.getbouncer.cardscan.base

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysisConfig
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.getbouncer.cardscan.base.camera.CardImageAnalysisAdapter
import com.getbouncer.cardscan.base.domain.CardImage
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.ml.AggregateResultListener
import com.getbouncer.cardscan.base.ml.CardImageOcrResultAggregator
import com.getbouncer.cardscan.base.ml.CoroutineMemoryBoundAnalyzerLoop
import com.getbouncer.cardscan.base.ml.Rate
import com.getbouncer.cardscan.base.ml.ResultAggregatorConfig
import com.getbouncer.cardscan.base.ml.models.MockCpuAnalyzer
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private const val CAMERA_PERMISSION_REQUEST = 1200

@ExperimentalTime
class CardScanActivity : AppCompatActivity(), AggregateResultListener<CardImage, CardOcrResult> {

    companion object {
        private const val DEFAULT_FRAME_STORAGE_BYTES = 0x2000000 // 32MB
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        } else {
            texture.post { startCamera() }
        }

        texture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateTransform() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> texture.post { startCamera() }
        }
    }

    private fun startCamera() {
        val resultAggregatorConfig = ResultAggregatorConfig.Builder()
            .withMaxTotalAggregationTime(10.seconds)
            .withTrackFrameRate(true)
            .build()
        val mainLoop = CoroutineMemoryBoundAnalyzerLoop(
            analyzer = MockCpuAnalyzer(),
            resultHandler = CardImageOcrResultAggregator(resultAggregatorConfig, this, requiredAgreementCount = 5)
//            , maximumFrameMemoryBytes = DEFAULT_FRAME_STORAGE_BYTES
        )
        val mainLoopAdapter = CardImageAnalysisAdapter<CardOcrResult>(mainLoop)
        mainLoop.start()

        val imageAnalysisConfig = ImageAnalysisConfig.Builder()
            .setTargetRotation(Surface.ROTATION_0)
//            .setTargetResolution(Size(texture.width, texture.height))
            .build()
        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), mainLoopAdapter)

        val previewConfig: PreviewConfig = PreviewConfig.Builder()
            .setLensFacing(CameraX.LensFacing.BACK)
            .setTargetResolution(Size(texture.width, texture.height))
            .build()
        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener { onPreviewUpdated(it) }

        CameraX.bindToLifecycle(this, preview)
        CameraX.bindToLifecycle(this, imageAnalysis)
    }

    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = texture.width / 2f
        val centerY = texture.height / 2f

        val rotationDegrees = when (texture.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }

        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        texture.setTransform(matrix)
    }

    /**
     * This solves the error:
     *
     * ```
     * updateAndRelease: GLConsumer is not attached to an OpenGL ES context
     * ```
     *
     * https://stackoverflow.com/questions/56064248/android-camerax-doesnt-show-anything/56121351#56121351
     */
    private fun onPreviewUpdated(previewOutput: Preview.PreviewOutput) {
        val parent = texture.parent as ViewGroup
        parent.removeView(texture)
        texture.surfaceTexture = previewOutput.surfaceTexture
        parent.addView(texture, 0)
        updateTransform()
    }

    override fun onResult(result: CardOcrResult, frames: List<CardImage>) {
        val number = result.number
        val expiry = result.expiry
        text.text = "${number?.number ?: "____"} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}"
        Log.i("RESULT", "SCANNING ${number?.number ?: "____"} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}")
    }

    override fun onInterimResult(result: CardOcrResult, frame: CardImage) {
        val number = result.number
        val expiry = result.expiry
        if (number != null) {
            text.text = "${number.number} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}"
            Log.i("RESULT", "SCANNING ${number.number} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}")
        }
    }

    override fun onUpdateProcessingRate(overallRate: Rate, instantRate: Rate) {
        val overallFps = if (overallRate.duration != Duration.ZERO) {
            overallRate.amount / overallRate.duration.inSeconds
        } else {
            0.0
        }

        val instantFps = if (instantRate.duration != Duration.ZERO) {
            instantRate.amount / instantRate.duration.inSeconds
        } else {
            0.0
        }

        framerate.text = "FR: avg=$overallFps, inst=$instantFps"
    }
}
