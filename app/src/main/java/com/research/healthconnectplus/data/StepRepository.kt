package com.research.healthconnectplus.data


class StepRepository(private val stepsDAO: StepDAO) : GenericRepository<StepRecord>(stepsDAO) {
    
    suspend fun getUnsyncedStepRecords(): List<StepRecord> {
        return stepsDAO.getUnsyncedStepRecords()
    }

}