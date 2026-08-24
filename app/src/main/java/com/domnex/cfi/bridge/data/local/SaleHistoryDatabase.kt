package com.domnex.cfi.bridge.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CapturedSaleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SaleHistoryDatabase : RoomDatabase() {

    abstract fun capturedSaleDao(): CapturedSaleDao

    companion object {
        private const val DB_NAME = "domnex_sale_history.db"
    }
}
