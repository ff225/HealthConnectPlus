package com.research.healthconnectplus.data

class InferenceTimeRepository(private val inferenceDAO: InferenceTimeDAO) :
    GenericRepository<InferenceTimeRecord>(inferenceDAO)