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

class InferenceTimingWorker4Sensor(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    companion object {
        const val TAG = "InferenceTimingWorker4S"
        const val MODEL_NAME_KEY = "model_name"

        // Dimensioni del modello
        const val WINDOW_SIZE = 32
        const val SAMPLES_PER_WINDOW = 50
        const val NUM_FEATURES = 24 // 6 features per 4 sensori

        // Range realistici per i sensori
        const val ACC_RANGE = 20f // ±20 m/s² per accelerometro
        const val GYRO_RANGE = 500f // ±500 deg/s per giroscopio
    }

    private var interpreter: Interpreter? = null
    private val assetManager = ctx.assets
    private val inferenceTimeRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.inferenceTimeRepository

    override suspend fun doWork(): Result {
        val modelName = inputData.getString(MODEL_NAME_KEY)
            ?: "cnn_rightpocket_leftwrist_rightankle_chest.tflite"

        try {
            // Carica il modello
            interpreter = loadModelFile(assetManager, modelName)?.let { Interpreter(it) }

            if (interpreter == null) {
                Log.e(TAG, "Failed to load model: $modelName")
                return Result.failure()
            }

            // Log delle informazioni del modello
            logModelInfo()

            // Genera i dati con la forma corretta [32, 50, 24]
            val inputData = generateRealisticSensorData()

            // Ottieni la forma dell'output dal modello
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.d(TAG, "Actual output shape from model: ${outputShape?.contentToString()}")

            // Crea l'array di output con la forma corretta
            val output = if (outputShape != null && outputShape.size >= 2) {
                Array(outputShape[0]) { FloatArray(outputShape[1]) }
            } else {
                Array(32) { FloatArray(5) } // Default [32, 5]
            }

            val inputShapeStr = "[${inputData.size}, ${inputData[0].size}, ${inputData[0][0].size}]"
            Log.d(TAG, "Input array shape: $inputShapeStr")
            Log.d(
                TAG,
                "Output array shape: [${output.size}, ${if (output.isNotEmpty()) output[0].size else 0}]"
            )

            // Warm-up run
            interpreter?.run(inputData, output)

            // Misura il tempo di inferenza
            val startTime = SystemClock.elapsedRealtimeNanos()
            interpreter?.run(inputData, output)
            val endTime = SystemClock.elapsedRealtimeNanos()

            val totalInferenceTimeMs = (endTime - startTime) / 1_000_000
            val averageTimePerWindowMs = totalInferenceTimeMs / 32.0f

            Log.d(TAG, "=== Inference Time Results ===")
            Log.d(TAG, "Total inference time (32 windows): $totalInferenceTimeMs ms")
            Log.d(TAG, "Average time per window: $averageTimePerWindowMs ms")
            Log.d(TAG, "Model: $modelName")
            Log.d(TAG, "==============================")

            // Salva i risultati nel database

            inferenceTimeRepo.insert(
                InferenceTimeRecord(
                    modelName = modelName,
                    shape = inputShapeStr,
                    totalTimeMs = totalInferenceTimeMs,
                    averageTimeMs = averageTimePerWindowMs,
                    windowCount = WINDOW_SIZE,
                    timestamp = Instant.now().toEpochMilli(),
                    device = Build.MODEL
                )
            )
            /**/

            // Analizza i risultati
            analyzeOutput(output)

            interpreter?.close()
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            interpreter?.close()
            return Result.failure()
        }
    }

    private fun generateRandomSensorData(): Array<Array<FloatArray>> {
        return Array(WINDOW_SIZE) {
            Array(SAMPLES_PER_WINDOW) {
                FloatArray(NUM_FEATURES) { featureIndex ->
                    when (featureIndex % 6) {
                        in 0..2 -> Random.nextFloat() * ACC_RANGE * 2 - ACC_RANGE // Accelerometro
                        else -> Random.nextFloat() * GYRO_RANGE * 2 - GYRO_RANGE // Giroscopio
                    }
                }
            }
        }
    }

    private fun generateRealisticSensorData(): Array<Array<FloatArray>> {
        return Array(WINDOW_SIZE) { windowIndex ->
            // Base values per 4 sensori (es. polso sx, polso dx, caviglia sx, caviglia dx)
            val sensorBases = Array(4) { sensorIdx ->
                val isUpperBody = sensorIdx < 2
                val isLeft = sensorIdx % 2 == 0

                Triple(
                    // Accelerometro base
                    Triple(
                        Random.nextFloat() * (if (isUpperBody) 10f else 15f) - (if (isUpperBody) 5f else 7.5f),
                        Random.nextFloat() * (if (isUpperBody) 10f else 15f) - (if (isUpperBody) 5f else 7.5f),
                        9.8f + Random.nextFloat() * 2f - 1f
                    ),
                    // Giroscopio base
                    Triple(
                        Random.nextFloat() * (if (isUpperBody) 50f else 100f) - (if (isUpperBody) 25f else 50f),
                        Random.nextFloat() * (if (isUpperBody) 50f else 100f) - (if (isUpperBody) 25f else 50f),
                        Random.nextFloat() * (if (isUpperBody) 50f else 100f) - (if (isUpperBody) 25f else 50f)
                    ),
                    // Parametri (isUpperBody, isLeft)
                    Pair(isUpperBody, isLeft)
                )
            }

            Array(SAMPLES_PER_WINDOW) { sampleIndex ->
                val timeOffset = sampleIndex / SAMPLES_PER_WINDOW.toFloat()
                val features = FloatArray(NUM_FEATURES)

                for (sensorIdx in 0..3) {
                    val (accBase, gyroBase, params) = sensorBases[sensorIdx]
                    val (isUpperBody, isLeft) = params
                    val baseIdx = sensorIdx * 6

                    // Phase shift per movimento alternato sinistra/destra
                    val phaseShift = if (isLeft) 0f else Math.PI.toFloat()

                    // Accelerometro
                    features[baseIdx] = accBase.first + Random.nextFloat() * 2f - 1f +
                            kotlin.math.sin(timeOffset * 2 * Math.PI + phaseShift)
                                .toFloat() * (if (isUpperBody) 2f else 4f)
                    features[baseIdx + 1] = accBase.second + Random.nextFloat() * 2f - 1f +
                            kotlin.math.cos(timeOffset * 2 * Math.PI)
                                .toFloat() * (if (isUpperBody) 2f else 4f)
                    features[baseIdx + 2] = accBase.third + Random.nextFloat() * 0.5f - 0.25f

                    // Giroscopio
                    features[baseIdx + 3] = gyroBase.first + Random.nextFloat() * 20f - 10f +
                            kotlin.math.sin(timeOffset * 4 * Math.PI + phaseShift)
                                .toFloat() * (if (isUpperBody) 15f else 30f)
                    features[baseIdx + 4] = gyroBase.second + Random.nextFloat() * 20f - 10f +
                            kotlin.math.cos(timeOffset * 4 * Math.PI)
                                .toFloat() * (if (isUpperBody) 15f else 30f)
                    features[baseIdx + 5] = gyroBase.third + Random.nextFloat() * 20f - 10f
                }

                features
            }
        }
    }

    private fun analyzeOutput(output: Array<FloatArray>) {
        Log.d(TAG, "=== Output Analysis ===")

        val classCount = IntArray(5)
        output.forEach { prediction ->
            val maxIndex = prediction.indices.maxByOrNull { prediction[it] } ?: -1
            if (maxIndex in 0..4) {
                classCount[maxIndex]++
            }
        }

        Log.d(TAG, "Class distribution:")
        val classNames = arrayOf("Downstairs", "Office", "Sitting", "Upstairs", "Walking")
        classCount.forEachIndexed { index, count ->
            Log.d(
                TAG,
                "  ${classNames[index]}: $count windows (${
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
        Log.d(
            TAG,
            "Model: ${inputData.getString(MODEL_NAME_KEY) ?: "cnn_rightpocket_leftwrist_rightankle_chest.tflite"}"
        )

        val numInputs = interpreter?.inputTensorCount ?: 0
        for (i in 0 until numInputs) {
            val inputTensor = interpreter?.getInputTensor(i)
            val inputShape = inputTensor?.shape()
            val inputType = inputTensor?.dataType()
            Log.d(TAG, "Input $i: shape=${inputShape?.contentToString()}, type=$inputType")
        }

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