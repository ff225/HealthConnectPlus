package com.research.healthconnectplus.data

class UserRepository(private val userDao: UserDao) : GenericRepository<UserInfo>(userDao) {

    fun getUserInfo() = userDao.getUserInfo()
}