package ir.pardava.mobile.ui.screens.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.AiChatIn
import ir.pardava.mobile.data.dto.AiMessageIn
import ir.pardava.mobile.ui.screens.courses.SimpleVmFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** یک پیام در گفتگو با هوش مصنوعی (error فقط روی پاسخ‌های ناموفق). */
data class AiMsg(
    val role: String,
    val content: String,
    val isPending: Boolean = false,
    val error: String? = null,
)

sealed interface AiChatUiState {
    data object Idle : AiChatUiState
    data class Conversation(
        val messages: List<AiMsg> = emptyList(),
        val sending: Boolean = false,
        val error: String? = null,
    ) : AiChatUiState
}

class AiChatViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<AiChatUiState>(AiChatUiState.Conversation())
    val state: StateFlow<AiChatUiState> = _state

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty()) return
        val current = (_state.value as? AiChatUiState.Conversation) ?: AiChatUiState.Conversation()
        if (current.sending) return

        val history = current.messages.filter { !it.isPending && it.error == null }
        val messages = history + AiMsg(role = "user", content = question)
        _state.value = AiChatUiState.Conversation(
            messages = messages + AiMsg(role = "assistant", content = "", isPending = true),
            sending = true,
        )
        viewModelScope.launch {
            try {
                val payload = AiChatIn(
                    messages = messages.map { AiMessageIn(role = it.role, content = it.content) },
                )
                val resp = client.api.aiChat(payload)
                val answer = resp.response.orEmpty().ifBlank { "…" }
                replaceLast(answer, error = null)
            } catch (e: HttpException) {
                val serverError = runCatching {
                    val body = e.response()?.errorBody()?.string().orEmpty()
                    val json = client.json.parseToJsonElement(body)
                    (json as? kotlinx.serialization.json.JsonObject)?.get("error")
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                }.getOrNull()
                replaceLast(
                    serverError ?: clientErrorMessage(e.code()),
                    error = serverError ?: clientErrorMessage(e.code()),
                )
            } catch (e: Exception) {
                replaceLast(
                    e.message ?: "error",
                    error = e.message ?: "error",
                )
            }
        }
    }

    private suspend fun replaceLast(answer: String, error: String?) {
        val current = (_state.value as? AiChatUiState.Conversation) ?: return
        val updated = current.messages.dropLast(1) +
            AiMsg(role = "assistant", content = answer, error = error)
        _state.value = current.copy(messages = updated, sending = false)
    }

    private fun clientErrorMessage(code: Int): String = when (code) {
        429 -> "درخواست‌ها زیاد است؛ چند لحظه صبر کن و دوباره بپرس."
        in 500..599 -> "سرویس هوش مصنوعی موقتاً در دسترس نیست. کمی بعد تلاش کن."
        else -> "مشکلی پیش آمد (کد $code). دوباره تلاش کن."
    }
}

/**
 * چت هوش مصنوعی پردآوا — همان چت‌بات صفحهٔ /ai-chat سایت؛ بدون نیاز به ورود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(app: PardavaApp, onBack: () -> Unit) {
    val vm: AiChatViewModel = viewModel(factory = SimpleVmFactory(app.api) { AiChatViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val conversation = state as? AiChatUiState.Conversation ?: AiChatUiState.Conversation()
    var input by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()
    LaunchedEffect(conversation.messages.size, conversation.sending) {
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding(),
        ) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (conversation.messages.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.ai_chat_welcome),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                itemsIndexed(conversation.messages) { _, msg ->
                    ChatBubble(msg = msg)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ai_chat_hint)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(),
                    enabled = !conversation.sending,
                )
                IconButton(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                    enabled = !conversation.sending && input.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = if (conversation.sending || input.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: AiMsg) {
    val isUser = msg.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    ),
                )
                .background(
                    if (isUser) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            val content = when {
                msg.isPending -> "…"
                else -> msg.content
            }
            val color = when {
                isUser -> MaterialTheme.colorScheme.onPrimary
                msg.error != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
