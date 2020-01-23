package com.getbouncer.cardscan.base

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysisConfig
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.getbouncer.cardscan.base.camera.CardImageAnalysisAdapter
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.ml.AggregateResultListener
import com.getbouncer.cardscan.base.ml.CardImageOcrResultAggregator
import com.getbouncer.cardscan.base.ml.MemoryBoundAnalyzerLoop
import com.getbouncer.cardscan.base.ml.Rate
import com.getbouncer.cardscan.base.ml.ResultAggregatorConfig
import com.getbouncer.cardscan.base.ml.models.SSDOcrModel
import com.getbouncer.cardscan.base.util.calculateCardPreviewRect
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private const val CAMERA_PERMISSION_REQUEST_CODE = 1200

@ExperimentalCoroutinesApi
@ExperimentalTime
@ExperimentalUnsignedTypes
class CardScanActivity : AppCompatActivity(), AggregateResultListener<ScanImage, CardOcrResult>, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
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
            CAMERA_PERMISSION_REQUEST_CODE -> texture.post { startCamera() }
        }
    }

    private fun startCamera() {
        val resultAggregatorConfig = ResultAggregatorConfig.Builder()
            .withMaxTotalAggregationTime(10.seconds)
            .withTrackFrameRate(true)
            .build()
        val mainLoop = MemoryBoundAnalyzerLoop(
            analyzerFactory = SSDOcrModel.Factory(this),
            resultHandler = CardImageOcrResultAggregator(resultAggregatorConfig, this, 5),
            coroutineScope = this
        )
        val previewSize = Size(texture.width, texture.height)
        val previewCard = calculateCardPreviewRect(previewSize)
        val mainLoopAdapter = CardImageAnalysisAdapter(previewSize, previewCard, mainLoop)
        mainLoop.start()

        val imageAnalysisConfig = ImageAnalysisConfig.Builder()
            .setLensFacing(CameraX.LensFacing.BACK)
            .build()
        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), mainLoopAdapter)

        val previewConfig: PreviewConfig = PreviewConfig.Builder()
            .setLensFacing(CameraX.LensFacing.BACK)
            .setTargetResolution(previewSize)
            .build()
        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener { onPreviewUpdated(it) }

        CameraX.bindToLifecycle(this, preview)
        CameraX.bindToLifecycle(this, imageAnalysis)
    }

    private fun updateTransform() {
//        val matrix = Matrix()
//        val centerX = texture.width / 2f
//        val centerY = texture.height / 2f
//
//        val rotationDegrees = when (texture.display.rotation) {
//            Surface.ROTATION_0 -> 0
//            Surface.ROTATION_90 -> 90
//            Surface.ROTATION_180 -> 180
//            Surface.ROTATION_270 -> 270
//            else -> return
//        }
//
//        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
//        texture.setTransform(matrix)

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

    override fun onResult(result: CardOcrResult, frames: Map<String, List<ScanImage>>) {
        val number = result.number
        val expiry = result.expiry
        text.text = "${number?.number ?: "____"} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}"
        Log.i("RESULT", "SCANNING ${number?.number ?: "____"} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}")
    }

    override fun onInterimResult(result: CardOcrResult, frame: ScanImage) {
        val number = result.number
        val expiry = result.expiry
        debug_bitmap.setImageBitmap(frame.ocrImage)
        if (number != null) {
            text.text = "${number.number} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}"
            Log.i("RESULT", "SCANNING ${number.number} - ${expiry?.day ?: "00"}/${expiry?.month ?: "00"}/${expiry?.year ?: "00"}")
        }
    }

    override fun onInvalidResult(result: CardOcrResult, frame: ScanImage, haveSeenValidResult: Boolean) {
        debug_bitmap.setImageBitmap(frame.ocrImage)
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
