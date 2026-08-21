package com.app.faceattendance.data.ml

import android.content.Context
import android.graphics.Bitmap
import com.app.faceattendance.data.local.UserEntity
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

data class MatchResult(val user: UserEntity, val similarity: Float)

class FaceNetModel(context: Context) : AutoCloseable {

    companion object {
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 128.0f
    }

    private var interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null
    private val enrolledUsers = ConcurrentHashMap<String, Pair<UserEntity, FloatArray>>()

    init {
        val options = Interpreter.Options().apply {
            val compat = CompatibilityList()
            if (compat.isDelegateSupportedOnThisDevice) {
                gpuDelegate = GpuDelegate(compat.bestOptionsForThisDevice)
                addDelegate(gpuDelegate)
            } else {
                setNumThreads(4)
            }
        }
        interpreter = Interpreter(loadModel(context, "mobile_facenet.tflite"), options)
    }

    fun loadUsers(users: List<UserEntity>) {
        enrolledUsers.clear()
        users.forEach { user ->
            val floats = user.embedding.split(",").map { it.toFloat() }.toFloatArray()
            enrolledUsers[user.id] = Pair(user, floats)
        }
    }

    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val resized = if (faceBitmap.width != INPUT_SIZE || faceBitmap.height != INPUT_SIZE) {
            Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        } else faceBitmap

        val byteBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            byteBuffer.putFloat(((pixel shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            byteBuffer.putFloat(((pixel shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            byteBuffer.putFloat(((pixel and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        }

        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        interpreter.run(byteBuffer, output)
        return l2Normalize(output[0])
    }

    fun findMatch(embedding: FloatArray, threshold: Float = 0.72f): MatchResult? {
        var bestMatch: UserEntity? = null
        var highestScore = -1.0f

        for ((_, pair) in enrolledUsers) {
            var dot = 0.0f
            for (i in embedding.indices) {
                dot += embedding[i] * pair.second[i]
            }
            if (dot > highestScore) {
                highestScore = dot
                bestMatch = pair.first
            }
        }

        return if (bestMatch != null && highestScore >= threshold) {
            MatchResult(bestMatch, highestScore)
        } else null
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum.toDouble()).toFloat()
        if (norm == 0f) return v
        return FloatArray(v.size) { i -> v[i] / norm }
    }

    private fun loadModel(context: Context, filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }
}
