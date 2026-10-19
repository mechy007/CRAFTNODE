package app.bedrockhost.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local, offline-first database for CraftNode-managed metadata only.
 *
 * World files, engine runtimes, server properties, and console logs remain in
 * their existing locations. This database must never copy, rename, or delete
 * those files. Secrets such as relay tokens must not be stored here.
 */
class CraftNodeDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE world_profiles (
                id TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL,
                engine_id TEXT NOT NULL,
                world_path TEXT NOT NULL,
                is_selected INTEGER NOT NULL DEFAULT 0 CHECK (is_selected IN (0, 1)),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX one_selected_world
            ON world_profiles(is_selected) WHERE is_selected = 1
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE player_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                last_seen_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(player_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE scheduled_tasks (
                id TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL,
                task_type TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
                configuration_json TEXT NOT NULL DEFAULT '{}',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE app_metadata (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add future schema migrations incrementally. Never drop user data here.
        if (oldVersion < 2) {
            // Version 2 migration will be added alongside the schema change.
        }
    }

    /** Store app-owned metadata only; does not create, move, or modify world files. */
    fun saveMetadata(key: String, value: String, now: Long = System.currentTimeMillis()) {
        require(key.isNotBlank()) { "Metadata key cannot be blank." }
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict(
            "app_metadata",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun readMetadata(key: String): String? {
        readableDatabase.query(
            "app_metadata",
            arrayOf("value"),
            "key = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    companion object {
        const val DATABASE_NAME = "craftnode_metadata.db"
        const val DATABASE_VERSION = 1
    }
}
