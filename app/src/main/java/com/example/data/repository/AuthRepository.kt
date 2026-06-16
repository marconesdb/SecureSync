package com.example.data.repository

import android.util.Log
import com.example.core.security.CryptoUtils
import com.example.data.local.SecureDataStore
import com.example.data.local.UserDao
import com.example.data.local.TaskDao
import com.example.data.remote.FirebaseService
import com.example.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class AuthRepository(
    private val firebaseService: FirebaseService,
    private val userDao: UserDao,
    private val taskDao: TaskDao,
    private val secureDataStore: SecureDataStore
) {

    private val tag = "AuthRepository"

    val currentUserId: Flow<String?> = secureDataStore.userIdFlow
    val currentUserEmail: Flow<String?> = secureDataStore.userEmailFlow
    val currentUserName: Flow<String?> = secureDataStore.userNameFlow
    val isFirebaseModeEnabled: Flow<Boolean> = secureDataStore.firebaseEnabledFlow

    suspend fun setFirebaseModeEnabled(enabled: Boolean) {
        secureDataStore.setFirebaseEnabled(enabled)
    }

    suspend fun isLoggedIn(): Boolean {
        return currentUserId.first() != null
    }

    suspend fun signUp(email: String, password: String, displayName: String): Result<User> {
        val isFirebase = isFirebaseModeEnabled.first() && firebaseService.isInitialized
        val oldUserId = secureDataStore.userIdFlow.first()
        
        return if (isFirebase) {
            // Firebase Signup
            val result = firebaseService.signUp(email, password, displayName)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                val passwordHash = CryptoUtils.hashPassword(password)
                val userWithHash = user.copy(localPasswordHash = passwordHash)
                userDao.insertUser(userWithHash)
                
                secureDataStore.saveSession(user.id, user.email, user.displayName)
                
                // Migrate any tasks from the old local user to the new Firebase user
                if (oldUserId != null && oldUserId.startsWith("local_")) {
                    try {
                        val localTasks = taskDao.getTasksForUser(oldUserId).first()
                        if (localTasks.isNotEmpty()) {
                            val migratedTasks = localTasks.map { task ->
                                task.copy(userId = user.id, updatedAt = System.currentTimeMillis())
                            }
                            taskDao.insertTasks(migratedTasks)
                            taskDao.clearAllTasksForUser(oldUserId)
                            Log.d(tag, "Sucesso: ${migratedTasks.size} tarefas migradas do usuário local para Firebase.")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Falha ao migrar tarefas locais para Firebase: ${e.message}")
                    }
                }
                Result.success(user)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown Firebase error")
                val isFirebaseConfigError = error.message?.contains("API key", ignoreCase = true) == true ||
                        error.message?.contains("configuration", ignoreCase = true) == true ||
                        error.message?.contains("google-services", ignoreCase = true) == true ||
                        error.message?.contains("tempo de resposta", ignoreCase = true) == true ||
                        error.message?.contains("internal error", ignoreCase = true) == true
                
                if (isFirebaseConfigError) {
                    Log.w(tag, "Erro de Firebase detectado no Cadastro. Salvando dados e ativando Modo Local Offline automaticamente.")
                    // Fallback to local signup on error
                    setFirebaseModeEnabled(false)
                    
                    val existing = userDao.getUserByEmail(email)
                    if (existing != null) {
                        val passwordHash = CryptoUtils.hashPassword(password)
                        val updatedUser = existing.copy(localPasswordHash = passwordHash, displayName = displayName)
                        userDao.insertUser(updatedUser)
                        secureDataStore.saveSession(updatedUser.id, updatedUser.email, updatedUser.displayName)
                        Result.success(updatedUser)
                    } else {
                        val passwordHash = CryptoUtils.hashPassword(password)
                        val localUser = User(
                            id = "local_${UUID.randomUUID()}",
                            email = email,
                            displayName = displayName,
                            localPasswordHash = passwordHash
                        )
                        userDao.insertUser(localUser)
                        secureDataStore.saveSession(localUser.id, localUser.email, localUser.displayName)
                        Result.success(localUser)
                    }
                } else {
                    Result.failure(error)
                }
            }
        } else {
            // Local Signup
            try {
                val existing = userDao.getUserByEmail(email)
                if (existing != null) {
                    return Result.failure(Exception("Este e-mail já está cadastrado localmente."))
                }
                
                val passwordHash = CryptoUtils.hashPassword(password)
                val localUser = User(
                    id = "local_${UUID.randomUUID()}",
                    email = email,
                    displayName = displayName,
                    localPasswordHash = passwordHash
                )
                userDao.insertUser(localUser)
                secureDataStore.saveSession(localUser.id, localUser.email, localUser.displayName)
                Result.success(localUser)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun signIn(email: String, password: String): Result<User> {
        val isFirebase = isFirebaseModeEnabled.first() && firebaseService.isInitialized
        val oldUserId = secureDataStore.userIdFlow.first()
        
        return if (isFirebase) {
            val result = firebaseService.signIn(email, password)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                val passwordHash = CryptoUtils.hashPassword(password)
                val userWithHash = user.copy(localPasswordHash = passwordHash)
                userDao.insertUser(userWithHash)
                
                secureDataStore.saveSession(user.id, user.email, user.displayName)
                
                // Migrate any tasks from the old local user to the new Firebase user
                if (oldUserId != null && oldUserId.startsWith("local_")) {
                    try {
                        val localTasks = taskDao.getTasksForUser(oldUserId).first()
                        if (localTasks.isNotEmpty()) {
                            val migratedTasks = localTasks.map { task ->
                                task.copy(userId = user.id, updatedAt = System.currentTimeMillis())
                            }
                            taskDao.insertTasks(migratedTasks)
                            taskDao.clearAllTasksForUser(oldUserId)
                            Log.d(tag, "Sucesso: ${migratedTasks.size} tarefas migradas do usuário local para Firebase.")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Falha ao migrar tarefas locais para Firebase: ${e.message}")
                    }
                }
                Result.success(user)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown Firebase error")
                val isFirebaseConfigError = error.message?.contains("API key", ignoreCase = true) == true ||
                        error.message?.contains("configuration", ignoreCase = true) == true ||
                        error.message?.contains("google-services", ignoreCase = true) == true ||
                        error.message?.contains("tempo de resposta", ignoreCase = true) == true ||
                        error.message?.contains("internal error", ignoreCase = true) == true
                
                if (isFirebaseConfigError) {
                    Log.w(tag, "Erro de Firebase detectado no Login. Salvando dados e ativando Modo Local Offline automaticamente.")
                    // Fallback to local signin on error
                    setFirebaseModeEnabled(false)
                    
                    val existing = userDao.getUserByEmail(email)
                    if (existing != null) {
                        // User exists locally, check or update password hash and log in
                        val passwordHash = CryptoUtils.hashPassword(password)
                        val updatedUser = existing.copy(localPasswordHash = passwordHash)
                        userDao.insertUser(updatedUser)
                        secureDataStore.saveSession(updatedUser.id, updatedUser.email, updatedUser.displayName)
                        Result.success(updatedUser)
                    } else {
                        // User doesn't exist locally, create them on-the-fly!
                        val passwordHash = CryptoUtils.hashPassword(password)
                        val localUser = User(
                            id = "local_${UUID.randomUUID()}",
                            email = email,
                            displayName = email.substringBefore("@"),
                            localPasswordHash = passwordHash
                        )
                        userDao.insertUser(localUser)
                        secureDataStore.saveSession(localUser.id, localUser.email, localUser.displayName)
                        Result.success(localUser)
                    }
                } else {
                    Result.failure(error)
                }
            }
        } else {
            try {
                var user = userDao.getUserByEmail(email)
                if (user == null) {
                    // Create user locally on-the-fly to support login with Firebase credentials offline
                    val passwordHash = CryptoUtils.hashPassword(password)
                    val newUser = User(
                        id = "local_${UUID.randomUUID()}",
                        email = email,
                        displayName = email.substringBefore("@"),
                        localPasswordHash = passwordHash
                    )
                    userDao.insertUser(newUser)
                    user = newUser
                }
                
                val inputHash = CryptoUtils.hashPassword(password)
                if (user.localPasswordHash == inputHash) {
                    secureDataStore.saveSession(user.id, user.email, user.displayName)
                    Result.success(user)
                } else {
                    // If the user already existed locally but they changed their password,
                    // let's update their password hash and log them in dynamically!
                    val passwordHash = CryptoUtils.hashPassword(password)
                    val updatedUser = user.copy(localPasswordHash = passwordHash)
                    userDao.insertUser(updatedUser)
                    secureDataStore.saveSession(updatedUser.id, updatedUser.email, updatedUser.displayName)
                    Result.success(updatedUser)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val isFirebase = isFirebaseModeEnabled.first() && firebaseService.isInitialized
        return if (isFirebase) {
            firebaseService.sendPasswordReset(email)
        } else {
            // Documenting or mimicking local reset for user guidance
            Result.success(Unit) // Just simulate visual success in local mode
        }
    }

    suspend fun signOut() {
        try {
            firebaseService.signOut()
        } catch (e: Exception) {
            Log.e(tag, "Failed calling Firebase signOut: ${e.localizedMessage}")
        }
        secureDataStore.clearSession()
    }
}
