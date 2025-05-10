package edu.ku.kuaistudy3

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.auth.User
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatView(navController: NavController, viewModel: ChatViewModel = viewModel()) {
    val messages = viewModel.messages
    val isSendingMessage = viewModel.isSendingMessage.value
    val messagesLoaded by viewModel.messagesLoaded
    var typingMessage by remember { mutableStateOf(TextFieldValue()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val auth = FirebaseAuth.getInstance()

    // Track auth state changes
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    // Handle auth state listener cleanup
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // Load messages when auth state changes
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.loadMessages()
        }
    }

    // 3. Existing auto-scroll effect
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chat") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                reverseLayout = false
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isLastFromSender = messages.lastOrNull { it.isMe == message.isMe } == message,
                        isTyping = !message.isMe && message.state == MessageState.PROCESSING,
                        modifier = Modifier.animateItemPlacement()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (isSendingMessage) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            MessageInputBar(
                message = typingMessage,
                onMessageChange = { typingMessage = it },
                onSend = {
                    if (typingMessage.text.isNotBlank()) {
                        viewModel.sendMessage(typingMessage.text)
                        typingMessage = TextFieldValue("")
                        keyboardController?.hide()
                    }
                },
                isSending = isSendingMessage
            )
        }
    }

    if (viewModel.batchMessageError.value) {
        AlertDialog(
            onDismissRequest = { viewModel.batchMessageError.value = false },
            title = { Text("There Was An Issue") },
            text = { Text(viewModel.batchErrorMessage.value) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.batchMessageError.value = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
}
@Composable
fun MessageInputBar(
    message: TextFieldValue,
    onMessageChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                placeholder = { Text("Message...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine = false,
                maxLines = 3
            )

            AnimatedVisibility(visible = message.text.isNotBlank()) {
                IconButton(
                    onClick = onSend,
                    enabled = !isSending,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send message",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isLastFromSender: Boolean,
    isTyping: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (message.isMe) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (message.isMe) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (message.isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Message bubble
            Surface(
                shape = when {
                    message.isMe && isLastFromSender -> RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                    message.isMe -> RoundedCornerShape(16.dp, 4.dp, 4.dp, 16.dp)
                    !message.isMe && isLastFromSender -> RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                    else -> RoundedCornerShape(4.dp, 16.dp, 16.dp, 4.dp)
                },
                color = bubbleColor,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = message.messageContent,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Status indicator or typing animation
            if (isLastFromSender) {
                Spacer(modifier = Modifier.height(4.dp))

                if (message.isMe) {
                    Text(
                        text = message.state.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else if (isTyping) {
                    TypingIndicator()
                } else {
                    Image(
                        painter = painterResource(R.drawable.ai_w),
                        contentDescription = "AI Assistant",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val dotSize = 8.dp
    val animatedDot = animateIntAsState(
        targetValue = 3,
        animationSpec = tween(durationMillis = 1000, delayMillis = 0),
        label = "typingIndicator"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (index < animatedDot.value) 1f else 0.3f
                        )
                    )
            )
        }
    }
}



@Composable
fun rememberKeyboardOpenState(): State<Boolean> {
    val keyboardState = remember { mutableStateOf(false) }
    val view = LocalView.current

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            keyboardState.value = keypadHeight > screenHeight * 0.15
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(listener)

        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    return keyboardState
}