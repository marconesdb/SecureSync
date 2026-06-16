package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Category
import com.example.domain.model.Task
import com.example.domain.model.TaskPriority
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.TaskUiState
import com.example.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    authViewModel: AuthViewModel,
    taskViewModel: TaskViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTaskForm: (String) -> Unit
) {
    val context = LocalContext.current
    val currentUserId by authViewModel.currentUserId.collectAsState()
    val currentUserName by authViewModel.currentUserName.collectAsState()
    
    val uiState by taskViewModel.uiState.collectAsState()
    val categories by taskViewModel.categories.collectAsState()
    val isRefreshing by taskViewModel.isRefreshing.collectAsState()

    // Filters
    val searchQuery by taskViewModel.searchQuery.collectAsState()
    val filterPriority by taskViewModel.filterPriority.collectAsState()
    val filterCategory by taskViewModel.filterCategory.collectAsState()
    val sortAscending by taskViewModel.sortAscending.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val isFirebaseMode by authViewModel.isFirebaseMode.collectAsState()

    // Trigger loading of user tasks on start
    LaunchedEffect(currentUserId, isFirebaseMode) {
        currentUserId?.let { uid ->
            taskViewModel.loadTasks(uid)
            if (isFirebaseMode) {
                taskViewModel.syncDatabase()
            }
        }
    }

    // Capture snackbars
    LaunchedEffect(Unit) {
        taskViewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        val welcomeText = if (!currentUserName.isNullOrBlank()) {
                            "Olá, $currentUserName 🎯"
                        } else {
                            "Minhas Atividades 🎯"
                        }
                        Text(
                            text = welcomeText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "Acompanhamento em tempo real",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { taskViewModel.syncDatabase() },
                        modifier = Modifier.testTag("sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sincronizar",
                            tint = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    taskViewModel.clearForm()
                    onNavigateToTaskForm("new")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .testTag("add_task_fab")
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Atividade", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = uiState) {
                is TaskUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is TaskUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Button(onClick = { currentUserId?.let { taskViewModel.loadTasks(it) } }) {
                                Text("Tentar Novamente")
                            }
                        }
                    }
                }
                is TaskUiState.Success -> {
                    val allTasks = state.tasks

                    // Apply active filters on memory
                    val filteredTasks = allTasks.filter { task ->
                        val matchesSearch = task.title.contains(searchQuery, ignoreCase = true) || 
                                           task.description.contains(searchQuery, ignoreCase = true)
                        val matchesPriority = filterPriority == null || task.priority == filterPriority
                        val matchesCategory = filterCategory == null || task.categoryId == filterCategory
                        matchesSearch && matchesPriority && matchesCategory
                    }.sortedWith { t1, t2 ->
                        val res = if (sortAscending) t1.dueDate.compareTo(t2.dueDate) else t2.dueDate.compareTo(t1.dueDate)
                        if (res == 0) t2.createdAt.compareTo(t1.createdAt) else res
                    }

                    // Pre-calculate statistics
                    val totalTasks = filteredTasks.size
                    val completedTasks = filteredTasks.count { it.isCompleted }
                    val progress = if (totalTasks > 0) completedTasks.toFloat() / totalTasks.toFloat() else 0f

                    // Header Dashboard panel (Canvas drawing)
                    DashboardHeaderCard(
                        total = totalTasks,
                        completed = completedTasks,
                        progress = progress,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Operational Filters bar
                    SearchBarRow(
                        searchQuery = searchQuery,
                        onSearchChange = { taskViewModel.searchQuery.value = it },
                        sortAscending = sortAscending,
                        onSortToggle = { taskViewModel.sortAscending.value = !sortAscending },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    FilterChipsRow(
                        categories = categories,
                        selectedCategory = filterCategory,
                        onSelectCategory = { taskViewModel.filterCategory.value = it },
                        selectedPriority = filterPriority,
                        onSelectPriority = { taskViewModel.filterPriority.value = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    if (filteredTasks.isEmpty()) {
                        EmptyStatePanel(
                            hasFilters = searchQuery.isNotEmpty() || filterPriority != null || filterCategory != null,
                            onClearFilters = {
                                taskViewModel.searchQuery.value = ""
                                taskViewModel.filterPriority.value = null
                                taskViewModel.filterCategory.value = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Main vertical LazyColumn
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("task_lazy_column"),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredTasks, key = { it.id }) { task ->
                                val category = categories.find { it.id == task.categoryId } ?: Category("other", "Outros", "#8E8E93", "folder")
                                TaskItemCard(
                                    task = task,
                                    category = category,
                                    onCheckChange = { isChecked ->
                                        taskViewModel.updateTaskStatus(task, isChecked)
                                    },
                                    onClick = {
                                        taskViewModel.startEditing(task)
                                        onNavigateToTaskForm(task.id)
                                    },
                                    onDelete = {
                                        taskViewModel.deleteTask(task)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardHeaderCard(
    total: Int,
    completed: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Progresso Diário",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$completed de $total tarefas concluídas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val percentage = (progress * 100).toInt()
                Text(
                    text = "$percentage% Concluído",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Custom radial progress ring Canvas
            val progressColor = MaterialTheme.colorScheme.primary
            val strokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Track circle
                    drawCircle(
                        color = strokeColor,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress arc
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = if (progress >= 1f) Icons.Default.CheckCircle else Icons.Default.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun SearchBarRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    sortAscending: Boolean,
    onSortToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Buscar atividades...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 56.dp)
                .testTag("search_field"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        IconButton(
            onClick = onSortToggle,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .testTag("sort_toggle_button")
        ) {
            Icon(
                imageVector = if (sortAscending) Icons.Default.SortByAlpha else Icons.Default.FilterList,
                contentDescription = "Inverter Ordem",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilterChipsRow(
    categories: List<Category>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    selectedPriority: TaskPriority?,
    onSelectPriority: (TaskPriority?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Categories list row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("Categorias (Todas)") },
                shape = RoundedCornerShape(12.dp)
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                    label = { Text(cat.name) },
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(android.graphics.Color.parseColor(cat.colorHex)), CircleShape)
                        )
                    }
                )
            }
        }

        // Priority list row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedPriority == null,
                onClick = { onSelectPriority(null) },
                label = { Text("Prioridade (Todas)") },
                shape = RoundedCornerShape(12.dp)
            )
            TaskPriority.values().forEach { prio ->
                val pColor = when (prio) {
                    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.secondary
                    TaskPriority.LOW -> MaterialTheme.colorScheme.outline
                }
                FilterChip(
                    selected = selectedPriority == prio,
                    onClick = { onSelectPriority(prio) },
                    label = { Text(prio.name) },
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(pColor, CircleShape)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    category: Category,
    onCheckChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(android.graphics.Color.parseColor(category.colorHex))
    val priorityIndicator = when (task.priority) {
        TaskPriority.HIGH -> "Alta"
        TaskPriority.MEDIUM -> "Média"
        TaskPriority.LOW -> "Baixa"
    }
    val priorityColor = when (task.priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
        TaskPriority.LOW -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onPriorityColor = when (task.priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.onErrorContainer
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
        TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val dFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dueDateForm = dFormat.format(Date(task.dueDate))

    var expandedConfirmation by remember { mutableStateOf(false) }

    if (expandedConfirmation) {
        AlertDialog(
            onDismissRequest = { expandedConfirmation = false },
            title = { Text("Excluir Atividade?") },
            text = { Text("Deseja realmente remover esta atividade? Essa ação é permanente tanto localmente quanto nos servidores remotos.") },
            confirmButton = {
                Button(
                    onClick = {
                        expandedConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { expandedConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("task_item_${task.id}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Visual check button target (minimum 48dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onCheckChange(!task.isCompleted) }
                    .testTag("checkbox_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Alternar Conclusão",
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Task Body details
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accentColor, CircleShape)
                    )
                    Text(
                        text = category.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Metadata markers (dueDate, priority banner)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = dueDateForm,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = priorityColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = priorityIndicator,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = onPriorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Garbage bin button target (minimum 48dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { expandedConfirmation = true }
                    .testTag("delete_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir Atividade",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStatePanel(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (hasFilters) Icons.Default.SearchOff else Icons.Default.Task,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = if (hasFilters) "Nenhum resultado encontrado" else "Tudo em paz por aqui!",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasFilters) "Experimente modificar os filtros ou apagar o texto de busca para encontrar suas tarefas." 
                       else "Não localizamos nenhuma tarefa anotada. Clique no botão '+' abaixo para cadastrar a sua primeira obrigação!",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasFilters) {
                Button(onClick = onClearFilters, shape = RoundedCornerShape(12.dp)) {
                    Text("Limpar Filtros")
                }
            }
        }
    }
}
