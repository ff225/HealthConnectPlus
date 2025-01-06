package com.research.healthconnectplus.classifier

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.research.healthconnectplus.HealthConnectApp
import com.research.healthconnectplus.data.MovesenseRecord
import com.research.healthconnectplus.data.PredictionRecord
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.pow
import kotlin.math.sqrt

class Classifier(ctx: Context, workParams: WorkerParameters) : CoroutineWorker(ctx, workParams) {
    private var interpreter: Interpreter? = null

    private val assetManager = ctx.assets
    private val movesenseRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.movesenseRepository

    private val predictionRepository =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.predictionRepository

    private lateinit var movesenseRecord: List<MovesenseRecord>


    override suspend fun doWork(): Result {

        // TODO remove hardcoded model name
        interpreter = loadModelFile(assetManager, "model.tflite")?.let { Interpreter(it) }

        movesenseRecord = movesenseRepo.getUnprocessedRecords()


        // create a list of magnitude acc
        val magnitudeAcc = movesenseRecord.map {
            val xAcc = it.xAcc.toFloat()
            val yAcc = it.yAcc.toFloat()
            val zAcc = it.zAcc.toFloat()

            val magnitudeAcc = sqrt(
                xAcc.toDouble().pow(2.0) + yAcc.toDouble().pow(2.0) + zAcc.toDouble().pow(2.0)
            ).toFloat()
            magnitudeAcc
        }

        Log.d("Classifier", "magnitudeAcc: ${magnitudeAcc}")

        // list of magnitude gyro
        val magnitudeGyro = movesenseRecord.map {
            val xGyro = it.xGyro.toFloat()
            val yGyro = it.yGyro.toFloat()
            val zGyro = it.zGyro.toFloat()

            val magnitudeGyro = sqrt(
                xGyro.toDouble().pow(2.0) + yGyro.toDouble().pow(2.0) + zGyro.toDouble().pow(2.0)
            ).toFloat()
            magnitudeGyro
        }

        Log.d("Classifier", "magnitudeAcc: ${magnitudeGyro}")

        val maxMagnitudeAcc = magnitudeAcc.maxOrNull()
        val minMagnitudeAcc = magnitudeAcc.minOrNull()
        val maxMagnitudeGyro = magnitudeGyro.maxOrNull()
        val minMagnitudeGyro = magnitudeGyro.minOrNull()
        val meanMagnitudeAcc = magnitudeAcc.average()
        val meanMagnitudeGyro = magnitudeGyro.average()
        val medianMagnitudeAcc = magnitudeAcc.sorted()[magnitudeAcc.size / 2]
        val medianMagnitudeGyro = magnitudeGyro.sorted()[magnitudeGyro.size / 2]
        val stdMagnitudeAcc = calculateSD(magnitudeAcc)
        val stdMagnitudeGyro = calculateSD(magnitudeGyro)


        val numInputs = interpreter!!.inputTensorCount


        for (i in 0 until numInputs) {
            val inputTensor = interpreter?.getInputTensor(i)
            val inputShape = inputTensor?.shape()
            val inputType = inputTensor?.dataType()
            println("Input $i: shape=${inputShape.contentToString()}, type=$inputType")
        }

        val numOutputs = interpreter!!.outputTensorCount


        for (i in 0 until numOutputs) {
            val outputTensor = interpreter?.getOutputTensor(i)
            val outputShape = outputTensor?.shape()
            val outputType = outputTensor?.dataType()
            println("Output $i: shape=${outputShape.contentToString()}, type=$outputType")
        }

        println(interpreter?.getOutputTensor(0)?.shape().contentToString())

        val input =
            Array(1) {
                floatArrayOf(
                    maxMagnitudeAcc!!,
                    minMagnitudeAcc!!,
                    meanMagnitudeAcc.toFloat(),
                    medianMagnitudeAcc,
                    stdMagnitudeAcc.toFloat(),
                    maxMagnitudeGyro!!,
                    minMagnitudeGyro!!,
                    meanMagnitudeGyro.toFloat(),
                    medianMagnitudeGyro,
                    stdMagnitudeGyro.toFloat()
                )

                /*floatArrayOf(
                    10.119906126046823f,
                    10.033319490577382f,
                    10.072553040379733f,
                    10.073728121053193f,
                    0.02098951966413345f,
                    4.584332012409223f,
                    3.731728821873315f,
                    4.129663634714019f,
                    4.123467871956417f,
                    0.19788876518984008f
                )*/
            }


        val output = Array(1) { FloatArray(5) }

        Log.d("Classifier", "Input: ${input.contentDeepToString()}")

        interpreter?.run(input, output)

        val max: Float = output.maxOf { it.max() }

        val prediction = when (output[0].indexOfFirst { it == max }) {
            0 -> "Downstairs"
            1 -> "Office"
            2 -> "Sitting"
            3 -> "Upstairs"
            else -> "Walking"
        }


        println("Risultato inferenza: ${output.contentDeepToString()}")
        println("Classe predetta: $prediction")

        interpreter?.close()


        // TODO end start al contrario ?
        predictionRepository.insert(
            PredictionRecord(
                0,
                prediction,
                movesenseRecord.first().timestamp.toLong(),
                movesenseRecord.last().timestamp.toLong()
            )
        )

        movesenseRecord.forEach {
            movesenseRepo.update(it.copy(isProcessed = true))
        }

        return Result.success()
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

    private fun calculateSD(numArray: List<Float>): Double {
        var sum = 0.0
        var standardDeviation = 0.0

        for (num in numArray) {
            sum += num
        }

        val mean = sum / 10

        for (num in numArray) {
            standardDeviation += (num - mean).pow(2.0)
        }

        return Math.sqrt(standardDeviation / 10)
    }

}