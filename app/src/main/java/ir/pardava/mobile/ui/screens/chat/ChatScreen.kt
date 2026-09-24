package ir.pardava.mobile.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.pardava.mobile.PardavaApp
import ir.pardava.mobile.R
import ir.pardava.mobile.core.Fmt
import ir.pardava.mobile.data.dto.SendSupportIn
import ir.pardava.mobile.data.dto.SupportMessageDto
import kotlinx.coroutines.launch

/**
 * Support chat: the learner talks to the pardava.ir team. Login is required
 * (the server rejects anonymous sends with a 401 envelope). Messages poll
 * lightly while the screen is visible; replies land in the same thread.
 */
@Composable
fun ChatScreen(
    app: PardavaApp,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val signedIn by app.signedIn.collectAsStateWithLifecycle()
    val lang = app.currentLanguage()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Icon(
                Icons.Filled.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.chat_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!signedIn) {
            GuestGate(onOpenLogin)
        } else {
            ChatBody(app, lang)
        }
    }
}

@Composable
private fun GuestGate(onOpenLogin: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.chat_login_required),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(stringResource(R.string.sign_in_cta))
        }
    }
}

@Composable
private fun ChatBody(app: PardavaApp, lang: String) {
    val messages = remember { mutableStateListOf<SupportMessageDto>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // initial load + light polling while the screen is visible
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val resp = app.api.call { app.api.api.supportMessages() }
                messages.clear()
                messages.addAll(resp.messages)
                failed = false
            } catch (_: Exception) {
                failed = true
            }
            kotlinx.coroutines.delay(4_000)
        }
    }

    // stick to the newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty() && !failed) {
                    item {
                        Text(
                            stringResource(R.string.chat_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                    }
                }
                items(messages, key = { it.id ?: -(it.body.hashCode() + it.createdAt.hashCode()).toLong() }) { m ->
                    MessageBubble(m, lang)
                }
            }

            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    IconButton(
                        enabled = !sending && input.isNotBlank(),
                        onClick = {
                            val body = input.trim()
                            if (body.isEmpty() || sending) return@IconButton
                            sending = true
                            scope.launch {
                                try {
                                    app.api.call { app.api.api.sendSupportMessage(SendSupportIn(body)) }
                                    input = ""
                                    try {
                                        val resp = app.api.call { app.api.api.supportMessages() }
                                        messages.clear()
                                        messages.addAll(resp.messages)
                                        failed = false
                                    } catch (_: Exception) {
                                        failed = true
                                    }
                                } catch (_: Exception) {
                                    failed = true
                                } finally {
                                    sending = false
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send),
                            tint = if (input.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        if (failed) {
            Text(
                stringResource(R.string.chat_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageBubble(m: SupportMessageDto, lang: String) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (m.mine) 14.dp else 3.dp,
                bottomEnd = if (m.mine) 3.dp else 14.dp,
            ),
            color = if (m.mine) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                m.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        m.createdAt?.takeIf { it.length >= 16 }?.let {
            Text(
                Fmt.digits(it.substring(11, 16), lang),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp),
            )
        }
    }
}
