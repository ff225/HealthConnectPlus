package com.research.healthconnectplus.workers

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.research.healthconnectplus.HealthConnectApp
import com.research.healthconnectplus.data.InferenceTimeRecord
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.time.Instant
import kotlin.random.Random

class InferenceTimingWorker1Sensor(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    companion object {
        const val TAG = "InferenceTimingWorker"
        const val MODEL_NAME_KEY = "model_name"

        // Dimensioni del modello
        const val WINDOW_SIZE = 32
        const val SAMPLES_PER_WINDOW = 50
        const val NUM_FEATURES = 6 // 3 acc + 3 gyro

        // Range realistici per i sensori
        const val ACC_RANGE = 20f // ±20 m/s² per accelerometro
        const val GYRO_RANGE = 500f // ±500 deg/s per giroscopio
    }

    private var interpreter: Interpreter? = null
    private val assetManager = ctx.assets
    private val inferenceTimeRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.inferenceTimeRepository


    override suspend fun doWork(): Result {
        val modelName = inputData.getString(MODEL_NAME_KEY) ?: "cnn_rightpocket.tflite"

        try {
            // Carica il modello
            interpreter = loadModelFile(assetManager, modelName)?.let { Interpreter(it) }

            if (interpreter == null) {
                Log.e(TAG, "Failed to load model: $modelName")
                return Result.failure()
            }

            // Log delle informazioni del modello
            logModelInfo()

            // Genera i dati con la forma corretta [32, 50, 6]
            val inputData = generateRealisticSensorData()
            val inputShapeStr = "[${inputData.size}, ${inputData[0].size}, ${inputData[0][0].size}]"
            Log.d(InferenceTimingWorker4Sensor.TAG, "Input array shape: $inputShapeStr")


            // Ottieni la forma dell'output dal modello
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.d(TAG, "Actual output shape from model: ${outputShape?.contentToString()}")

            // Crea l'array di output con la forma corretta [32, 5]
            val output = if (outputShape != null && outputShape.size >= 2) {
                Array(outputShape[0]) { FloatArray(outputShape[1]) }
            } else {
                Array(32) { FloatArray(5) }
            }

            Log.d(
                TAG,
                "Input array shape: [${inputData.size}, ${inputData[0].size}, ${inputData[0][0].size}]"
            )
            Log.d(
                TAG,
                "Output array shape: [${output.size}, ${if (output.isNotEmpty()) output[0].size else 0}]"
            )

            // Misura il tempo di inferenza
            val startTime = SystemClock.elapsedRealtimeNanos()
            interpreter?.run(inputData, output)
            val endTime = SystemClock.elapsedRealtimeNanos()

            //val inferenceTimeMs = (endTime - startTime) / 1_000_000


            val totalInferenceTimeMs = (endTime - startTime) / 1_000_000
            val averageTimePerWindowMs = totalInferenceTimeMs / 32.0f

            Log.d(InferenceTimingWorker4Sensor.TAG, "=== Inference Time Results ===")
            Log.d(InferenceTimingWorker4Sensor.TAG, "Total inference time (32 windows): $totalInferenceTimeMs ms")
            Log.d(InferenceTimingWorker4Sensor.TAG, "Average time per window: $averageTimePerWindowMs ms")
            Log.d(InferenceTimingWorker4Sensor.TAG, "Model: $modelName")
            Log.d(InferenceTimingWorker4Sensor.TAG, "==============================")

            // Salva i risultati nel database

            inferenceTimeRepo.insert(
                InferenceTimeRecord(
                    modelName = modelName,
                    shape = inputShapeStr,
                    totalTimeMs = totalInferenceTimeMs,
                    averageTimeMs = averageTimePerWindowMs,
                    windowCount = InferenceTimingWorker4Sensor.WINDOW_SIZE,
                    timestamp = Instant.now().toEpochMilli(),
                    device = Build.MODEL
                )
            )
            Log.d(InferenceTimingWorker2Sensor.TAG, "=== Inference Time Results ===")
            Log.d(
                InferenceTimingWorker2Sensor.TAG,
                "Total inference time (32 windows): $totalInferenceTimeMs ms"
            )
            Log.d(
                InferenceTimingWorker2Sensor.TAG,
                "Average time per window: $averageTimePerWindowMs ms"
            )
            Log.d(InferenceTimingWorker2Sensor.TAG, "Model: $modelName")
            Log.d(InferenceTimingWorker2Sensor.TAG, "==============================")

            //Log.d(TAG, "Inference time: $inferenceTimeMs ms")

            // Analizza i risultati (opzionale, per debug)
            analyzeOutput(output)

            // Salva il tempo di inferenza nel database se necessario
            // inferenceTimeRepo.insert(InferenceTimeRecord(inferenceTimeMs = inferenceTimeMs))

            interpreter?.close()
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            interpreter?.close()
            return Result.failure()
        }
    }

    private fun generateRandomSensorData(): Array<Array<FloatArray>> {
        // Crea un array 3D con shape [32, 50, 6]
        // Nota: NON c'è batch dimension!
        return Array(WINDOW_SIZE) { // 32 windows
            Array(SAMPLES_PER_WINDOW) { // 50 samples per window
                FloatArray(NUM_FEATURES) { featureIndex ->
                    when (featureIndex) {
                        // Accelerometro (indici 0, 1, 2)
                        0, 1, 2 -> Random.nextFloat() * ACC_RANGE * 2 - ACC_RANGE
                        // Giroscopio (indici 3, 4, 5)
                        else -> Random.nextFloat() * GYRO_RANGE * 2 - GYRO_RANGE
                    }
                }
            }
        }
    }

    private fun generateRealisticSensorData(): Array<Array<FloatArray>> {
        // Versione più realistica che simula movimenti continui
        return Array(WINDOW_SIZE) { windowIndex ->
            // Base values per simulare continuità tra finestre
            val baseAccX = Random.nextFloat() * 10f - 5f
            val baseAccY = Random.nextFloat() * 10f - 5f
            val baseAccZ = 9.8f + Random.nextFloat() * 2f - 1f // Gravità + rumore

            val baseGyroX = Random.nextFloat() * 50f - 25f
            val baseGyroY = Random.nextFloat() * 50f - 25f
            val baseGyroZ = Random.nextFloat() * 50f - 25f

            Array(SAMPLES_PER_WINDOW) { sampleIndex ->
                // Aggiungi variazioni temporali per simulare movimento
                val timeOffset = sampleIndex / SAMPLES_PER_WINDOW.toFloat()

                floatArrayOf(
                    // Accelerometro con rumore e variazione temporale
                    baseAccX + Random.nextFloat() * 2f - 1f + kotlin.math.sin(timeOffset * 2 * Math.PI)
                        .toFloat(),
                    baseAccY + Random.nextFloat() * 2f - 1f + kotlin.math.cos(timeOffset * 2 * Math.PI)
                        .toFloat(),
                    baseAccZ + Random.nextFloat() * 0.5f - 0.25f,

                    // Giroscopio con rumore e variazione temporale
                    baseGyroX + Random.nextFloat() * 20f - 10f + kotlin.math.sin(timeOffset * 4 * Math.PI)
                        .toFloat() * 10f,
                    baseGyroY + Random.nextFloat() * 20f - 10f + kotlin.math.cos(timeOffset * 4 * Math.PI)
                        .toFloat() * 10f,
                    baseGyroZ + Random.nextFloat() * 20f - 10f
                )
            }
        }
    }

    private fun analyzeOutput(output: Array<FloatArray>) {
        // Analizza alcune predizioni per debug
        Log.d(TAG, "=== Output Analysis ===")

        // Mostra le prime 3 predizioni
        for (i in 0 until minOf(3, output.size)) {
            val prediction = output[i]
            val maxIndex = prediction.indices.maxByOrNull { prediction[it] } ?: -1
            val className = when (maxIndex) {
                0 -> "Downstairs"
                1 -> "Office"
                2 -> "Sitting"
                3 -> "Upstairs"
                4 -> "Walking"
                else -> "Unknown"
            }
            Log.d(
                TAG,
                "Window $i - Predicted: $className (index $maxIndex) with confidence ${prediction.maxOrNull()}"
            )
        }

        // Calcola la distribuzione delle classi predette
        val classCount = IntArray(5)
        output.forEach { prediction ->
            val maxIndex = prediction.indices.maxByOrNull { prediction[it] } ?: -1
            if (maxIndex in 0..4) {
                classCount[maxIndex]++
            }
        }

        Log.d(TAG, "Class distribution across ${output.size} windows:")
        classCount.forEachIndexed { index, count ->
            val className = when (index) {
                0 -> "Downstairs"
                1 -> "Office"
                2 -> "Sitting"
                3 -> "Upstairs"
                4 -> "Walking"
                else -> "Unknown"
            }
            Log.d(
                TAG,
                "  $className: $count windows (${
                    String.format(
                        "%.1f",
                        count * 100.0 / output.size
                    )
                }%)"
            )
        }
        Log.d(TAG, "======================")
    }

    private fun logModelInfo() {
        Log.d(TAG, "=== Model Information ===")
        Log.d(TAG, "Model: ${inputData.getString(MODEL_NAME_KEY) ?: "cnn_rightpocket.tflite"}")

        Log.d(TAG, "Model Input Info:")
        val numInputs = interpreter?.inputTensorCount ?: 0
        for (i in 0 until numInputs) {
            val inputTensor = interpreter?.getInputTensor(i)
            val inputShape = inputTensor?.shape()
            val inputType = inputTensor?.dataType()
            Log.d(TAG, "Input $i: shape=${inputShape?.contentToString()}, type=$inputType")
        }

        Log.d(TAG, "Model Output Info:")
        val numOutputs = interpreter?.outputTensorCount ?: 0
        for (i in 0 until numOutputs) {
            val outputTensor = interpreter?.getOutputTensor(i)
            val outputShape = outputTensor?.shape()
            val outputType = outputTensor?.dataType()
            Log.d(TAG, "Output $i: shape=${outputShape?.contentToString()}, type=$outputType")
        }
        Log.d(TAG, "========================")
    }

    private fun loadModelFile(assets: AssetManager, fileName: String): MappedByteBuffer? {
        return try {
            val fileDescriptor = assets.openFd(fileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength

            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}