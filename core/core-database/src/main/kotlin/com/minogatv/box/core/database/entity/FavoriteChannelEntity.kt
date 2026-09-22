package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room entity representing a channel marked as favourite by a specific profile.
 *
 * Table: `favorite_channel`
 *
 * Composite primary key: `(profile_id, channel_id)` — a channel can be a favourite
 * for multiple profiles independently.
 *
 * Cascade deletes:
 *  - Deleting the profile removes all its favourites.
 *  - Deleting the channel removes it from all profiles' favourites.
 */
@Entity(
    tableName = "favorite_channel",
    primaryKeys = ["profile_id", "channel_id"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profile_id"),
        Index("channel_id"),
    ],
)
data class FavoriteChannelEntity(

    /** Owner profile. */
    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /** The favourited channel. */
    @ColumnInfo(name = "channel_id")
    val channelId: Long,

    /**
     * Manual sort order within the Favorites folder.
     * Lower value = higher position. Updated during drag-and-drop.
     */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    /** Timestamp when the channel was added to favourites (epoch ms). */
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
)
