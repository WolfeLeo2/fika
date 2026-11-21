package com.wolfeleo2.fika

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wolfeleo2.fika.auth.AuthRepository
import com.wolfeleo2.fika.auth.SessionState
import com.wolfeleo2.fika.auth.SignInRoute
import com.wolfeleo2.fika.theme.FikaTheme
import com.wolfeleo2.fika.theme.rememberDarkTheme
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App() {
    FikaTheme(darkTheme = rememberDarkTheme()) {
        Surface(Modifier.fillMaxSize()) {
            val session by koinInject<AuthRepository>().sessionState.collectAsState(SessionState.Loading)
            when (session) {
                SessionState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator() }
                SessionState.SignedOut -> SignInRoute()
                SessionState.SignedIn -> AppNav()
            }
        }
    }
}
