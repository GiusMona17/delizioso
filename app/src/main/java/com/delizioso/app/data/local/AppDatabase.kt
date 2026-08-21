package com.delizioso.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
        SourceEntity::class,
        PlannedMealEntity::class,
        PantryItemEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun pantryDao(): PantryDao

    companion object {

        /** v1 → v2: add the meal planner table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `planned_meals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recipeId` INTEGER NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `slot` TEXT NOT NULL,
                        `servings` INTEGER NOT NULL,
                        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_meals_recipeId` ON `planned_meals` (`recipeId`)")
            }
        }

        /** v2 → v3: track whether a planned meal was actually cooked. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `planned_meals` ADD COLUMN `cooked` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3 → v4: drop the four stored macro columns.
         *
         * Macros are now summed from the ingredients whenever they are shown, so
         * the columns could only ever hold a stale copy. SQLite before 3.35 has
         * no DROP COLUMN, so the table is rebuilt — which is also what Room's own
         * generated migrations do.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipes_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `servings` INTEGER,
                        `prepTimeMinutes` INTEGER,
                        `cookTimeMinutes` INTEGER,
                        `imageUri` TEXT,
                        `notes` TEXT,
                        `isFavorite` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `recipes_new` (
                        `id`, `title`, `description`, `servings`, `prepTimeMinutes`,
                        `cookTimeMinutes`, `imageUri`, `notes`, `isFavorite`, `createdAt`, `updatedAt`
                    )
                    SELECT `id`, `title`, `description`, `servings`, `prepTimeMinutes`,
                           `cookTimeMinutes`, `imageUri`, `notes`, `isFavorite`, `createdAt`, `updatedAt`
                    FROM `recipes`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `recipes`")
                db.execSQL("ALTER TABLE `recipes_new` RENAME TO `recipes`")
            }
        }

        /** v4 → v5: add caloriesKcal, proteinG, fatG, carbsG columns for AI/stored macros. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `caloriesKcal` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `proteinG` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `fatG` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `carbsG` REAL DEFAULT NULL")
            }
        }

        /** v5 → v6: add isSide column to planned_meals. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `planned_meals` ADD COLUMN `isSide` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 → v7: add pantry_items table. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pantry_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `quantity` TEXT,
                        `expiresAtEpochDay` INTEGER,
                        `inStock` INTEGER NOT NULL,
                        `addedAtEpochMilli` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pantry_items_name` ON `pantry_items` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pantry_items_inStock` ON `pantry_items` (`inStock`)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "delizioso.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // Safety net only; every version bump so far ships a real migration.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
