package com.devcraft.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.devcraft.data.local.dao.ConflictDao
import com.devcraft.data.local.dao.CustomerDao
import com.devcraft.data.local.dao.MessageDao
import com.devcraft.data.local.dao.OperationDao
import com.devcraft.data.local.dao.OrderDao
import com.devcraft.data.local.entities.*

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `messages` (
                `messageId` TEXT NOT NULL PRIMARY KEY,
                `source` TEXT NOT NULL,
                `sender` TEXT,
                `senderName` TEXT,
                `originalText` TEXT NOT NULL,
                `receivedAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `confidence` REAL NOT NULL,
                `parsedOrderId` TEXT,
                `needsClarification` INTEGER NOT NULL,
                `parseError` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

/**
 * Adds nullable location columns to orders and customers. Every column is
 * nullable so existing rows stay valid and location remains optional.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("orders", "customers")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `latitude` REAL")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `longitude` REAL")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `formattedAddress` TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `placeId` TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `locationSource` TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `locationUpdatedAt` INTEGER")
        }
    }
}

@Database(
    entities = [
        CustomerEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        OperationEntity::class,
        ConflictEntity::class,
        MessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DevCraftDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun operationDao(): OperationDao
    abstract fun conflictDao(): ConflictDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: DevCraftDatabase? = null

        fun getDatabase(context: Context): DevCraftDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DevCraftDatabase::class.java,
                    "devcraft_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // No fallbackToDestructiveMigration: it silently wipes every
                    // order a merchant has entered if a migration is ever missed.
                    // A crash on an unhandled upgrade is far better than data loss.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
