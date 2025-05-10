package edu.ku.kuaistudy3

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.BuildConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.tasks.await




class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val chatViewModel = viewModel<ChatViewModel>()
            val context = LocalContext.current

            // State for version info and dialog
            val (showUpdateDialog, setShowUpdateDialog) = remember { mutableStateOf(false) }
            val currentVersion = remember { mutableStateOf("") }
            val minVersion = remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                checkAppVersion(context) { needsUpdate, currentVer, minVer ->
                    currentVersion.value = currentVer
                    minVersion.value = minVer
                    setShowUpdateDialog(needsUpdate)
                }
            }

            MaterialTheme {
                Surface {
                    Box {
                        NavHost(
                            navController = navController,
                            startDestination = if (FirebaseAuth.getInstance().currentUser != null) "chat" else "signup"
                        ) {
                            composable("login") { LoginView(navController) }
                            composable("signup") { SignUpView(navController) }
                            composable("chat") { ChatView(navController, chatViewModel) }
                        }

                        if (showUpdateDialog) {
                            AlertDialog(
                                onDismissRequest = { /* Force exit */ },
                                title = { Text("Update Required") },
                                text = {
                                    Column {
                                        Text("Your current version: ${currentVersion.value}")
                                        Spacer(Modifier.height(8.dp))
                                        Text("Minimum required version: ${minVersion.value}")
                                        Spacer(Modifier.height(8.dp))
                                        Text("Please contact lab personnel for assistance")
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { finishAffinity() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Exit App")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun checkAppVersion(
        context: Context,
        callback: (Boolean, String, String) -> Unit
    ) {
        try {
            val remoteConfig = Firebase.remoteConfig.apply {
                setConfigSettingsAsync(remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600
                })
                setDefaultsAsync(mapOf(
                    "minimum_version_name" to "\"1.1\""
                ))
                fetchAndActivate().await()
            }

            val minVersion = remoteConfig.getString("minimum_version_name").removeSurrounding("\"")
            val currentVersion = getAppVersionName(context)

            println("Version Check: Current=$currentVersion, Minimum=$minVersion")
            callback(
                isVersionOutdated(currentVersion, minVersion),
                currentVersion,
                minVersion
            )
        } catch (e: Exception) {
            println("Version check failed: ${e.message}")
            callback(false, "1.0", "1.1")
        }
    }

    private fun getAppVersionName(context: Context): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?.takeIf { it.isNotBlank() }
                ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isVersionOutdated(current: String, minimum: String): Boolean {
        return try {
            val currentParts = current.split('.').take(2).map { it.toInt() }
            val minParts = minimum.split('.').take(2).map { it.toInt() }

            currentParts[0] < minParts[0] ||
                    (currentParts[0] == minParts[0] && currentParts[1] < minParts[1])
        } catch (e: Exception) {
            println("Version parse error: ${e.message}")
            false
        }
    }
}