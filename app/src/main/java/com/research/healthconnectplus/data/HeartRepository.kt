package com.research.healthconnectplus.data

class HeartRepository(private val heartDAO: HeartDAO) {

    suspend fun insert(heartRecord: HeartRecord) {
        heartDAO.insert(heartRecord)

    }
    
    suspend fun update(heartRecord: HeartRecord) {
        heartDAO.update(heartRecord)
    }

    suspend fun getUnsyncedHeartRecords(): List<HeartRecord> {
        return heartDAO.getUnsyncedHeartRecords()
    }
}