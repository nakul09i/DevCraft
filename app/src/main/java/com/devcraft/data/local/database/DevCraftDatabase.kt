package com.devcraft.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.devcraft.data.local.dao.ConflictDao
import com.devcraft.data.local.dao.CustomerDao
import com.devcraft.data.local.dao.OperationDao
import com.devcraft.data.local.dao.OrderDao
import com.devcraft.data.local.entities.*

@Database(
    entities = [
        CustomerEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        OperationEntity::class,
        ConflictEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DevCraftDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun operationDao(): OperationDao
    abstract fun conflictDao(): ConflictDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
