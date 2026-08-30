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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `operations` ADD COLUMN `hlcTimestamp` TEXT")
        db.execSQL("ALTER TABLE `operations` ADD COLUMN `logicalClock` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `operations` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Orders table scoring and date fields
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `rawDateText` TEXT")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `resolvedDate` TEXT")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `dateConfidence` REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `paymentMethod` TEXT")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `classification` TEXT DEFAULT 'ORDER'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `classificationScore` REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `fieldExtractionScore` REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `dateResolutionScore` REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `clarificationDecisionScore` REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `overallScore` REAL NOT NULL DEFAULT 1.0")

        // Messages table classification and scoring fields
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `classification` TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `classificationScore` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `fieldExtractionScore` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `dateResolutionScore` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `clarificationDecisionScore` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `overallScore` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `rawDateText` TEXT")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `resolvedDate` TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Orders table omnichannel and versioning columns
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `orderNumber` TEXT NOT NULL DEFAULT '#1000'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'SMS'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `phone` TEXT")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `deliveryTime` TEXT")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `paymentStatus` TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `targetDurationMinutes` INTEGER NOT NULL DEFAULT 30")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `baseVersion` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT 'device-local'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'user-default'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'SYNCED'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `lastModifiedBy` TEXT NOT NULL DEFAULT 'device-local'")
        db.execSQL("ALTER TABLE `orders` ADD COLUMN `pinCode` TEXT")

        // Customers table versioning columns
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")

        // Operations table versioning and userId columns
        db.execSQL("ALTER TABLE `operations` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'user-default'")
        db.execSQL("ALTER TABLE `operations` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `operations` ADD COLUMN `baseVersion` INTEGER NOT NULL DEFAULT 0")
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
    version = 6,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
