package com.afterlight.feature.auth.domain

import android.content.Context
import com.afterlight.core.security.PartyKeyStore
import com.afterlight.data.local.MediaFilePaths
import com.afterlight.data.local.dao.FaceDao
import com.afterlight.data.local.dao.MediaDao
import com.afterlight.data.local.dao.PartyDao
import com.afterlight.data.local.dao.SyncStateDao
import com.afterlight.data.local.dao.UserDao
import com.afterlight.data.local.model.UserEntity
import com.afterlight.data.remote.firebase.FirebaseAuthService
import com.afterlight.data.remote.dto.LoginRequest
import com.afterlight.data.remote.dto.RegisterRequest
import com.afterlight.feature.auth.data.SecureTokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication repository.
 * Stage 13: Login, register, logout with Firebase Authentication.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuthService: FirebaseAuthService,
    private val tokenProvider: SecureTokenProvider,
    private val partyKeyStore: PartyKeyStore,
    private val userDao: UserDao,
    private val partyDao: PartyDao,
    private val mediaDao: MediaDao,
    private val faceDao: FaceDao,
    private val syncStateDao: SyncStateDao
) {
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: Flow<AuthState> = _authState.asStateFlow()
    
    init {
        // Check if user is already authenticated (Firebase persists sessions)
        if (firebaseAuthService.getCurrentUser() != null) {
            _authState.value = AuthState.Authenticated
        }
    }
    
    /**
     * Authenticates user with email and password using Firebase.
     */
    suspend fun login(email: String, password: String): Result<AuthState> {
        return try {
            val result = firebaseAuthService.login(LoginRequest(email, password))
            
            result.fold(
                onSuccess = { loginResponse ->
                    // Save tokens
                    tokenProvider.saveTokens(
                        loginResponse.accessToken,
                        loginResponse.refreshToken
                    )
                    
                    // Save user to local database
                    userDao.insert(
                        UserEntity(
                            id = loginResponse.userId,
                            email = email,
                            displayName = null,
                            createdAt = Clock.System.now()
                        )
                    )
                    
                    _authState.value = AuthState.Authenticated
                    Result.success(AuthState.Authenticated)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Registers new user account using Firebase.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String?
    ): Result<Unit> {
        return try {
            val result = firebaseAuthService.register(
                RegisterRequest(
                    email = email,
                    password = password,
                    displayName = displayName
                )
            )
            
            result.fold(
                onSuccess = { registerResponse ->
                    // Save tokens
                    tokenProvider.saveTokens(
                        registerResponse.accessToken,
                        registerResponse.refreshToken
                    )
                    
                    // Save user to local database
                    userDao.insert(
                        UserEntity(
                            id = registerResponse.userId,
                            email = email,
                            displayName = displayName,
                            createdAt = Clock.System.now()
                        )
                    )
                    
                    _authState.value = AuthState.Authenticated
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Logs out user from Firebase and clears all local data.
     * Stage 13: Clears all 5 database tables in correct order.
     */
    suspend fun logout() {
        try {
            // Logout from Firebase
            firebaseAuthService.logout()
        } catch (e: Exception) {
            // Ignore Firebase logout errors
        }
        
        tokenProvider.clearTokens()
        partyKeyStore.deleteAll()
        MediaFilePaths.deleteAllPartyFiles(context.filesDir)
        
        // Clear database in correct order (respects foreign keys)
        faceDao.deleteAll()
        syncStateDao.deleteAll()
        mediaDao.deleteAll()
        partyDao.deleteAll()
        userDao.deleteAll()
        
        _authState.value = AuthState.Unauthenticated
    }
}

sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
}
