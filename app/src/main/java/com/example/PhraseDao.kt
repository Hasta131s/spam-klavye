package com.example

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhraseDao {
    @Query("SELECT * FROM phrases ORDER BY id DESC")
    fun getAllPhrases(): Flow<List<Phrase>>

    @Insert
    suspend fun insert(phrase: Phrase)

    @Delete
    suspend fun delete(phrase: Phrase)

    @Query("SELECT * FROM typer_logs ORDER BY id DESC")
    fun getAllLogs(): Flow<List<TyperLog>>

    @Insert
    suspend fun insertLog(log: TyperLog)

    @Query("DELETE FROM typer_logs")
    suspend fun clearLogs()
}
