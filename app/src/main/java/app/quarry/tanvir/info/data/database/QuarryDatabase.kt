package app.quarry.tanvir.info.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FileEntity::class,
        ScanSnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class QuarryDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun scanSnapshotDao(): ScanSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: QuarryDatabase? = null

        fun getInstance(context: Context): QuarryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuarryDatabase::class.java,
                    "quarry_storage.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
