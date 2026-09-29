package com.pti.worker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pti.worker.data.db.entities.ContactEntity
import com.pti.worker.data.db.entities.EventEntity
import com.pti.worker.data.db.entities.SettingsEntity
import com.pti.worker.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [EventEntity::class, ContactEntity::class, SettingsEntity::class],
    version = Constants.DATABASE_VERSION,
    exportSchema = false
)
abstract class PtiDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun contactDao(): ContactDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: PtiDatabase? = null

        fun getInstance(context: Context): PtiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PtiDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    database.settingsDao().insert(SettingsEntity())
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
