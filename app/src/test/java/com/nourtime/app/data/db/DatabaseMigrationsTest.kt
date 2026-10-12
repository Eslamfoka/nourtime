package com.nourtime.app.data.db

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Found in the 2026-10-12 audit: the database had no exported schema and no migrations, so the first
// schema change shipped without one would crash Nour Time on every start for existing installs.
class DatabaseMigrationsTest {

    @Test
    fun `every version after the first has a migration from the one before`() {
        for (v in 2..NourDatabase.VERSION) {
            assertTrue(
                "Add a Migration($v - 1, $v) to NourDatabase.MIGRATIONS",
                NourDatabase.MIGRATIONS.any { it.startVersion == v - 1 && it.endVersion == v },
            )
        }
    }

    @Test
    fun `every version's schema is exported and committed`() {
        // Unit tests run in the module directory (app/).
        val dir = File("schemas/${NourDatabase::class.java.name}")
        for (v in 1..NourDatabase.VERSION) {
            assertTrue("Build once and commit ${dir.path}/$v.json", File(dir, "$v.json").isFile)
        }
    }
}
