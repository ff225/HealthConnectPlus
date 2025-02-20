package com.research.healthconnectplus.data

class PredictionRepository(private val predictionDAO: PredictionDAO) :
    GenericRepository<PredictionRecord>(predictionDAO)