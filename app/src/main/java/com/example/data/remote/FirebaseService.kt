package com.example.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.domain.model.Task
import com.example.domain.model.TaskPriority
import com.example.domain.model.User
import kotlinx.coroutines.tasks.await

class FirebaseService(private val context: Context) {

    private val tag = "FirebaseService"

    // Checks safely if Firebase is initialized. If not, it won't crash the app.
    val isInitialized: Boolean
        get() = try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            Log.w(tag, "Firebase dynamic check failed. FirebaseApp is not initialized: ${e.localizedMessage}")
            false
        }

    private val auth: FirebaseAuth?
        get() = if (isInitialized) FirebaseAuth.getInstance() else null

    private val firestore: FirebaseFirestore?
        get() = if (isInitialized) FirebaseFirestore.getInstance() else null

    suspend fun signUp(email: String, password: String, displayName: String): Result<User> {
        if (!isInitialized) return Result.failure(Exception("Firebase não está inicializado. Adicione o arquivo google-services.json."))
        return try {
            kotlinx.coroutines.withTimeout(12000) {
                val result = auth!!.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = result.user ?: throw Exception("Falha ao criar usuário nulo.")
                
                // Save profile details to Firestore
                val user = User(
                    id = firebaseUser.uid,
                    email = email,
                    displayName = displayName,
                    localPasswordHash = "" // Clear for cloud-auth profiles
                )
                saveUserProfile(user)
                Result.success(user)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("O tempo de resposta do Firebase esgotou. Verifique se o provedor 'E-mail/senha' está ativo no console do Firebase ou altere para o Modo Local Offline."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<User> {
        if (!isInitialized) return Result.failure(Exception("Firebase não está inicializado. Adicione o arquivo google-services.json."))
        return try {
            kotlinx.coroutines.withTimeout(12000) {
                val result = auth!!.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = result.user ?: throw Exception("Falha ao entrar.")
                
                // Fetch profile description from Firestore or build default
                val displayName = firebaseUser.displayName ?: fetchUserProfile(firebaseUser.uid)?.displayName ?: email.substringBefore("@")
                val user = User(
                    id = firebaseUser.uid,
                    email = email,
                    displayName = displayName,
                    localPasswordHash = ""
                )
                Result.success(user)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("O tempo de resposta do Firebase esgotou. Verifique se o provedor 'E-mail/senha' está ativo no console do Firebase ou altere para o Modo Local Offline."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        if (isInitialized) {
            auth?.signOut()
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        if (!isInitialized) return Result.failure(Exception("Firebase não está inicializado. Adicione o arquivo google-services.json."))
        return try {
            auth!!.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Profiles on Firestore
    private suspend fun saveUserProfile(user: User) {
        try {
            firestore?.collection("profiles")?.document(user.id)?.set(user)?.await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save profile: ${e.localizedMessage}")
        }
    }

    private suspend fun fetchUserProfile(uid: String): User? {
        return try {
            val doc = firestore?.collection("profiles")?.document(uid)?.get()?.await()
            if (doc != null && doc.exists()) {
                User(
                    id = uid,
                    email = doc.getString("email") ?: "",
                    displayName = doc.getString("displayName") ?: "",
                    localPasswordHash = ""
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // CRUD for Tasks in Firestore
    suspend fun fetchTasks(userId: String): Result<List<Task>> {
        if (!isInitialized) return Result.failure(Exception("Firebase não está inicializado."))
        return try {
            val querySnapshot = firestore!!.collection("tasks")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            val tasksList = querySnapshot.documents.mapNotNull { doc ->
                try {
                    Task(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        categoryId = doc.getString("categoryId") ?: "personal",
                        isCompleted = doc.getBoolean("completed") ?: doc.getBoolean("isCompleted") ?: false,
                        priority = TaskPriority.valueOf(doc.getString("priority") ?: "MEDIUM"),
                        dueDate = doc.getLong("dueDate") ?: System.currentTimeMillis(),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(tasksList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveTask(task: Task): Result<Unit> {
        if (!isInitialized) return Result.failure(Exception("Firebase desativado."))
        return try {
            val data = hashMapOf(
                "userId" to task.userId,
                "title" to task.title,
                "description" to task.description,
                "categoryId" to task.categoryId,
                "completed" to task.isCompleted,
                "priority" to task.priority.name,
                "dueDate" to task.dueDate,
                "createdAt" to task.createdAt,
                "updatedAt" to task.updatedAt
            )
            firestore!!.collection("tasks").document(task.id).set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(task: Task): Result<Unit> {
        if (!isInitialized) return Result.failure(Exception("Firebase desativado."))
        return try {
            firestore!!.collection("tasks").document(task.id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
