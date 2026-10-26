package `in`.artham.fundamentals.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.data.ChatMessage
import `in`.artham.fundamentals.data.Company
import `in`.artham.fundamentals.data.CompanyRecord
import `in`.artham.fundamentals.data.ScreenResult
import `in`.artham.fundamentals.domain.ProsConsEngine
import `in`.artham.fundamentals.llm.EngineStatus
import `in`.artham.fundamentals.llm.PromptBuilder
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

class AiViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()

    var companies by mutableStateOf<List<Company>>(emptyList())
    var selected by mutableStateOf<Company?>(null)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var draft by mutableStateOf("")
    var streaming by mutableStateOf(false)
    var partial by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var statusText by mutableStateOf("Checking the local model")
    var ready by mutableStateOf(false)
    var lastScreen by mutableStateOf<ScreenResult?>(null)

    private var sessionKey: String = "general"

    fun init() {
        viewModelScope.launch {
            companies = c.companyDao.all()
            val preselected = AiSelection.companyId?.let { id -> companies.firstOrNull { it.id == id } }
            selected = preselected ?: selected ?: companies.firstOrNull()
            sessionKey = selected?.let { "company-${it.id}" } ?: "general"
            messages = c.aiDao.messages(sessionKey)
            refreshStatus()
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            when (val s = runCatching { c.llm.status() }.getOrNull()) {
                is EngineStatus.Ready -> { ready = true; statusText = s.detail }
                is EngineStatus.NotReady -> { ready = false; statusText = "${s.reason} ${s.hint}" }
                null -> { ready = false; statusText = "The local model status could not be read." }
            }
        }
    }

    fun select(company: Company) {
        selected = company
        sessionKey = "company-${company.id}"
        viewModelScope.launch { messages = c.aiDao.messages(sessionKey) }
    }

    fun attachScreen(result: ScreenResult?) {
        lastScreen = result
    }

    fun ask(question: String) {
        if (question.isBlank() || streaming) return
        error = null
        viewModelScope.launch {
            streaming = true
            partial = ""
            c.aiDao.append(sessionKey, "user", question, c.settings.engine.name)
            messages = c.aiDao.messages(sessionKey)
            val record: CompanyRecord? = selected?.let { c.companyDao.record(it) }
            val prompt = if (record != null) {
                PromptBuilder.companyPrompt(record, ProsConsEngine.analyse(`in`.artham.fundamentals.domain.Financials(record)), question)
            } else if (lastScreen != null) {
                PromptBuilder.screenPrompt(lastScreen!!, question)
            } else {
                question
            }
            val builder = StringBuilder()
            try {
                c.llm.generate(PromptBuilder.SYSTEM, prompt)
                    .onCompletion { streaming = false }
                    .collect { piece ->
                        builder.append(piece)
                        partial = builder.toString()
                    }
                if (builder.isNotBlank()) {
                    c.aiDao.append(sessionKey, "assistant", builder.toString(), c.settings.engine.name)
                } else {
                    error = "The local model returned nothing. Check it in the Models tab."
                }
            } catch (e: Exception) {
                error = e.message ?: "The local model failed to answer."
            } finally {
                streaming = false
                partial = ""
                messages = c.aiDao.messages(sessionKey)
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            c.aiDao.clearSession(sessionKey)
            messages = emptyList()
        }
    }
}

private val suggestions = listOf(
    "Is the profit turning into cash?",
    "Explain the working capital trend",
    "What is the biggest risk in these numbers?",
    "Compare margins with three years ago",
    "Why is the debt to equity where it is?"
)

@Composable
fun AiScreen(onOpenCompany: (Long) -> Unit) {
    val vm: AiViewModel = viewModel()
    LaunchedEffect(Unit) { vm.init() }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Analyst", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Answers are generated by a model running on this device, grounded on the numbers stored here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (vm.ready) "MODEL READY" else "MODEL NOT READY", if (vm.ready) Tone.Positive else Tone.Warning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        vm.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
            }
        }

        item {
            SectionCard(title = "Company in context") {
                if (vm.companies.isEmpty()) {
                    EmptyState("No companies yet", "Import a dataset from the Data tab first.")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(vm.companies.take(40)) { company ->
                            AssistChip(
                                onClick = { vm.select(company) },
                                label = { Text(company.code, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        vm.selected?.name ?: "No company selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (vm.error != null) {
            item { MessageBanner(vm.error!!, Tone.Negative) { vm.error = null } }
        }

        items(vm.messages) { message ->
            val isUser = message.role == "user"
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (isUser) "You" else "Artham",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(message.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (vm.streaming) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Generating on device", style = MaterialTheme.typography.labelSmall)
                        }
                        if (vm.partial.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(vm.partial, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        if (vm.messages.isEmpty() && !vm.streaming) {
            item {
                SectionCard(title = "Try asking") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { suggestion ->
                            AssistChip(onClick = { vm.ask(suggestion) }, label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = vm.draft,
                onValueChange = { vm.draft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Your question") },
                placeholder = { Text("What does the cash flow say about earnings quality?") }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val q = vm.draft.trim()
                        vm.draft = ""
                        vm.ask(q)
                    },
                    enabled = !vm.streaming && vm.draft.isNotBlank()
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ask")
                }
                if (vm.messages.isNotEmpty()) {
                    Button(onClick = { vm.clear() }, enabled = !vm.streaming) { Text("Clear") }
                }
            }
        }

        item {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(12.dp)) {
                Text(
                    "The model only sees the numbers in the prompt, which come from this device. It cannot browse and it has no market data. Always check the original filing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
