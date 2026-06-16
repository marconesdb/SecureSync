package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.domain.model.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val currentUserId: StateFlow<String?> = MutableStateFlow<String?>(null).apply {
        viewModelScope.launch {
            authRepository.currentUserId.collect { value ->
                this@apply.value = value
                if (value != null) {
                    // Check if authenticated
                    _authState.value = AuthState.Idle // Clear previous error
                } else {
                    _authState.value = AuthState.Idle
                }
            }
        }
    }

    val currentUserEmail: StateFlow<String?> = MutableStateFlow<String?>(null).apply {
        viewModelScope.launch {
            authRepository.currentUserEmail.collect { value = it }
        }
    }

    val currentUserName: StateFlow<String?> = MutableStateFlow<String?>(null).apply {
        viewModelScope.launch {
            authRepository.currentUserName.collect { value = it }
        }
    }

    val isFirebaseMode: StateFlow<Boolean> = MutableStateFlow(false).apply {
        viewModelScope.launch {
            authRepository.isFirebaseModeEnabled.collect { value = it }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
            _authState.value = AuthState.Error("Preencha todos os campos obrigatórios.")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.signUp(email, password, displayName)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    _snackbarMessage.emit("Cadastro realizado com sucesso!")
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.localizedMessage ?: "Erro desconhecido")
                    _snackbarMessage.emit("Erro: ${error.localizedMessage}")
                }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("E-mail e senha são obrigatórios.")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.signIn(email, password)
                .onSuccess { user ->
                    _authState.value = AuthState.Authenticated(user)
                    _snackbarMessage.emit("Bem-vindo de volta, ${user.displayName}!")
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(error.localizedMessage ?: "Credenciais inválidas")
                    _snackbarMessage.emit("Falha no login: ${error.localizedMessage}")
                }
        }
    }

    fun recoverPassword(email: String) {
        if (email.isBlank()) {
            viewModelScope.launch { _snackbarMessage.emit("Digite seu e-mail para recuperar a senha.") }
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _snackbarMessage.emit("E-mail de recuperação enviado! Verifique sua caixa de entrada.")
                }
                .onFailure { error ->
                    _snackbarMessage.emit("Erro ao enviar e-mail: ${error.localizedMessage}")
                }
        }
    }

    fun setFirebaseModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            authRepository.setFirebaseModeEnabled(enabled)
            _authState.value = AuthState.Idle
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Idle
            _snackbarMessage.emit("Sessão encerrada com sucesso.")
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}
