package com.getbouncer.cardscan.base.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysisConfig
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.getbouncer.cardscan.base.R
import com.getbouncer.cardscan.base.camera.CardImageAnalysisAdapter
import com.getbouncer.cardscan.base.config.IS_DEBUG
import com.getbouncer.cardscan.base.config.MEASURE_TIME
import com.getbouncer.cardscan.base.config.TEST_CARD_NUMBER
import com.getbouncer.cardscan.base.domain.CardOcrResult
import com.getbouncer.cardscan.base.domain.ScanImage
import com.getbouncer.cardscan.base.ml.AggregateResultListener
import com.getbouncer.cardscan.base.ml.CardImageOcrResultAggregator
import com.getbouncer.cardscan.base.ml.MemoryBoundAnalyzerLoop
import com.getbouncer.cardscan.base.ml.Rate
import com.getbouncer.cardscan.base.ml.ResultAggregatorConfig
import com.getbouncer.cardscan.base.ml.models.SSDOcr
import com.getbouncer.cardscan.base.util.CreditCardUtils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.time.ClockMark
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.MonoClock
import kotlin.time.seconds

private const val CAMERA_PERMISSION_REQUEST_CODE = 1200

@ExperimentalCoroutinesApi
@ExperimentalTime
@ExperimentalUnsignedTypes
class CardScanActivity : AppCompatActivity(), AggregateResultListener<ScanImage, CardOcrResult>, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private var lastWrongCard: ClockMark? = null
    private val showWrongDuration = 1.seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            texture.post { startCamera() }
        }
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
        val mainLoopAdapter = launchMainLoop()

        val imageAnalysisConfig = ImageAnalysisConfig.Builder()
            .setTargetResolution(Size(1280, 720))
            .setLensFacing(CameraX.LensFacing.BACK)
            .build()
        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), mainLoopAdapter)

        val previewSize = Size(texture.width, texture.height)
        val previewConfig: PreviewConfig = PreviewConfig.Builder()
            .setLensFacing(CameraX.LensFacing.BACK)
            .setTargetResolution(previewSize)
            .build()
        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener { onPreviewUpdated(it) }

        CameraX.bindToLifecycle(this, preview)
        CameraX.bindToLifecycle(this, imageAnalysis)
    }

    private fun launchMainLoop(): ImageAnalysis.Analyzer {
        val resultAggregatorConfig = ResultAggregatorConfig.Builder()
            .withMaxTotalAggregationTime(2.seconds)
            .withTrackFrameRate(IS_DEBUG && MEASURE_TIME)
            .build()
        val mainLoop = MemoryBoundAnalyzerLoop(
            analyzerFactory = SSDOcr.Factory(this),
            resultHandler = CardImageOcrResultAggregator(
                config = resultAggregatorConfig,
                listener = this,
                requiredCardNumber = TEST_CARD_NUMBER,
                requiredAgreementCount = 10
            ),
            coroutineScope = this
        )
        val previewSize = Size(texture.width, texture.height)
        val cardFinder = viewFinder.getCardFinderRectangle()
        mainLoop.start()
        return CardImageAnalysisAdapter(previewSize, cardFinder, mainLoop)
    }

    private fun launchCompletionLoop(frames: Collection<ScanImage>) {
//        val completionLoop = FiniteAnalyzerLoop(
//            frames = frames,
//            analyzerFactory = SSDOcr.Factory(this),
//            resultHandler = object : ResultHandler<ScanImage, CardOcrResult> {
//                override fun onResult(result: CardOcrResult, data: ScanImage) {
//                    Log.d("BOUNCER", "COMPLETION LOOP HANDLING RESULT")
//                }
//            },
//            coroutineScope = this
//        )
//        completionLoop.start()
//        return completionLoop
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
    }

    override fun onResult(result: CardOcrResult, frames: Map<String, List<ScanImage>>) = runOnUiThread {
        val number = result.number
        val expiry = result.expiry

        if (number != null) {
            cardNumber.visibility = View.VISIBLE
            cardNumber.text = CreditCardUtils.formatNumberForDisplay(number.number)
        }

        if (expiry != null) {
            cardExpiry.visibility = View.VISIBLE
            cardExpiry.text = CreditCardUtils.formatExpiryForDisplay(expiry.day, expiry.month, expiry.year)
        }

        val framesWithNumbers = frames[CardImageOcrResultAggregator.FRAME_TYPE_VALID_NUMBER]
        if (framesWithNumbers != null) {
            launchCompletionLoop(framesWithNumbers)
        }
    }

    override fun onInterimResult(result: CardOcrResult, frame: ScanImage) = runOnUiThread {
        if (IS_DEBUG) {
            debugBitmap.visibility = View.VISIBLE
            debugBitmap.setImageBitmap(frame.ocrImage)
            debugOverlay.visibility = View.VISIBLE
            debugOverlay.setBoxes(result.number?.boxes)
        }

        viewFinder.setState(ViewFinderOverlay.State.FOUND)
    }

    override fun onInvalidResult(result: CardOcrResult, frame: ScanImage, haveSeenValidResult: Boolean) = runOnUiThread {
        val number = result.number

        if (IS_DEBUG) {
            debugBitmap.visibility = View.VISIBLE
            debugBitmap.setImageBitmap(frame.ocrImage)
            debugOverlay.visibility = View.VISIBLE
            debugOverlay.setBoxes(result.number?.boxes)
        }

        if (number != null) {
            cardNumber.visibility = View.VISIBLE
            cardNumber.text = CreditCardUtils.formatNumberForDisplay(number.number)

            lastWrongCard = MonoClock.markNow()
            cardNumber.visibility = View.VISIBLE
            if (viewFinder.getState() == ViewFinderOverlay.State.SCANNING) {
                viewFinder.setState(ViewFinderOverlay.State.WRONG)
            }
        } else {
            val lastWrongCard = this.lastWrongCard
            if (viewFinder.getState() == ViewFinderOverlay.State.WRONG && (lastWrongCard == null || lastWrongCard.elapsedNow() > showWrongDuration)) {
                viewFinder.setState(ViewFinderOverlay.State.SCANNING)
                cardNumber.visibility = View.INVISIBLE
            }
        }
    }

    override fun onUpdateProcessingRate(overallRate: Rate, instantRate: Rate) = runOnUiThread {
        framerate.visibility = View.VISIBLE

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
