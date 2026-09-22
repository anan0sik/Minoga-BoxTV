package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table providing ultra-fast full-text search over EPG programme data.
 *
 * This is a **content table** backed by [EpgProgramEntity].
 * Room keeps the FTS index in sync automatically via triggers.
 *
 * Usage in [com.minogatv.box.core.database.dao.EpgDao]:
 * ```sql
 * SELECT epg_program.*
 * FROM epg_program
 * WHERE rowid IN (
 *     SELECT rowid FROM epg_fts WHERE epg_fts MATCH :query
 * )
 * ```
 *
 * Why FTS4 and not FTS5?
 * FTS5 is only available on SQLite 3.9+ (Android 7+). FTS4 is available on
 * all supported Android versions and performs identically for our use case.
 *
 * Tokenizer: `unicode61` — handles accented characters and non-ASCII correctly.
 */
@Entity(tableName = "epg_fts")
@Fts4(
    contentEntity = EpgProgramEntity::class,
    tokenizer = "unicode61",
)
data class EpgFtsEntity(

    /** Indexed programme title. */
    @ColumnInfo(name = "title")
    val title: String,

    /** Indexed programme description / synopsis. */
    @ColumnInfo(name = "description")
    val description: String,
)
