package com.catlife.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY completed ASC, createdAt ASC") fun observeAll(): Flow<List<TodoItem>>
    @Insert suspend fun insert(item: TodoItem): Long
    @Update suspend fun update(item: TodoItem)
    @Delete suspend fun delete(item: TodoItem)
    @Query("SELECT * FROM todos") suspend fun getAllOnce(): List<TodoItem>
    @Query("SELECT * FROM todos WHERE completed = 1") suspend fun getCompletedOnce(): List<TodoItem>
    @Query("DELETE FROM todos WHERE completed = 1") suspend fun deleteCompleted()
    @Query("DELETE FROM todos") suspend fun deleteAllForRestore()
    @Insert suspend fun insertRestored(items: List<TodoItem>)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY purchased ASC, createdAt ASC") fun observeAll(): Flow<List<ShoppingItem>>
    @Insert suspend fun insert(item: ShoppingItem)
    @Update suspend fun update(item: ShoppingItem)
    @Delete suspend fun delete(item: ShoppingItem)
    @Query("DELETE FROM shopping_items WHERE purchased = 1") suspend fun deletePurchased()
    @Query("SELECT * FROM shopping_items") suspend fun getAllOnce(): List<ShoppingItem>
    @Query("DELETE FROM shopping_items") suspend fun deleteAllForRestore()
    @Insert suspend fun insertRestored(items: List<ShoppingItem>)
}

@Database(entities = [TodoItem::class, ShoppingItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun shoppingDao(): ShoppingDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE todos ADD COLUMN reminderTime TEXT")
            }
        }

        fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "korokke-life.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
