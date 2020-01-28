package com.getbouncer.cardscan.base

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


@Throws(IOException::class)
private fun readFileToByteBuffer(
    fileInputStream: FileInputStream,
    startOffset: Long,
    declaredLength: Long
): ByteBuffer = fileInputStream.channel.map(
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
                input ->
            readFileToByteBuffer(
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
        private const val HASH_ALGORITHM = "SHA-256"
    }

    @Throws(IOException::class, HashMismatchException::class, NoSuchAlgorithmException::class)
    @Synchronized
    fun loadModelFromWeb(url: URL, localFileName: String, hash: String): ByteBuffer =
        if (hashMatches(localFileName, hash)) {
            readFileToByteBuffer(localFileName)
        } else {
            downloadFile(url, localFileName)
            if (hashMatches(localFileName, hash)) {
                readFileToByteBuffer(localFileName)
            } else {
                throw HashMismatchException(
                    HASH_ALGORITHM,
                    hash,
                    calculateHash(localFileName)
                )
            }
        }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun hashMatches(localFileName: String, hash: String): Boolean = hash == calculateHash(localFileName)

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun calculateHash(localFileName: String): String? {
        val file = File(context.cacheDir, localFileName)
        return if (file.exists()) {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            FileInputStream(file).use { digest.update(it.readBytes()) }
            digest.digest().joinToString("") { "%02x".format(it) }
        } else {
            null
        }
    }

    private fun readFileToByteBuffer(localFileName: String): ByteBuffer {
        val file = File(context.cacheDir, localFileName)
        return FileInputStream(file).use {
            readFileToByteBuffer(
                it,
                0,
                file.length()
            )
        }
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
