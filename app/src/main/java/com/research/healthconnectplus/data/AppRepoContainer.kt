package com.research.healthconnectplus.data

import android.content.Context

interface AppRepoContainer {

    val heartRepository: HeartRepository
    val stepRepository: StepRepository
    val movesenseRepository: MovesenseRepository
    val predictionRepository: PredictionRepository
}


class AppRepoContainerImpl(context: Context) : AppRepoContainer {

    override val heartRepository: HeartRepository by lazy {
        HeartRepository(HCPlusDatabase.getDatabase(context).heartDAO())
    }

    override val stepRepository: StepRepository by lazy {
        StepRepository(HCPlusDatabase.getDatabase(context).stepDAO())
    }

    override val movesenseRepository: MovesenseRepository by lazy {
        MovesenseRepository(HCPlusDatabase.getDatabase(context).movesenseDAO())
    }

    override val predictionRepository: PredictionRepository by lazy {
        PredictionRepository(HCPlusDatabase.getDatabase(context).predictionDAO())
    }
}