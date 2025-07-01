package net.xian.xianwalletapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NftCacheEntity::class, TokenCacheEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun nftCacheDao(): NftCacheDao
    abstract fun tokenCacheDao(): TokenCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xian_wallet_database" // Name for the database file
                )
                // Wipes and rebuilds instead of migrating if no Migration object.
                // Migration is not covered by this scope.
                .fallbackToDestructiveMigration() // Use this carefully in production
                .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}
