package com.getbouncer.cardscan.base.ml

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


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
 * A factory for creating [ByteBuffer] objects from an android resource.
 */
class ResourceLoader(private val context: Context) {

    @Throws(IOException::class)
    fun loadModelFromResource(resource: Int): ByteBuffer =
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
 * A factory for creating [ByteBuffer] objects from files downloaded from the web.
 */
class WebLoader(private val context: Context) {

    companion object {
        private const val SHA256_ALGORITHM = "SHA-256"
    }

    @Throws(IOException::class)
    @Synchronized
    fun loadModelFromWeb(url: URL, localFileName: String, sha256: String): ByteBuffer =
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
