package edu.ku.kuaistudy3

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import java.util.UUID
import java.util.Timer
import java.util.TimerTask
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject
import kotlin.collections.ArrayList


class Message(
    val isMe: Boolean,
    var messageContent: String,
    var name: String? = FirebaseAuth.getInstance().currentUser?.displayName,
    var state: MessageState = MessageState.SENT,
    var timestamp: Date = Date(),
    var lineNumber: Int? = null,
    var isComplete: Boolean? = null
) {
    @DocumentId
    var docId: String? = null

    val id: UUID = UUID.randomUUID()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

enum class MessageState(val value: String) {
    SENT("Sent"),
    PROCESSING("Responding"),
    READ("Read")
}

class ChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient()

    val isSendingMessage = mutableStateOf(false)
    val messagesLoaded = mutableStateOf(false)
    val batchErrorMessage = mutableStateOf("")
    val batchMessageError = mutableStateOf(false)

    val messages = mutableStateListOf<Message>()
    val serverAddress = mutableStateOf("https://testing2.ittc.ku.edu")

    private var batchedMessages = ArrayList<String>()
    private var timer: Timer? = null
    private var typingListener: ListenerRegistration? = null

    init {
        loadMessages()
        loadServerAddress()
        setupTypingListener()
    }

    override fun onCleared() {
        super.onCleared()
        typingListener?.remove()
        timer?.cancel()
    }

    private fun loadServerAddress() {
        db.collection("ServerDetails").document("address")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    serverAddress.value = document.data?.get("value") as? String ?: "https://testing2.ittc.ku.edu"
                }
            }
            .addOnFailureListener { exception ->
                println("Error getting server address: $exception")
            }
    }

    private fun setupTypingListener() {
        val userID = auth.currentUser?.uid ?: return

        typingListener = db.collection("UserMessages").document(userID)
            .collection("typingStatus").document("bot")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val isTyping = snapshot?.getBoolean("isTyping") ?: false
                messages.lastOrNull { !it.isMe }?.state =
                    if (isTyping) MessageState.PROCESSING else MessageState.READ
            }
    }

    fun loadMessages() {
        val userID = auth.currentUser?.uid ?: run {
            println("User is not logged in")
            return
        }

        db.collection("UserMessages").document(userID).collection("messageItems")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Error listening for messages: $error")
                    return@addSnapshotListener
                }

                val docs = snapshot?.documents ?: run {
                    println("No documents")
                    return@addSnapshotListener
                }

                val newMessages = docs.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null

                    val isMe = data["isMe"] as? Boolean ?: false
                    val messageContent = data["messageContent"] as? String ?: ""
                    val name = data["name"] as? String
                    val timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
                    val lineNumber = data["lineNumber"] as? Int
                    val isComplete = data["isComplete"] as? Boolean

                    Message(
                        isMe = isMe,
                        messageContent = messageContent,
                        name = name,
                        state = if (isMe) MessageState.SENT else MessageState.READ,
                        timestamp = timestamp,
                        lineNumber = lineNumber,
                        isComplete = isComplete
                    )
                }

                messages.clear()
                messages.addAll(newMessages)
                messagesLoaded.value = true
            }
    }

    fun sendMessage(message: String) {
        if (message.isEmpty()) return

        val newMessage = Message(
            isMe = true,
            messageContent = message,
            state = MessageState.SENT
        )
        messages.add(newMessage)

        val userMessageData = hashMapOf<String, Any>(
            "isMe" to true,
            "messageContent" to message,
            "name" to (auth.currentUser?.displayName ?: ""),
            "timestamp" to FieldValue.serverTimestamp()
        )

        val userID = auth.currentUser?.uid ?: ""
        db.collection("UserMessages").document(userID)
            .collection("messageItems")
            .add(userMessageData)
            .addOnFailureListener { e ->
                println("Error saving user message: $e")
            }

        batchedMessages.add(message)
        timer?.cancel()

        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    sendBatchedMessages()
                }
            }, 7000)
        }
    }

    private fun sendBatchedMessages() {
        val combinedMessage = batchedMessages.joinToString(" ")
        if (combinedMessage.isEmpty()) return

        messages.lastOrNull { it.isMe }?.state = MessageState.PROCESSING
        batchedMessages.clear()

        val json = JSONObject().apply {
            put("user_id", auth.currentUser?.uid ?: "")
            put("message", combinedMessage)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${serverAddress.value}/iOSMessage")
            .post(requestBody)
            .build()

        isSendingMessage.value = true

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                batchErrorMessage.value = e.message ?: "Unknown error"
                batchMessageError.value = true
                isSendingMessage.value = false
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    markLastSentMessageAsRead()
                } else {
                    batchErrorMessage.value = response.message
                    batchMessageError.value = true
                }
                isSendingMessage.value = false
            }
        })
    }

    fun markLastSentMessageAsRead() {
        messages.lastOrNull { it.isMe }?.state = MessageState.READ
    }
}