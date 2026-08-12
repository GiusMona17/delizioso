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
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "delizioso.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // Safety net only; every version bump so far ships a real migration.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
