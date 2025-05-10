package edu.ku.kuaistudy3

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpView(navController: NavController) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // State variables
    var firstName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    var error by remember { mutableStateOf("") }
    var showSignUpError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            navController.navigate("chat") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Create Account") },
//                navigationIcon = {
//                    IconButton(onClick = { navController.popBackStack() }) {
//                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
//                    }
//                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Logo
            Image(
                painter = painterResource(id = R.drawable.ai_v_in),
                contentDescription = "App Logo",
                modifier = Modifier.size(120.dp)
            )

            Text(
                text = "Create your account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // First name field
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            // Email field
            OutlinedTextField(
                value = userEmail,
                onValueChange = { userEmail = it.lowercase() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            // Password field
            OutlinedTextField(
                value = userPass,
                onValueChange = { userPass = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )

            // Confirm password field
            OutlinedTextField(
                value = confirmPass,
                onValueChange = { confirmPass = it },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sign up button
            Button(
                onClick = {
                    signUp(
                        firstName = firstName,
                        userEmail = userEmail,
                        userPass = userPass,
                        confirmPass = confirmPass,
                        onLoading = { isLoading = it },
                        onError = { errorMsg ->
                            error = errorMsg
                            showSignUpError = true
                        },
                        onSuccess = {
                            navController.navigate("chat") {
                                popUpTo("signup") { inclusive = true }
                            }
                        },
                        context = context
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Sign Up", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Sign in link
            TextButton(
                onClick = { navController.navigate("login") }
            ) {
                Text("Already have an account? Sign in")
            }
        }
    }

    // Error dialog
    if (showSignUpError) {
        AlertDialog(
            onDismissRequest = { showSignUpError = false },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { showSignUpError = false }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun signUp(
    firstName: String,
    userEmail: String,
    userPass: String,
    confirmPass: String,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit,
    context: android.content.Context
) {
    // Validation checks
    if (firstName.isBlank()) {
        onError("Please enter your first name")
        return
    }

    if (userEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
        onError("Please enter a valid email address")
        return
    }

    if (userPass.length < 6) {
        onError("Password must be at least 6 characters")
        return
    }

    if (userPass != confirmPass) {
        onError("Passwords do not match")
        return
    }

    onLoading(true)

    val auth = FirebaseAuth.getInstance()
    auth.createUserWithEmailAndPassword(userEmail, userPass)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Update profile with first name
                val user = auth.currentUser
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(firstName)
                    .build()

                user?.updateProfile(profileUpdates)
                    ?.addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            // Send welcome message and add user to prompt group
                            user.uid.let { userId ->
                                val db = FirebaseFirestore.getInstance()

                                // Welcome message
                                val welcomeMessage = hashMapOf(
                                    "isMe" to false,
                                    "messageContent" to "Hi, I'm OwO Bot. Thanks for taking part in this study. Please send a message whenever you would like to start the chat. Thank you.",
                                    "name" to "Bot",
                                    "timestamp" to Date()
                                )

                                db.collection("UserMessages")
                                    .document(userId)
                                    .collection("messageItems")
                                    .add(welcomeMessage)

                                // Add user to prompt group
                                val promptType = hashMapOf("promptType" to "Default")
                                db.collection("UserPromptTypes")
                                    .document(userId)
                                    .set(promptType)
                                    .addOnSuccessListener {
                                        onLoading(false)
                                        onSuccess()
                                        Toast.makeText(context, "Account created successfully", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        onLoading(false)
                                        onError(e.localizedMessage ?: "Failed to complete setup")
                                    }
                            }
                        } else {
                            onLoading(false)
                            onError(profileTask.exception?.localizedMessage ?: "Failed to update profile")
                        }
                    }
            } else {
                onLoading(false)
                onError(task.exception?.localizedMessage ?: "Authentication failed. ${task.exception?.message}")
            }
        }
}