package com.research.healthconnectplus.data


open class GenericRepository<T>(private val genericDAO: GenericDAO<T>) {

    suspend fun insert(t: T) {
        genericDAO.insert(t)
    }

    suspend fun update(t: T) {
        genericDAO.update(t)
    }

    suspend fun delete(t: T) {
        genericDAO.delete(t)
    }
}