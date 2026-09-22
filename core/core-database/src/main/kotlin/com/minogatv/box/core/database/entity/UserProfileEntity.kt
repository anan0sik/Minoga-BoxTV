package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.minogatv.box.core.model.enums.ProfileType

/**
 * Room entity for [com.minogatv.box.core.model.UserProfile].
 *
 * Table: `user_profile`
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Human-readable display name. */
    @ColumnInfo(name = "name")
    val name: String,

    /** Profile preset type stored as its ordinal string. */
    @ColumnInfo(name = "type")
    val type: ProfileType = ProfileType.MAIN,

    /**
     * BCrypt hash of the 4-digit parental-control PIN.
     * Null means no PIN is set.
     */
    @ColumnInfo(name = "pin_hash")
    val pinHash: String? = null,

    /** URI pointing to a locally stored avatar image, or null. */
    @ColumnInfo(name = "avatar_uri")
    val avatarUri: String? = null,

    /** Creation timestamp (epoch ms). */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** True when this profile is currently active/selected. */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,
)
