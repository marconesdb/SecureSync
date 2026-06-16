package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.TaskRepository
import com.example.domain.model.Category
import com.example.domain.model.Task
import com.example.domain.model.TaskPriority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

sealed class TaskUiState {
    object Loading : TaskUiState()
    data class Success(val tasks: List<Task>) : TaskUiState()
    data class Error(val message: String) : TaskUiState()
}

class TaskViewModel(private val taskRepository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskUiState>(TaskUiState.Loading)
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(Category.DEFAULT_CATEGORIES)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Dashboard Filters / Searching State
    val searchQuery = MutableStateFlow("")
    val filterPriority = MutableStateFlow<TaskPriority?>(null)
    val filterCategory = MutableStateFlow<String?>(null)
    val sortAscending = MutableStateFlow(true)

    // Editing State (Temporary holders for task additions/modifications)
    val editTitle = MutableStateFlow("")
    val editDescription = MutableStateFlow("")
    val editCategory = MutableStateFlow("personal")
    val editPriority = MutableStateFlow(TaskPriority.MEDIUM)
    val editDueDate = MutableStateFlow(System.currentTimeMillis())

    private var activeUserId: String? = null

    init {
        viewModelScope.launch {
            taskRepository.getCategories().collect { list ->
                _categories.value = list
            }
        }
    }

    fun loadTasks(userId: String) {
        activeUserId = userId
        _uiState.value = TaskUiState.Loading
        viewModelScope.launch {
            taskRepository.getTasksForUser(userId)
                .catch { e ->
                    _uiState.value = TaskUiState.Error(e.localizedMessage ?: "Erro ao carregar dados")
                }
                .collectLatest { list ->
                    _uiState.value = TaskUiState.Success(list)
                }
        }
    }

    fun syncDatabase() {
        val uid = activeUserId ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            taskRepository.syncWithRemote(uid)
                .onSuccess {
                    _snackbarMessage.emit("Banco sincronizado com sucesso!")
                }
                .onFailure { error ->
                    _snackbarMessage.emit("Sync Local: ${error.localizedMessage}")
                }
            _isRefreshing.value = false
        }
    }

    fun addTask() {
        val uid = activeUserId ?: return
        val title = editTitle.value.trim()
        val desc = editDescription.value.trim()
        
        if (title.isEmpty()) {
            viewModelScope.launch { _snackbarMessage.emit("O título da tarefa não pode estar vazio.") }
            return
        }

        viewModelScope.launch {
            val task = Task(
                id = UUID.randomUUID().toString(),
                userId = uid,
                title = title,
                description = desc,
                categoryId = editCategory.value,
                isCompleted = false,
                priority = editPriority.value,
                dueDate = editDueDate.value,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            taskRepository.insertTask(task)
                .onSuccess {
                    _snackbarMessage.emit("Tarefa criada com sucesso!")
                    clearForm()
                }
                .onFailure { error ->
                    _snackbarMessage.emit("Salvo localmente (Offline: ${error.localizedMessage})")
                    clearForm()
                }
        }
    }

    fun updateTaskStatus(task: Task, isCompleted: Boolean) {
        viewModelScope.launch {
            val updated = task.copy(
                isCompleted = isCompleted,
                updatedAt = System.currentTimeMillis()
            )
            taskRepository.insertTask(updated)
                .onFailure { error ->
                    _snackbarMessage.emit("Status atualizado offline.")
                }
        }
    }

    fun startEditing(task: Task) {
        editTitle.value = task.title
        editDescription.value = task.description
        editCategory.value = task.categoryId
        editPriority.value = task.priority
        editDueDate.value = task.dueDate
    }

    fun updateTask(task: Task) {
        val title = editTitle.value.trim()
        val desc = editDescription.value.trim()
        if (title.isEmpty()) {
            viewModelScope.launch { _snackbarMessage.emit("O título não pode estar vazio.") }
            return
        }

        viewModelScope.launch {
            val updated = task.copy(
                title = title,
                description = desc,
                categoryId = editCategory.value,
                priority = editPriority.value,
                dueDate = editDueDate.value,
                updatedAt = System.currentTimeMillis()
            )
            taskRepository.insertTask(updated)
                .onSuccess {
                    _snackbarMessage.emit("Tarefa atualizada!")
                    clearForm()
                }
                .onFailure { error ->
                    _snackbarMessage.emit("Atualizado offline.")
                    clearForm()
                }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
                .onSuccess {
                    _snackbarMessage.emit("Tarefa removida com sucesso!")
                }
                .onFailure { error ->
                    _snackbarMessage.emit("Tarefa deletada localmente.")
                }
        }
    }

    fun clearForm() {
        editTitle.value = ""
        editDescription.value = ""
        editCategory.value = "personal"
        editPriority.value = TaskPriority.MEDIUM
        editDueDate.value = System.currentTimeMillis()
    }
}
