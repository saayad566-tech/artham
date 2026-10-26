package `in`.artham.fundamentals.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.data.DocumentRecord
import `in`.artham.fundamentals.data.Export
import `in`.artham.fundamentals.domain.DocumentIndex
import `in`.artham.fundamentals.domain.RetrievedChunk
import `in`.artham.fundamentals.llm.EngineStatus
import `in`.artham.fundamentals.llm.PromptBuilder
import `in`.artham.fundamentals.ui.components.EmptyState
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()

    var documents by mutableStateOf<List<DocumentRecord>>(emptyList())
    var question by mutableStateOf("")
    var answer by mutableStateOf<String?>(null)
    var passages by mutableStateOf<List<RetrievedChunk>>(emptyList())
    var working by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var modelReady by mutableStateOf(false)

    fun load() {
        viewModelScope.launch {
            documents = c.documentDao.all()
            modelReady = runCatching { c.llm.status() }.getOrNull() is EngineStatus.Ready
        }
    }

    fun importText(uri: Uri, name: String) {
        viewModelScope.launch {
            working = true
            notice = null
            try {
                val context = getApplication<Application>()
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("The file could not be opened.")
                if (text.isBlank()) throw IllegalStateException("The file is empty.")
                val chunks = DocumentIndex.chunk(text)
                if (chunks.isEmpty()) throw IllegalStateException("No readable text was found.")
                c.documentDao.insert(name, null, chunks)
                notice = "Indexed \"$name\": ${chunks.size} passages from ${text.length} characters."
                load()
            } catch (e: Exception) {
                notice = "Import failed: ${e.message}"
            } finally {
                working = false
            }
        }
    }

    fun ask() {
        if (question.isBlank() || working) return
        viewModelScope.launch {
            working = true
            answer = null
            val chunks = c.documentDao.chunks().map { it to "" }
            val titles = c.documentDao.all().associate { it.id to it.title }
            val retrieved = DocumentIndex.search(chunks, titles, question, topK = 6)
            passages = retrieved
            if (retrieved.isEmpty()) {
                notice = "No passage in your documents mentions those terms. Try different words."
                working = false
                return@launch
            }
            if (!modelReady) {
                notice = "Passages found, but no local model is configured, so only the excerpts are shown."
                working = false
                return@launch
            }
            answer = try {
                c.llm.generate(
                    PromptBuilder.DOCUMENT_SYSTEM,
                    PromptBuilder.documentPrompt(question, retrieved, null)
                ).toList().joinToString("")
            } catch (e: Exception) {
                notice = "The model failed: ${e.message}"
                null
            }
            working = false
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            c.documentDao.delete(id)
            load()
        }
    }
}

private val sampleQuestions = listOf(
    "What does management say about margins?",
    "How much did capex increase?",
    "What are the stated risks?",
    "Which segments grew fastest?"
)

@Composable
fun DocumentsScreen() {
    val vm: DocumentsViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val app = vm.getApplication<Application>()
            vm.importText(uri, app.displayName(uri))
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Documents", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Annual report text, concall transcripts, your own notes. Indexed and searched on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vm.notice != null) {
            item { MessageBanner(vm.notice!!, Tone.Warning) { vm.notice = null } }
        }

        item {
            SectionCard(
                title = "Library",
                subtitle = "Plain text or markdown (.txt, .md, .csv). Nothing is uploaded."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("text/*", "*/*")) }, enabled = !vm.working) {
                        Text("Add document")
                    }
                    if (vm.documents.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                Export.share(
                                    context, "artham-documents", "text/plain",
                                    vm.documents.joinToString("\n") { "${it.title} — ${it.chunkCount} passages, ${it.charCount} chars" }
                                )
                            }
                        ) { Text("Export list") }
                    }
                }
                if (vm.working) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Working", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (vm.documents.isEmpty()) {
                    EmptyState(
                        "No documents yet",
                        "Paste the text of an annual report into a .txt file and add it here. Scanned PDFs need to be converted to text first, because this app has no OCR."
                    )
                } else {
                    val format = SimpleDateFormat("dd MMM yyyy", Locale.US)
                    vm.documents.forEach { doc ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(doc.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text(
                                    "${doc.chunkCount} passages  •  ${doc.charCount} chars  •  ${format.format(Date(doc.addedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.delete(doc.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Ask your documents", subtitle = "Lexical retrieval (BM25), then the local model reads the passages") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (vm.modelReady) "MODEL READY" else "NO MODEL", if (vm.modelReady) Tone.Positive else Tone.Warning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.modelReady) "Answers are generated on the device."
                        else "Without a model you still get the matching passages.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.question,
                    onValueChange = { vm.question = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.ask() }, enabled = !vm.working && vm.question.isNotBlank()) {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search and answer")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sampleQuestions.take(2).forEach { sample ->
                        AssistChip(
                            onClick = { vm.question = sample; vm.ask() },
                            label = { Text(sample, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        if (vm.answer != null) {
            item {
                SectionCard(title = "Answer") {
                    Text(vm.answer!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Generated from the passages below. Check the citation before relying on it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (vm.passages.isNotEmpty()) {
            item {
                SectionCard(title = "Passages used", subtitle = "Ranked by BM25 score") {
                    vm.passages.forEachIndexed { index, passage ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "[${index + 1}] ${passage.docTitle}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "score ${String.format("%.2f", passage.score)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                passage.body.take(600) + if (passage.body.length > 600) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Retrieval is lexical, not semantic: it matches words, not meaning. A question phrased in completely different vocabulary from the document may miss. Vector search would need a second model file, which this version deliberately avoids.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
