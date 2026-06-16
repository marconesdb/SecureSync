package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.SecureDataStore
import com.example.data.local.SecureSyncDatabase
import com.example.data.remote.FirebaseService
import com.example.data.repository.AuthRepository
import com.example.data.repository.TaskRepository
import com.example.ui.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize local persistent engines and remote services
        val database = SecureSyncDatabase.getDatabase(applicationContext)
        val secureDataStore = SecureDataStore(applicationContext)
        val firebaseService = FirebaseService(applicationContext)

        // 2. Initialize unified operational repositories
        val authRepository = AuthRepository(firebaseService, database.userDao(), database.taskDao(), secureDataStore)
        val taskRepository = TaskRepository(database.taskDao(), firebaseService, secureDataStore)

        // 3. Coordinate VM creation via standard Provider Factory
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(AuthViewModel::class.java) -> 
                        AuthViewModel(authRepository) as T
                    modelClass.isAssignableFrom(TaskViewModel::class.java) -> 
                        TaskViewModel(taskRepository) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> 
                        SettingsViewModel(secureDataStore) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }

        val authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]
        val taskViewModel = ViewModelProvider(this, factory)[TaskViewModel::class.java]
        val settingsViewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            // Determine active Theme preference in real-time
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme() // "SYSTEM" default
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    // Full screen Jetpack Compose Safe Area Layout with AppNavigation
                    AppNavigation(
                        authViewModel = authViewModel,
                        taskViewModel = taskViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
