package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SecureDataStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val secureDataStore: SecureDataStore) : ViewModel() {

    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _firebaseEnabled = MutableStateFlow(false)
    val firebaseEnabled: StateFlow<Boolean> = _firebaseEnabled.asStateFlow()

    private val _localEncryptionEnabled = MutableStateFlow(false)
    val localEncryptionEnabled: StateFlow<Boolean> = _localEncryptionEnabled.asStateFlow()

    private val _settingsSnackbar = MutableSharedFlow<String>()
    val settingsSnackbar: SharedFlow<String> = _settingsSnackbar.asSharedFlow()

    init {
        viewModelScope.launch {
            secureDataStore.darkModeFlow.collect { _themeMode.value = it }
        }
        viewModelScope.launch {
            secureDataStore.firebaseEnabledFlow.collect { _firebaseEnabled.value = it }
        }
        viewModelScope.launch {
            secureDataStore.localEncryptionFlow.collect { _localEncryptionEnabled.value = it }
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            secureDataStore.setDarkMode(mode)
            _themeMode.value = mode
            _settingsSnackbar.emit("Tema atualizado para: $mode")
        }
    }

    fun setFirebaseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            secureDataStore.setFirebaseEnabled(enabled)
            _firebaseEnabled.value = enabled
            val text = if (enabled) "Modo Firebase Habilitado! Insira suas credenciais se necessário." else "Modo de Armazenamento Local Seguro Ativado!"
            _settingsSnackbar.emit(text)
        }
    }

    fun setLocalEncryption(enabled: Boolean) {
        viewModelScope.launch {
            secureDataStore.setLocalEncryption(enabled)
            _localEncryptionEnabled.value = enabled
            val text = if (enabled) "Criptografia local AES-128 ligada! Dados salvos no dispositivo agora são criptografados." else "Criptografia local AES-128 desligada."
            _settingsSnackbar.emit(text)
        }
    }
}
