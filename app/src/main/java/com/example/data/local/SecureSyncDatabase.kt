package com.example.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.Category
import com.example.domain.model.Task
import com.example.domain.model.TaskPriority
import com.example.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromPriority(priority: TaskPriority): String {
        return priority.name
    }

    @TypeConverter
    fun toPriority(value: String): TaskPriority {
        return try {
            TaskPriority.valueOf(value)
        } catch (e: Exception) {
            TaskPriority.MEDIUM
        }
    }
}

@Database(entities = [User::class, Task::class, Category::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SecureSyncDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: SecureSyncDatabase? = null

        fun getDatabase(context: Context): SecureSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecureSyncDatabase::class.java,
                    "securesync_database"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default categories upon Room DB creation safely within the main transaction
                        try {
                            Category.DEFAULT_CATEGORIES.forEach { category ->
                                db.execSQL(
                                    "INSERT OR IGNORE INTO categories (id, name, colorHex, iconName) VALUES (?, ?, ?, ?)",
                                    arrayOf(category.id, category.name, category.colorHex, category.iconName)
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SecureSyncDatabase", "Error seeding default categories: ${e.message}")
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
