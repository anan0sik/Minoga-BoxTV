package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minogatv.box.core.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [UserProfileEntity].
 */
@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = :id")
    suspend fun getById(id: Long): UserProfileEntity?

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getFirstProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profile")
    fun observeAll(): Flow<List<UserProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: UserProfileEntity): Long
}
