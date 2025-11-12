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

class InferenceTimingWorker2Sensor(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    companion object {
        const val TAG = "InferenceTimingWorker2S"
        const val MODEL_NAME_KEY = "model_name"

        // Dimensioni del modello
        const val WINDOW_SIZE = 32
        const val SAMPLES_PER_WINDOW = 50
        const val NUM_FEATURES = 12 // 6 acc + 6 gyro (probabilmente 2 sensori)

        // Range realistici per i sensori
        const val ACC_RANGE = 20f // ±20 m/s² per accelerometro
        const val GYRO_RANGE = 500f // ±500 deg/s per giroscopio
    }

    private var interpreter: Interpreter? = null
    private val assetManager = ctx.assets
    private val inferenceTimeRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.inferenceTimeRepository


    override suspend fun doWork(): Result {
        val modelName = inputData.getString(MODEL_NAME_KEY) ?: "cnn_rightpocket_leftwrist.tflite"

        try {
            // Carica il modello
            interpreter = loadModelFile(assetManager, modelName)?.let { Interpreter(it) }

            if (interpreter == null) {
                Log.e(TAG, "Failed to load model: $modelName")
                return Result.failure()
            }

            // Log delle informazioni del modello
            logModelInfo()

            // Genera i dati con la forma corretta [32, 50, 12]
            val inputData = generateRealisticSensorData()
            logGeneratedData(inputData)
            val inputShapeStr = "[${inputData.size}, ${inputData[0].size}, ${inputData[0][0].size}]"
            Log.d(InferenceTimingWorker4Sensor.TAG, "Input array shape: $inputShapeStr")
            // Ottieni la forma dell'output dal modello
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.d(TAG, "Actual output shape from model: ${outputShape?.contentToString()}")

            // Crea l'array di output con la forma corretta
            val output = if (outputShape != null && outputShape.size >= 2) {
                Array(outputShape[0]) { FloatArray(outputShape[1]) }
            } else {
                Array(32) { FloatArray(5) } // Default [32, 5]
            }

            Log.d(
                TAG,
                "Input array shape: [${inputData.size}, ${inputData[0].size}, ${inputData[0][0].size}]"
            )
            Log.d(
                TAG,
                "Output array shape: [${output.size}, ${if (output.isNotEmpty()) output[0].size else 0}]"
            )

            // Warm-up run (opzionale ma consigliato)
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

            // Analizza i risultati (opzionale, per debug)
            analyzeOutput(output)

            // Salva il tempo di inferenza nel database se necessario
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

            interpreter?.close()
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            interpreter?.close()
            return Result.failure()
        }
    }

    private fun generateRandomSensorData(): Array<Array<FloatArray>> {
        // Crea un array 3D con shape [32, 50, 12]
        return Array(WINDOW_SIZE) { // 32 windows
            Array(SAMPLES_PER_WINDOW) { // 50 samples per window
                FloatArray(NUM_FEATURES) { featureIndex ->
                    when (featureIndex) {
                        // Primo sensore - Accelerometro (indici 0, 1, 2)
                        0, 1, 2 -> Random.nextFloat() * ACC_RANGE * 2 - ACC_RANGE
                        // Primo sensore - Giroscopio (indici 3, 4, 5)
                        3, 4, 5 -> Random.nextFloat() * GYRO_RANGE * 2 - GYRO_RANGE
                        // Secondo sensore - Accelerometro (indici 6, 7, 8)
                        6, 7, 8 -> Random.nextFloat() * ACC_RANGE * 2 - ACC_RANGE
                        // Secondo sensore - Giroscopio (indici 9, 10, 11)
                        else -> Random.nextFloat() * GYRO_RANGE * 2 - GYRO_RANGE
                    }
                }
            }
        }
    }

    private fun generateRealisticSensorData(): Array<Array<FloatArray>> {
        // Versione più realistica che simula movimenti coordinati tra due sensori
        return Array(WINDOW_SIZE) { windowIndex ->
            // Base values per simulare continuità
            // Sensore 1
            val baseAcc1X = Random.nextFloat() * 10f - 5f
            val baseAcc1Y = Random.nextFloat() * 10f - 5f
            val baseAcc1Z = 9.8f + Random.nextFloat() * 2f - 1f
            val baseGyro1X = Random.nextFloat() * 50f - 25f
            val baseGyro1Y = Random.nextFloat() * 50f - 25f
            val baseGyro1Z = Random.nextFloat() * 50f - 25f

            // Sensore 2 - correlato al primo ma con alcune differenze
            val correlation = 0.7f // Quanto sono correlati i due sensori
            val baseAcc2X = baseAcc1X * correlation + Random.nextFloat() * 5f * (1 - correlation)
            val baseAcc2Y = baseAcc1Y * correlation + Random.nextFloat() * 5f * (1 - correlation)
            val baseAcc2Z = 9.8f + Random.nextFloat() * 2f - 1f
            val baseGyro2X = baseGyro1X * correlation + Random.nextFloat() * 30f * (1 - correlation)
            val baseGyro2Y = baseGyro1Y * correlation + Random.nextFloat() * 30f * (1 - correlation)
            val baseGyro2Z = baseGyro1Z * correlation + Random.nextFloat() * 30f * (1 - correlation)

            Array(SAMPLES_PER_WINDOW) { sampleIndex ->
                val timeOffset = sampleIndex / SAMPLES_PER_WINDOW.toFloat()

                floatArrayOf(
                    // Sensore 1 - Accelerometro
                    baseAcc1X + Random.nextFloat() * 2f - 1f + kotlin.math.sin(timeOffset * 2 * Math.PI)
                        .toFloat(),
                    baseAcc1Y + Random.nextFloat() * 2f - 1f + kotlin.math.cos(timeOffset * 2 * Math.PI)
                        .toFloat(),
                    baseAcc1Z + Random.nextFloat() * 0.5f - 0.25f,

                    // Sensore 1 - Giroscopio
                    baseGyro1X + Random.nextFloat() * 20f - 10f + kotlin.math.sin(timeOffset * 4 * Math.PI)
                        .toFloat() * 10f,
                    baseGyro1Y + Random.nextFloat() * 20f - 10f + kotlin.math.cos(timeOffset * 4 * Math.PI)
                        .toFloat() * 10f,
                    baseGyro1Z + Random.nextFloat() * 20f - 10f,

                    // Sensore 2 - Accelerometro
                    baseAcc2X + Random.nextFloat() * 2f - 1f + kotlin.math.sin(timeOffset * 2 * Math.PI + 0.5)
                        .toFloat(),
                    baseAcc2Y + Random.nextFloat() * 2f - 1f + kotlin.math.cos(timeOffset * 2 * Math.PI + 0.5)
                        .toFloat(),
                    baseAcc2Z + Random.nextFloat() * 0.5f - 0.25f,

                    // Sensore 2 - Giroscopio
                    baseGyro2X + Random.nextFloat() * 20f - 10f + kotlin.math.sin(timeOffset * 4 * Math.PI + 0.5)
                        .toFloat() * 10f,
                    baseGyro2Y + Random.nextFloat() * 20f - 10f + kotlin.math.cos(timeOffset * 4 * Math.PI + 0.5)
                        .toFloat() * 10f,
                    baseGyro2Z + Random.nextFloat() * 20f - 10f
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
        Log.d(
            TAG,
            "Model: ${inputData.getString(MODEL_NAME_KEY) ?: "cnn_rightpocket_leftwrist.tflite"}"
        )

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

    private fun logGeneratedData(data: Array<Array<FloatArray>>) {
        Log.d(TAG, "=== Generated Sensor Data Sample ===")
        Log.d(TAG, "Full data shape: [${data.size}, ${data[0].size}, ${data[0][0].size}]")

        // Mostra i dati della prima finestra, primi 5 campioni
        Log.d(TAG, "\n--- Window 0 (first 5 samples) ---")
        for (sampleIdx in 0 until minOf(5, data[0].size)) {
            Log.d(TAG, "Sample $sampleIdx:")
            val sample = data[0][sampleIdx]

            // Sensore 1
            Log.d(TAG, "  Sensor 1:")
            Log.d(
                TAG,
                "    Acc: X=${String.format("%6.2f", sample[0])}, Y=${
                    String.format(
                        "%6.2f",
                        sample[1]
                    )
                }, Z=${String.format("%6.2f", sample[2])}"
            )
            Log.d(
                TAG,
                "    Gyro: X=${String.format("%6.2f", sample[3])}, Y=${
                    String.format(
                        "%6.2f",
                        sample[4]
                    )
                }, Z=${String.format("%6.2f", sample[5])}"
            )

            // Sensore 2
            Log.d(TAG, "  Sensor 2:")
            Log.d(
                TAG,
                "    Acc: X=${String.format("%6.2f", sample[6])}, Y=${
                    String.format(
                        "%6.2f",
                        sample[7]
                    )
                }, Z=${String.format("%6.2f", sample[8])}"
            )
            Log.d(
                TAG,
                "    Gyro: X=${String.format("%6.2f", sample[9])}, Y=${
                    String.format(
                        "%6.2f",
                        sample[10]
                    )
                }, Z=${String.format("%6.2f", sample[11])}"
            )
        }

        // Statistiche per ogni canale
        Log.d(TAG, "\n--- Statistics across all windows ---")
        val channelNames = arrayOf(
            "S1_AccX", "S1_AccY", "S1_AccZ", "S1_GyroX", "S1_GyroY", "S1_GyroZ",
            "S2_AccX", "S2_AccY", "S2_AccZ", "S2_GyroX", "S2_GyroY", "S2_GyroZ"
        )

        for (channelIdx in 0 until NUM_FEATURES) {
            val values = mutableListOf<Float>()

            // Raccoglie tutti i valori per questo canale
            for (window in data) {
                for (sample in window) {
                    values.add(sample[channelIdx])
                }
            }

            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 0f
            val mean = values.average()
            val std = calculateStandardDeviation(values)

            Log.d(
                TAG,
                "${channelNames[channelIdx]}: min=${
                    String.format(
                        "%6.2f",
                        min
                    )
                }, max=${String.format("%6.2f", max)}, mean=${
                    String.format(
                        "%6.2f",
                        mean
                    )
                }, std=${String.format("%6.2f", std)}"
            )
        }

        // Visualizza un "plot" ASCII della prima finestra per il primo canale (Acc X del sensore 1)
        Log.d(TAG, "\n--- ASCII Plot: Window 0, Sensor 1 Acc X ---")
        plotAscii(data[0].map { it[0] }, "S1_AccX")

        // Mostra la correlazione tra i due sensori
        Log.d(TAG, "\n--- Correlation between sensors (Window 0) ---")
        showSensorCorrelation(data[0])
    }

    private fun calculateStandardDeviation(values: List<Float>): Float {
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }

    private fun plotAscii(values: List<Float>, label: String) {
        val width = 50
        val height = 10

        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 0f
        val range = max - min

        Log.d(TAG, "$label range: [$min, $max]")

        // Crea il plot
        val plot = Array(height) { CharArray(width) { ' ' } }

        values.forEachIndexed { index, value ->
            val x = (index * width / values.size).coerceIn(0, width - 1)
            val y = (((value - min) / range) * (height - 1)).toInt().coerceIn(0, height - 1)
            plot[height - 1 - y][x] = '*'
        }

        // Stampa il plot
        plot.forEach { row ->
            Log.d(TAG, "  |${String(row)}|")
        }
        Log.d(TAG, "  ${"-".repeat(width + 2)}")
    }

    private fun showSensorCorrelation(windowData: Array<FloatArray>) {
        // Calcola correlazione semplice tra accelerometri X dei due sensori
        val sensor1AccX = windowData.map { it[0] }
        val sensor2AccX = windowData.map { it[6] }

        val correlation = calculateCorrelation(sensor1AccX, sensor2AccX)
        Log.d(TAG, "Correlation S1_AccX vs S2_AccX: ${String.format("%.3f", correlation)}")

        // Mostra differenze medie
        val avgDiffAccX =
            sensor1AccX.zip(sensor2AccX).map { kotlin.math.abs(it.first - it.second) }.average()
        Log.d(TAG, "Average absolute difference AccX: ${String.format("%.3f", avgDiffAccX)}")
    }

    private fun calculateCorrelation(x: List<Float>, y: List<Float>): Float {
        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - meanX) * (yi - meanY) }
        val denomX = kotlin.math.sqrt(x.sumOf { (it - meanX) * (it - meanX) })
        val denomY = kotlin.math.sqrt(y.sumOf { (it - meanY) * (it - meanY) })

        return (numerator / (denomX * denomY)).toFloat()
    }
}