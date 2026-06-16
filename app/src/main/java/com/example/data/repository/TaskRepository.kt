package com.example.data.repository

import com.example.core.security.CryptoUtils
import com.example.data.local.SecureDataStore
import com.example.data.local.TaskDao
import com.example.data.remote.FirebaseService
import com.example.domain.model.Category
import com.example.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val taskDao: TaskDao,
    private val firebaseService: FirebaseService,
    private val secureDataStore: SecureDataStore
) {

    // Listens to tasks, decrypting values if local encryption is enabled
    fun getTasksForUser(userId: String): Flow<List<Task>> {
        val tasksFlow = taskDao.getTasksForUser(userId)
        val encryptionFlow = secureDataStore.localEncryptionFlow
        
        return combine(tasksFlow, encryptionFlow) { tasks, encrypted ->
            if (encrypted) {
                tasks.map { task ->
                    task.copy(
                        title = CryptoUtils.decrypt(task.title),
                        description = CryptoUtils.decrypt(task.description)
                    )
                }
            } else {
                tasks
            }
        }
    }

    suspend fun getTaskById(taskId: String): Task? {
        val task = taskDao.getTaskById(taskId) ?: return null
        val encrypted = secureDataStore.localEncryptionFlow.first()
        return if (encrypted) {
            task.copy(
                title = CryptoUtils.decrypt(task.title),
                description = CryptoUtils.decrypt(task.description)
            )
        } else {
            task
        }
    }

    suspend fun insertTask(task: Task): Result<Unit> {
        val encrypted = secureDataStore.localEncryptionFlow.first()
        val processedTask = if (encrypted) {
            task.copy(
                title = CryptoUtils.encrypt(task.title),
                description = CryptoUtils.encrypt(task.description)
            )
        } else {
            task
        }

        // Save locally
        taskDao.insertTask(processedTask)

        // Save on cloud if enabled
        val isFirebase = secureDataStore.firebaseEnabledFlow.first() && firebaseService.isInitialized
        if (isFirebase) {
            // Send decrypted or normal to cloud depending on requirements
            // Cloud usually holds normal or optionally hashed/protected content. Let's send normal values to cloud for clear web view
            val result = firebaseService.saveTask(task)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Falha ao salvar no Firestore (Sessão Offline)."))
            }
        }
        return Result.success(Unit)
    }

    suspend fun deleteTask(task: Task): Result<Unit> {
        taskDao.deleteTask(task)
        val isFirebase = secureDataStore.firebaseEnabledFlow.first() && firebaseService.isInitialized
        if (isFirebase) {
            val result = firebaseService.deleteTask(task)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Falha ao deletar no Firestore (Sessão Offline)."))
            }
        }
        return Result.success(Unit)
    }

    suspend fun syncWithRemote(userId: String): Result<Unit> {
        val isFirebase = secureDataStore.firebaseEnabledFlow.first() && firebaseService.isInitialized
        if (!isFirebase) {
            return Result.failure(Exception("Sincronização desativada (Configurado em Modo Local)."))
        }

        return try {
            val remoteResult = firebaseService.fetchTasks(userId)
            if (remoteResult.isSuccess) {
                val remoteTasks = remoteResult.getOrNull() ?: emptyList()
                val localTasks = taskDao.getTasksForUser(userId).first()
                val encrypted = secureDataStore.localEncryptionFlow.first()

                // Merge: any task on remote but not local to be cached locally
                remoteTasks.forEach { remoteTask ->
                    val localMatch = localTasks.find { it.id == remoteTask.id }
                    if (localMatch == null || remoteTask.updatedAt > localMatch.updatedAt) {
                        val taskToInsert = if (encrypted) {
                            remoteTask.copy(
                                title = CryptoUtils.encrypt(remoteTask.title),
                                description = CryptoUtils.encrypt(remoteTask.description)
                            )
                        } else {
                            remoteTask
                        }
                        taskDao.insertTask(taskToInsert)
                    }
                }

                // Any local tasks not on remote to be uploaded
                localTasks.forEach { localTask ->
                    val remoteMatch = remoteTasks.find { it.id == localTask.id }
                    if (remoteMatch == null) {
                        val decryptedTask = if (encrypted) {
                            localTask.copy(
                                title = CryptoUtils.decrypt(localTask.title),
                                description = CryptoUtils.decrypt(localTask.description)
                            )
                        } else {
                            localTask
                        }
                        firebaseService.saveTask(decryptedTask)
                    }
                }
                Result.success(Unit)
            } else {
                Result.failure(remoteResult.exceptionOrNull() ?: Exception("Erro ao buscar registros da nuvem."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Categories Selection
    fun getCategories(): Flow<List<Category>> {
        return taskDao.getAllCategories().map { list ->
            list.ifEmpty { Category.DEFAULT_CATEGORIES }
        }
    }

    suspend fun insertCategory(category: Category) {
        taskDao.insertCategory(category)
    }
}
