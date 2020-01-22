package com.getbouncer.cardscan.base.ml

import android.content.Context
import android.util.Size
import com.getbouncer.cardscan.base.util.Timer
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.time.ExperimentalTime

/**
 * A TensorFlowLite analyzer defines a [MappedByteBuffer] and [Interpreter.Options] to construct a
 * TF interpreter. This interpreter can be used in the analyzer's [analyze] method.
 */
@ExperimentalTime
abstract class MLTensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput> : Analyzer<Input, Output> {

    abstract val logTag: String

    abstract val trainedImageSize: Size

    abstract val tfModel: MappedByteBuffer

    abstract val tfOptions: Interpreter.Options

    abstract fun buildEmptyMLOutput(): MLOutput

    abstract fun interpretMLOutput(data: Input, mlOutput: MLOutput): Output

    private val tfInterpreter: Interpreter by lazy { Interpreter(tfModel, tfOptions) }

    private val loggingTransformTimer by lazy { Timer.newInstance(logTag, "transform ${this::class.java.simpleName}") }
    private val loggingMLOutputTimer by lazy { Timer.newInstance(logTag, "ml_output ${this::class.java.simpleName}") }
    private val loggingAnalysisTimer by lazy { Timer.newInstance(logTag, "analyze ${this::class.java.simpleName}") }
    private val loggingInterpretTimer by lazy { Timer.newInstance(logTag, "interpret ${this::class.java.simpleName}") }

    abstract fun transformData(data: Input): MLInput

    abstract fun executeInterpreter(tfInterpreter: Interpreter, data: MLInput, mlOutput: MLOutput)

    override fun analyze(data: Input): Output {
        val mlInput = loggingTransformTimer.measure {
            transformData(data)
        }

        val mlOutput = loggingMLOutputTimer.measure {
            buildEmptyMLOutput()
        }

        loggingAnalysisTimer.measure {
            executeInterpreter(tfInterpreter, mlInput, mlOutput)
        }

        return loggingInterpretTimer.measure {
            interpretMLOutput(data, mlOutput)
        }
    }

    fun close() = tfInterpreter.close()
}

/**
 * A TensorFlowLite resource model is a TF model that is defined by a local resource within the
 * android package. In this case, models are stored in `res/raw`.
 */
@ExperimentalTime
abstract class MLTFLResourceModel<Input, MLInput, Output, MLOutput>(
    private val factory: MLResourceModelFactory
) : MLTensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput>() {

    abstract val modelFileResource: Int

    override val tfModel: MappedByteBuffer by lazy { loadModelFile() }

    @Throws(IOException::class)
    fun loadModelFile() = factory.loadModelFromResource(modelFileResource)
}

/**
 * A TensorFlowLite downloaded model is a TF model that is downloaded from a URL specified in the
 * factory.
 */
@ExperimentalTime
abstract class MLTFLDownloadedModel<Input, MLInput, Output, MLOutput>(private val factory: MLDownloadedModelFactory)
    : MLTensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput>() {

    abstract val url: URL

    abstract val localFileName: String

    abstract val sha256: String

    fun warmUp() = tfModel.apply { }

    override val tfModel: MappedByteBuffer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        factory.loadModelFromWeb(url, localFileName, sha256)
    }
}

@Throws(IOException::class)
private fun readFileToMappedByteBuffer(
    fileInputStream: FileInputStream,
    startOffset: Long,
    declaredLength: Long
): MappedByteBuffer = fileInputStream.channel.map(
    FileChannel.MapMode.READ_ONLY,
    startOffset,
    declaredLength
)

/**
 * A factory for creating [MappedByteBuffer] objects from an android resource. This is used by the
 * [MLTFLResourceModel] to create TF models.
 */
class MLResourceModelFactory(private val context: Context) {

    @Throws(IOException::class)
    fun loadModelFromResource(resource: Int): MappedByteBuffer =
        context.resources.openRawResourceFd(resource).use {
            fileDescriptor -> FileInputStream(fileDescriptor.fileDescriptor).use {
                input -> readFileToMappedByteBuffer(
                    input,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
}

/**
 * A factory for creating [MappedByteBuffer] objects from files downloaded from the web. This is
 * used by the [MLTFLDownloadedModel] to create TF models.
 */
class MLDownloadedModelFactory(private val context: Context) {

    companion object {
        private const val SHA256_ALGORITHM = "SHA-256"
    }

    @Throws(IOException::class)
    fun loadModelFromWeb(url: URL, localFileName: String, sha256: String): MappedByteBuffer =
        if (hashMatches(localFileName, sha256)) {
            readFileToMappedByteBuffer(localFileName)
        } else {
            downloadFile(url, localFileName)
            readFileToMappedByteBuffer(localFileName)
        }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun hashMatches(localFileName: String, sha256: String): Boolean {
        val file = File(context.cacheDir, localFileName)
        val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
        FileInputStream(file).use { digest.update(it.readBytes()) }
        return sha256 == digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readFileToMappedByteBuffer(localFileName: String): MappedByteBuffer {
        val file = File(context.cacheDir, localFileName)
        return FileInputStream(file).use { readFileToMappedByteBuffer(it, 0, file.length()) }
    }

    @Throws(IOException::class)
    private fun downloadFile(url: URL, localFileName: String) {
        val urlConnection = url.openConnection()
        val outputFile = File(context.cacheDir, localFileName)

        if (outputFile.exists()) {
            if (!outputFile.delete()) {
                return
            }
        }

        if (!outputFile.createNewFile()) {
            return
        }

        readStreamToFile(urlConnection.getInputStream(), outputFile)
    }

    @Throws(IOException::class)
    private fun readStreamToFile(stream: InputStream, file: File) =
        FileOutputStream(file).use { it.write(stream.readBytes()) }
}