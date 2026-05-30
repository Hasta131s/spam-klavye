package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phrases")
data class Phrase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val repeatCount: Int,
    val intervalMs: Long = 1000
)
