package com.research.healthconnectplus.data


class StepRepository(private val stepsDAO: StepDAO) {


    suspend fun insert(stepRecord: StepRecord) {
        stepsDAO.insert(stepRecord)

    }

    suspend fun update(stepRecord: StepRecord) {
        stepsDAO.update(stepRecord)
    }

    suspend fun getUnsyncedStepRecords(): List<StepRecord> {
        return stepsDAO.getUnsyncedStepRecords()
    }

}