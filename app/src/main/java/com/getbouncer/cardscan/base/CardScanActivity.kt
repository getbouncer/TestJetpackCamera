package com.getbouncer.cardscan.base

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.image.crop
import com.getbouncer.cardscan.base.ml.CardResultListener
import com.getbouncer.cardscan.base.ml.MLCardResultManager
import com.getbouncer.cardscan.base.ml.MLCardResultManagerConfig
import com.getbouncer.cardscan.base.ml.MLExecutorFactory
import com.getbouncer.cardscan.base.ml.models.MockCpuModel
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import kotlin.math.round

private const val CAMERA_PERMISSION_REQUEST = 1200

class CardScanActivity : AppCompatActivity(), CardResultListener<ByteBuffer> {

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
        val mlResultManagerConfig = MLCardResultManagerConfig.Builder().build()
        val mlResultManager = MLCardResultManager(mlResultManagerConfig, this)
        val mlModel = MockCpuModel(mlResultManager)
        val mlExecutorFactory = MLExecutorFactory(mlModel)

        val imageAnalysisConfig = ImageAnalysisConfig.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)
        imageAnalysis.setAnalyzer(mlExecutorFactory.getExecutor(), mlModel)

        val previewConfig: PreviewConfig = PreviewConfig.Builder()
            .setLensFacing(CameraX.LensFacing.BACK)
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

    override fun onCardResult(result: CardOcrResult, frames: List<Pair<ByteBuffer, Int>>) {
        val number = result.number
        val expiry = result.expiry
        text.text = (number?.number ?: "____") + " - " + (expiry?.day ?: "0") + "/" + (expiry?.month ?: "0") + "/" + (expiry?.year ?: "0")
    }

    override fun onInterimCardResult(result: CardOcrResult, frame: ByteBuffer?) {
        val number = result.number
        val expiry = result.expiry

//        if (frame != null) {
//            frame.crop()
//        }

        text.text = "SCANNING " + (number?.number ?: "____") + " - " + (expiry?.day ?: "0") + "/" + (expiry?.month ?: "0") + "/" + (expiry?.year ?: "0")
    }

    override fun onUpdateProcessingRate(
        avgFramesPerSecond: Double,
        currentFramesPerSecond: Double
    ) {
        framerate.text = "FR: avg=" + round(avgFramesPerSecond) + ", cur=" + round(currentFramesPerSecond)
    }
}
