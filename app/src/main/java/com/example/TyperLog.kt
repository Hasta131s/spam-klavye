package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "typer_logs")
data class TyperLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phraseText: String,
    val count: Int,
    val timestamp: Long = System.currentTimeMillis()
)
