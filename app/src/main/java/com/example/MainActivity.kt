package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.ui.screens.MainShell
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.SignupScreen
import com.example.ui.screens.auth.WelcomeScreen
import com.example.ui.theme.AlreadyMusicTheme

enum class AuthDestination {
    WELCOME, LOGIN, SIGNUP, MAIN
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AlreadyApp
        val repository = app.repository
        val audioController = app.audioController

        setContent {
            AlreadyMusicTheme {
                val initialAuth = remember {
                    if (repository.authPreferences.isLoggedIn()) {
                        AuthDestination.MAIN
                    } else {
                        AuthDestination.WELCOME
                    }
                }
                var currentDestination by remember { mutableStateOf(initialAuth) }

                when (currentDestination) {
                    AuthDestination.WELCOME -> {
                        WelcomeScreen(
                            onNavigateToLogin = { currentDestination = AuthDestination.LOGIN },
                            onNavigateToSignup = { currentDestination = AuthDestination.SIGNUP },
                            onContinueAsGuest = { currentDestination = AuthDestination.MAIN }
                        )
                    }

                    AuthDestination.LOGIN -> {
                        LoginScreen(
                            repository = repository,
                            onBack = { currentDestination = AuthDestination.WELCOME },
                            onLoginSuccess = { currentDestination = AuthDestination.MAIN },
                            onNavigateToSignup = { currentDestination = AuthDestination.SIGNUP }
                        )
                    }

                    AuthDestination.SIGNUP -> {
                        SignupScreen(
                            repository = repository,
                            onBack = { currentDestination = AuthDestination.WELCOME },
                            onSignupSuccess = { currentDestination = AuthDestination.MAIN },
                            onNavigateToLogin = { currentDestination = AuthDestination.LOGIN }
                        )
                    }

                    AuthDestination.MAIN -> {
                        MainShell(
                            repository = repository,
                            audioController = audioController,
                            onLogout = { currentDestination = AuthDestination.WELCOME }
                        )
                    }
                }
            }
        }
    }
}
