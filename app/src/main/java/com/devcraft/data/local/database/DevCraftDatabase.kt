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

@Database(
    entities = [
        CustomerEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        OperationEntity::class,
        ConflictEntity::class,
        MessageEntity::class
    ],
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
