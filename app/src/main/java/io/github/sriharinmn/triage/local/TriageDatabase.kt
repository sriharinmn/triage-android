package io.github.sriharinmn.triage.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [IssueEntity::class, IssueListEntryEntity::class, IssueListEntity::class, ProjectEntity::class],
  version = 1,
  exportSchema = true,
)
abstract class TriageDatabase : RoomDatabase() {
  abstract fun cacheDao(): CacheDao

  companion object {
    /** The app's on-disk database. Tests use `Room.inMemoryDatabaseBuilder` instead. */
    fun create(context: Context): TriageDatabase =
      Room.databaseBuilder(context.applicationContext, TriageDatabase::class.java, "triage.db")
        // Everything stored is a copy of Sentry data, so a schema change may simply start over.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
  }
}
