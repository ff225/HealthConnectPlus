package com.research.healthconnectplus.data

class PredictionRepository(private val predictionDAO: PredictionDAO) {

    suspend fun insert(predictionRecord: PredictionRecord) {
        predictionDAO.insert(predictionRecord)
    }

    suspend fun update(predictionRecord: PredictionRecord) {
        predictionDAO.update(predictionRecord)
    }

    suspend fun delete(predictionRecord: PredictionRecord) {
        predictionDAO.delete(predictionRecord)
    }
}