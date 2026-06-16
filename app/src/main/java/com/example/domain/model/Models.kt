package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority {
    LOW, MEDIUM, HIGH
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String, // Firebase UID or local secure UUID
    val email: String,
    val displayName: String,
    val localPasswordHash: String, // Kept for local fallback crypt/verification
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val iconName: String
) {
    companion object {
        val DEFAULT_CATEGORIES = listOf(
            Category("work", "Trabalho", "#FF3B30", "work"),
            Category("personal", "Pessoal", "#34C759", "person"),
            Category("health", "Saúde", "#AF52DE", "favorite"),
            Category("finance", "Financeiro", "#FFCC00", "payments")
        )
    }
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val isCompleted: Boolean,
    val priority: TaskPriority,
    val dueDate: Long, // Timestamp
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
