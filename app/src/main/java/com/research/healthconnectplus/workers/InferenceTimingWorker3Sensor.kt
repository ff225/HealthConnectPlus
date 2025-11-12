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

class InferenceTimingWorker3Sensor(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    companion object {
        const val TAG = "InferenceTimingWorker3S"
        const val MODEL_NAME_KEY = "model_name"

        // Dimensioni del modello
        const val WINDOW_SIZE = 32
        const val SAMPLES_PER_WINDOW = 50
        const val NUM_FEATURES = 18 // 6 acc + 6 gyro per 3 sensori

        // Range realistici per i sensori
        const val ACC_RANGE = 20f // ±20 m/s² per accelerometro
        const val GYRO_RANGE = 500f // ±500 deg/s per giroscopio
    }

    private var interpreter: Interpreter? = null
    private val assetManager = ctx.assets
    private val inferenceTimeRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.inferenceTimeRepository

    override suspend fun doWork(): Result {
        val modelName =
            inputData.getString(MODEL_NAME_KEY) ?: "cnn_rightpocket_leftwrist_rightankle.tflite"

        try {
            // Carica il modello
            interpreter = loadModelFile(assetManager, modelName)?.let { Interpreter(it) }

            if (interpreter == null) {
                Log.e(TAG, "Failed to load model: $modelName")
                return Result.failure()
            }

            // Log delle informazioni del modello
            logModelInfo()

            // Genera i dati con la forma corretta [32, 50, 18]
            val inputData = generateRealisticSensorData()

            // Visualizza i dati generati
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

            // Analizza i risultati
            analyzeOutput(output)

            //Salva il tempo di inferenza nel database se necessario
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
        // Crea un array 3D con shape [32, 50, 18]
        return Array(WINDOW_SIZE) { // 32 windows
            Array(SAMPLES_PER_WINDOW) { // 50 samples per window
                FloatArray(NUM_FEATURES) { featureIndex ->
                    when (featureIndex) {
                        // Sensore 1 - Accelerometro (0-2) e Giroscopio (3-5)
                        in 0..2 -> Random.nextFloat() * ACC_RANGE * 2 - ACC_RANGE
                        in 3..5 -> Random.nextFloat() * GYRO_RANGE * 2 - GYRO_RANGE
                        // Sensore 2 - Accelerometro (6-8) e Giroscopio (9-11)
                        in 6..8 -> Random.nextFloat() * ACC_RANGE * 2 - ACC_RANGE
                        in 9..11 -> Random.nextFloat() * GYRO_RANGE * 2 - GYRO_RANGE
                        // Sensore 3 - Accelerometro (12-14) e Giroscopio (15-17)
                        in 12..14 -> Random.nextFloat() * ACC_RANGE * 2 - ACC_RANGE
                        else -> Random.nextFloat() * GYRO_RANGE * 2 - GYRO_RANGE
                    }
                }
            }
        }
    }

    private fun generateRealisticSensorData(): Array<Array<FloatArray>> {
        // Versione realistica che simula movimenti coordinati tra tre sensori
        return Array(WINDOW_SIZE) { windowIndex ->
            // Base values per simulare continuità
            // Sensore 1 (es. polso sinistro)
            val baseAcc1X = Random.nextFloat() * 10f - 5f
            val baseAcc1Y = Random.nextFloat() * 10f - 5f
            val baseAcc1Z = 9.8f + Random.nextFloat() * 2f - 1f
            val baseGyro1X = Random.nextFloat() * 50f - 25f
            val baseGyro1Y = Random.nextFloat() * 50f - 25f
            val baseGyro1Z = Random.nextFloat() * 50f - 25f

            // Sensore 2 (es. polso destro) - correlato ma con differenze
            val correlation12 = 0.6f // Correlazione tra sensore 1 e 2
            val baseAcc2X =
                baseAcc1X * correlation12 + Random.nextFloat() * 5f * (1 - correlation12)
            val baseAcc2Y =
                baseAcc1Y * correlation12 + Random.nextFloat() * 5f * (1 - correlation12)
            val baseAcc2Z = 9.8f + Random.nextFloat() * 2f - 1f
            val baseGyro2X =
                -baseGyro1X * correlation12 + Random.nextFloat() * 30f * (1 - correlation12) // Opposto per movimento speculare
            val baseGyro2Y =
                baseGyro1Y * correlation12 + Random.nextFloat() * 30f * (1 - correlation12)
            val baseGyro2Z =
                -baseGyro1Z * correlation12 + Random.nextFloat() * 30f * (1 - correlation12)

            // Sensore 3 (es. vita/centro) - parzialmente correlato a entrambi
            val correlation13 = 0.5f
            val correlation23 = 0.5f
            val baseAcc3X =
                (baseAcc1X * correlation13 + baseAcc2X * correlation23) / 2 + Random.nextFloat() * 3f - 1.5f
            val baseAcc3Y =
                (baseAcc1Y * correlation13 + baseAcc2Y * correlation23) / 2 + Random.nextFloat() * 3f - 1.5f
            val baseAcc3Z = 9.8f + Random.nextFloat() * 1f - 0.5f // Più stabile
            val baseGyro3X =
                (baseGyro1X + baseGyro2X) / 4 + Random.nextFloat() * 20f - 10f // Media smorzata
            val baseGyro3Y = (baseGyro1Y + baseGyro2Y) / 4 + Random.nextFloat() * 20f - 10f
            val baseGyro3Z = (baseGyro1Z + baseGyro2Z) / 4 + Random.nextFloat() * 20f - 10f

            Array(SAMPLES_PER_WINDOW) { sampleIndex ->
                val timeOffset = sampleIndex / SAMPLES_PER_WINDOW.toFloat()

                floatArrayOf(
                    // Sensore 1 - Accelerometro
                    baseAcc1X + Random.nextFloat() * 2f - 1f + kotlin.math.sin(timeOffset * 2 * Math.PI)
                        .toFloat() * 2f,
                    baseAcc1Y + Random.nextFloat() * 2f - 1f + kotlin.math.cos(timeOffset * 2 * Math.PI)
                        .toFloat() * 2f,
                    baseAcc1Z + Random.nextFloat() * 0.5f - 0.25f,

                    // Sensore 1 - Giroscopio
                    baseGyro1X + Random.nextFloat() * 20f - 10f + kotlin.math.sin(timeOffset * 4 * Math.PI)
                        .toFloat() * 15f,
                    baseGyro1Y + Random.nextFloat() * 20f - 10f + kotlin.math.cos(timeOffset * 4 * Math.PI)
                        .toFloat() * 15f,
                    baseGyro1Z + Random.nextFloat() * 20f - 10f,

                    // Sensore 2 - Accelerometro (movimento speculare/opposto)
                    baseAcc2X + Random.nextFloat() * 2f - 1f + kotlin.math.sin(timeOffset * 2 * Math.PI + Math.PI)
                        .toFloat() * 2f,
                    baseAcc2Y + Random.nextFloat() * 2f - 1f + kotlin.math.cos(timeOffset * 2 * Math.PI)
                        .toFloat() * 2f,
                    baseAcc2Z + Random.nextFloat() * 0.5f - 0.25f,

                    // Sensore 2 - Giroscopio
                    baseGyro2X + Random.nextFloat() * 20f - 10f + kotlin.math.sin(timeOffset * 4 * Math.PI + Math.PI)
                        .toFloat() * 15f,
                    baseGyro2Y + Random.nextFloat() * 20f - 10f + kotlin.math.cos(timeOffset * 4 * Math.PI)
                        .toFloat() * 15f,
                    baseGyro2Z + Random.nextFloat() * 20f - 10f,

                    // Sensore 3 - Accelerometro (movimento più stabile)
                    baseAcc3X + Random.nextFloat() * 1f - 0.5f + kotlin.math.sin(timeOffset * 2 * Math.PI)
                        .toFloat() * 0.5f,
                    baseAcc3Y + Random.nextFloat() * 1f - 0.5f + kotlin.math.cos(timeOffset * 2 * Math.PI)
                        .toFloat() * 0.5f,
                    baseAcc3Z + Random.nextFloat() * 0.3f - 0.15f,

                    // Sensore 3 - Giroscopio
                    baseGyro3X + Random.nextFloat() * 10f - 5f + kotlin.math.sin(timeOffset * 4 * Math.PI)
                        .toFloat() * 5f,
                    baseGyro3Y + Random.nextFloat() * 10f - 5f + kotlin.math.cos(timeOffset * 4 * Math.PI)
                        .toFloat() * 5f,
                    baseGyro3Z + Random.nextFloat() * 10f - 5f
                )
            }
        }
    }

    private fun logGeneratedData(data: Array<Array<FloatArray>>) {
        Log.d(TAG, "=== Generated 3-Sensor Data Sample ===")
        Log.d(TAG, "Full data shape: [${data.size}, ${data[0].size}, ${data[0][0].size}]")

        // Mostra i dati della prima finestra, primi 3 campioni
        Log.d(TAG, "\n--- Window 0 (first 3 samples) ---")
        for (sampleIdx in 0 until minOf(3, data[0].size)) {
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

            // Sensore 3
            Log.d(TAG, "  Sensor 3:")
            Log.d(
                TAG,
                "    Acc: X=${String.format("%6.2f", sample[12])}, Y=${
                    String.format(
                        "%6.2f",
                        sample[13]
                    )
                }, Z=${String.format("%6.2f", sample[14])}"
            )
            Log.d(
                TAG,
                "    Gyro: X=${String.format("%6.2f", sample[15])}, Y=${
                    String.format(
                        "%6.2f",
                        sample[16]
                    )
                }, Z=${String.format("%6.2f", sample[17])}"
            )
        }

        // Statistiche per ogni canale
        Log.d(TAG, "\n--- Statistics across all windows ---")
        val channelNames = arrayOf(
            "S1_AccX", "S1_AccY", "S1_AccZ", "S1_GyroX", "S1_GyroY", "S1_GyroZ",
            "S2_AccX", "S2_AccY", "S2_AccZ", "S2_GyroX", "S2_GyroY", "S2_GyroZ",
            "S3_AccX", "S3_AccY", "S3_AccZ", "S3_GyroX", "S3_GyroY", "S3_GyroZ"
        )

        for (channelIdx in 0 until NUM_FEATURES) {
            val values = mutableListOf<Float>()

            for (window in data) {
                for (sample in window) {
                    values.add(sample[channelIdx])
                }
            }

            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 0f
            val mean = values.average()

            Log.d(
                TAG,
                "${channelNames[channelIdx]}: min=${
                    String.format(
                        "%6.2f",
                        min
                    )
                }, max=${String.format("%6.2f", max)}, mean=${String.format("%6.2f", mean)}"
            )
        }

        // Mostra correlazioni tra i sensori
        Log.d(TAG, "\n--- Sensor Correlations (Window 0, AccX) ---")
        showThreeSensorCorrelation(data[0])
    }

    private fun showThreeSensorCorrelation(windowData: Array<FloatArray>) {
        val sensor1AccX = windowData.map { it[0] }
        val sensor2AccX = windowData.map { it[6] }
        val sensor3AccX = windowData.map { it[12] }

        val corr12 = calculateCorrelation(sensor1AccX, sensor2AccX)
        val corr13 = calculateCorrelation(sensor1AccX, sensor3AccX)
        val corr23 = calculateCorrelation(sensor2AccX, sensor3AccX)

        Log.d(TAG, "Correlation S1-S2 (AccX): ${String.format("%.3f", corr12)}")
        Log.d(TAG, "Correlation S1-S3 (AccX): ${String.format("%.3f", corr13)}")
        Log.d(TAG, "Correlation S2-S3 (AccX): ${String.format("%.3f", corr23)}")
    }

    private fun calculateCorrelation(x: List<Float>, y: List<Float>): Float {
        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - meanX) * (yi - meanY) }
        val denomX = kotlin.math.sqrt(x.sumOf { (it - meanX) * (it - meanX) })
        val denomY = kotlin.math.sqrt(y.sumOf { (it - meanY) * (it - meanY) })

        return if (denomX * denomY > 0) (numerator / (denomX * denomY)).toFloat() else 0f
    }

    private fun analyzeOutput(output: Array<FloatArray>) {
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

        // Distribuzione delle classi
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
            "Model: ${inputData.getString(MODEL_NAME_KEY) ?: "cnn_rightpocket_leftwrist_rightankle.tflite"}"
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
}