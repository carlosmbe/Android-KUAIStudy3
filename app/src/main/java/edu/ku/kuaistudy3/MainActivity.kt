package edu.ku.kuaistudy3
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Use default Material3 theme
            Surface(color = MaterialTheme.colorScheme.background) {
                val navController = rememberNavController()
                val chatViewModel = viewModel<ChatViewModel>()

                // Determine start destination based on login status
                val startDestination = if (FirebaseAuth.getInstance().currentUser != null) {
                    "chat"
                } else {
                    "signup"
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable("login") {
                        LoginView(navController = navController)
                    }
                    composable("signup") {
                        SignUpView(navController = navController)
                    }
                    composable("chat") {
                        ChatView(navController = navController, viewModel = chatViewModel)
                    }
                }
            }
        }
    }
}