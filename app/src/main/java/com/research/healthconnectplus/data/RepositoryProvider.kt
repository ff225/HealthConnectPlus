package com.research.healthconnectplus.data

// TODO add repo when add new datatype
class RepositoryProvider(
    val heartRepository: HeartRepository,
    val stepRepository: StepRepository,
    val movesenseRepository: MovesenseRepository
)
