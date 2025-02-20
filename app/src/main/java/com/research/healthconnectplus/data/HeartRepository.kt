package com.research.healthconnectplus.data

class HeartRepository(private val heartDAO: HeartDAO) : GenericRepository<HeartRecord>(heartDAO) {

    suspend fun getUnsyncedHeartRecords(): List<HeartRecord> {
        return heartDAO.getUnsyncedHeartRecords()
    }
}