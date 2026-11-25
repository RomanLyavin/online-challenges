package com.onlinechallenges.app

import android.os.Bundle
import com.onlinechallenges.app.data.SupabaseAuth
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.onlinechallenges.app.ui.AppRoot
import com.onlinechallenges.app.ui.AppViewModel
import com.onlinechallenges.app.ui.theme.OnlineChallengesTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SupabaseAuth.prepare(intent)
        vm.handleAuthIntent(intent)
        setContent {
            OnlineChallengesTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SupabaseAuth.prepare(intent)
        vm.handleAuthIntent(intent)
    }
}
