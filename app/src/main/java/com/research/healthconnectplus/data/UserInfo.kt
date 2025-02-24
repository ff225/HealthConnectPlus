package com.research.healthconnectplus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_info")
data class UserInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "first_name")
    val name: String,
    @ColumnInfo(name = "last_name")
    val lastName: String,
    @ColumnInfo(name = "dob")
    val dob: Long,
    @ColumnInfo(name = "uid_login")
    val uid: String,
)
