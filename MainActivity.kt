package com.yadav.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF10243B)
private val Cyan = Color(0xFF53D8FB)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
        setContent { JarvisScreen(onSpeak = { text, language -> speak(text, language) }) }
    }

    private fun speak(text: String, language: String) {
        val locale = when (language) {
            "नेपाली" -> Locale("ne", "NP")
            "हिन्दी" -> Locale("hi", "IN")
            else -> Locale.US
        }
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun JarvisScreen(onSpeak: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf("English") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready when you are, Sir.") }
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>() }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "Speech recognition is unavailable on this device."
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, when (language) {
                "नेपाली" -> "ne-NP"
                "हिन्दी" -> "hi-IN"
                else -> "en-US"
            })
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status = "Listening…" }
            override fun onBeginningOfSpeech() { status = "Listening…" }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status = "Processing speech…" }
            override fun onError(error: Int) {
                status = "Could not recognize speech. Try again or type your message."
                recognizer.destroy()
            }
            override fun onResults(results: Bundle?) {
                input = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                status = if (input.isBlank()) "No speech detected." else "Voice captured."
                recognizer.destroy()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else status = "Microphone permission is required for voice input."
    }

    suspend fun askBackend(prompt: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(BuildConfig.BASE_URL.trimEnd('/') + "/chat").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 45000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            val payload = JSONObject()
                .put("message", prompt)
                .put("language", language)
                .toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw Exception("Backend error $code: $body")
            JSONObject(body).optString("reply", "I couldn't form a reply.")
        } finally {
            connection.disconnect()
        }
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isEmpty() || busy) return
        messages.add(prompt to true)
        input = ""
        busy = true
        status = "JARVIS is thinking…"
        scope.launch {
            try {
                val reply = askBackend(prompt)
                messages.add(reply to false)
                status = "Response received."
                onSpeak(reply, language)
            } catch (e: Exception) {
                val msg = "I couldn't connect to the AI server. Check the backend URL and internet connection."
                messages.add(msg to false)
                status = e.message ?: "Connection failed."
            } finally {
                busy = false
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, background = Navy, surface = Panel,
        onPrimary = Navy, onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Navy).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("J.A.R.V.I.S", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = Cyan)
            Text("PERSONAL AI ASSISTANT", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Good day, Sir.", fontWeight = FontWeight.SemiBold)
                    Text(status, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Response language", color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("English", "हिन्दी", "नेपाली").forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = { language = option },
                        label = { Text(option) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { (text, isUser) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (isUser) Color(0xFF174C68) else Panel,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Text(text, Modifier.padding(12.dp), color = Color.White)
                        }
                    }
                }
                if (busy) item { CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp)) }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask me anything, Sir…") },
                enabled = !busy,
                maxLines = 4
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startListening()
                        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Speak")
                    Spacer(Modifier.width(6.dp))
                    Text("Speak")
                }
                Button(
                    onClick = { send() },
                    enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                    Spacer(Modifier.width(6.dp))
                    Text("Send")
                }
            }
        }
    }
}
