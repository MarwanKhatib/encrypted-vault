package com.example.encryptedvault

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.encryptedvault.data.VaultDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies every schema upgrade keeps existing photos (Gap 11). Uses the schemas exported to app/schemas. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), VaultDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesFromV1ToLatestKeepingRows() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO photos (hash, mimeType, timestamp, status) VALUES ('h1', 'image/jpeg', 1000, 'complete')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 5, true, *VaultDatabase.MIGRATIONS)
        db.query("SELECT hash, mimeType, timestamp, status, size, favorite, deletedAt, durationMs, metaEnc FROM photos").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("h1", c.getString(0))
            assertEquals(1000L, c.getLong(2))
            assertEquals("complete", c.getString(3))
            assertEquals(0L, c.getLong(4)); assertEquals(0L, c.getLong(5)); assertEquals(0L, c.getLong(6)); assertEquals(0L, c.getLong(7))
            assertTrue(c.isNull(8))
        }
    }

    @Test
    fun migratesFromV2DroppingClearTextNameButKeepingFavorites() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO photos (hash, mimeType, timestamp, status, name, size, favorite) VALUES ('h2', 'video/mp4', 2000, 'complete', 'secret-name.mp4', 55, 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 3, true, VaultDatabase.MIGRATION_2_3)
        db.query("SELECT size, favorite FROM photos WHERE hash = 'h2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(55L, c.getLong(0)); assertEquals(1L, c.getLong(1))
        }
        db.query("SELECT name FROM pragma_table_info('photos')").use { c ->
            while (c.moveToNext()) assertTrue("clear-text name column must be gone", c.getString(0) != "name")
        }
    }

    @Test
    fun migratesFromV3AddingHiddenAndSizeColumnsWithSafeDefaults() {
        helper.createDatabase(DB, 3).apply {
            execSQL("INSERT INTO photos (hash, mimeType, timestamp, status, favorite) VALUES ('h3', 'image/png', 3000, 'complete', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 4, true, VaultDatabase.MIGRATION_3_4)
        db.query("SELECT hidden, width, height, favorite FROM photos WHERE hash = 'h3'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0L, c.getLong(0)); assertEquals(0L, c.getLong(1)); assertEquals(0L, c.getLong(2)); assertEquals(1L, c.getLong(3))
        }
    }

    /** v4 -> v5 drops the removed People table but must leave photos and album links intact. */
    @Test
    fun migratesFromV4DroppingPeopleButKeepingPhotosAndAlbums() {
        helper.createDatabase(DB, 4).apply {
            execSQL("INSERT INTO photos (hash, mimeType, timestamp, status) VALUES ('h4', 'image/jpeg', 4000, 'complete')")
            execSQL("INSERT INTO people (nameEnc) VALUES (x'00')")
            execSQL("INSERT INTO albums (nameEnc) VALUES (x'01')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 5, true, VaultDatabase.MIGRATION_4_5)
        db.query("SELECT count(*) FROM photos WHERE hash = 'h4'").use { c -> assertTrue(c.moveToFirst()); assertEquals(1L, c.getLong(0)) }
        db.query("SELECT count(*) FROM albums").use { c -> assertTrue(c.moveToFirst()); assertEquals(1L, c.getLong(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'people'").use { c -> assertEquals(0, c.count) }
    }

    private companion object { const val DB = "migration-test" }
}
