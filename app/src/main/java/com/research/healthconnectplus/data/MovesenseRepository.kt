package com.research.healthconnectplus.data

class MovesenseRepository(private val movesenseDAO: MovesenseDAO) {

    suspend fun insert(movesenseRecord: MovesenseRecord) {
        movesenseDAO.insert(movesenseRecord)
    }

    suspend fun update(movesenseRecord: MovesenseRecord) {
        movesenseDAO.update(movesenseRecord)
    }

    suspend fun delete(movesenseRecord: MovesenseRecord) {
        movesenseDAO.delete(movesenseRecord)
    }

    suspend fun getUnsyncedRecords(): List<MovesenseRecord> {
        return movesenseDAO.getUnsyncedRecords()
    }

    suspend fun getUnprocessedRecords(): List<MovesenseRecord> {
        return movesenseDAO.getUnprocessedRecords()
    }

    fun fetchCursor() = movesenseDAO.fetchCursor()
}