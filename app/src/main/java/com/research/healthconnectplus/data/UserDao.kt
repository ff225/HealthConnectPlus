package com.research.healthconnectplus.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao : GenericDAO<UserInfo> {

    @Query("SELECT * FROM user_info")
    fun getUserInfo(): Flow<UserInfo?>
}