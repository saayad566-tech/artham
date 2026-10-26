package `in`.artham.fundamentals.ui

import android.app.Application
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.artham.fundamentals.artham
import `in`.artham.fundamentals.data.ModelPackRecord
import `in`.artham.fundamentals.llm.EngineKind
import `in`.artham.fundamentals.llm.EngineStatus
import `in`.artham.fundamentals.llm.ModelPacks
import `in`.artham.fundamentals.ui.components.LoadingRow
import `in`.artham.fundamentals.ui.components.MessageBanner
import `in`.artham.fundamentals.ui.components.Pill
import `in`.artham.fundamentals.ui.components.SectionCard
import `in`.artham.fundamentals.ui.components.Tone
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ModelsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.artham()

    var engine by mutableStateOf(c.settings.engine)
    var serverUrl by mutableStateOf(c.settings.serverUrl)
    var serverModel by mutableStateOf(c.settings.serverModel)
    var packs by mutableStateOf<List<ModelPackRecord>>(emptyList())
    var status by mutableStateOf<EngineStatus?>(null)
    var busy by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var noticeTone by mutableStateOf(Tone.Positive)

    fun refresh() {
        viewModelScope.launch {
            packs = c.modelPackDao.all()
            status = runCatching { c.llm.status() }.getOrNull()
        }
    }

    fun selectEngine(kind: EngineKind) {
        engine = kind
        c.settings.engine = kind
        refresh()
    }

    fun saveServer() {
        c.settings.serverUrl = serverUrl
        c.settings.serverModel = serverModel
        notice = "Server settings saved."
        noticeTone = Tone.Positive
        test()
    }

    fun test() {
        viewModelScope.launch {
            busy = true
            status = runCatching { c.llm.status(engine) }.getOrNull()
            busy = false
        }
    }

    fun importModel(uri: Uri, name: String) {
        viewModelScope.launch {
            busy = true
            notice = null
            ModelPacks.importFrom(getApplication(), uri, name)
                .onSuccess { imported ->
                    c.modelPackDao.insert(
                        ModelPackRecord(
                            name = imported.name,
                            engine = EngineKind.MEDIAPIPE.name,
                            path = imported.path,
                            sizeBytes = imported.sizeBytes,
                            sha256 = imported.sha256
                        )
                    )
                    c.settings.engine = EngineKind.MEDIAPIPE
                    engine = EngineKind.MEDIAPIPE
                    notice = "Imported ${imported.name} (${imported.sizeBytes / (1024 * 1024)} MB). Loading it now."
                    noticeTone = Tone.Positive
                    c.llm.engine(EngineKind.MEDIAPIPE).close()
                    status = runCatching { c.llm.status(EngineKind.MEDIAPIPE) }.getOrNull()
                }
                .onFailure {
                    notice = "Import failed: ${it.message}"
                    noticeTone = Tone.Negative
                }
            busy = false
            refresh()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            c.modelPackDao.delete(id)
            c.llm.engine(EngineKind.MEDIAPIPE).close()
            notice = "Model record removed. The file stays in app storage until you clear app data."
            noticeTone = Tone.Warning
            refresh()
        }
    }
}

@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val vm: ModelsViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refresh() }
    var serverUrl by mutableStateOf(vm.serverUrl)
    var serverModel by mutableStateOf(vm.serverModel)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val app = vm.getApplication<Application>()
            vm.importModel(uri, app.displayName(uri))
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("Local models", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "No API keys, no per-token billing, no data leaving the phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "Engine") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vm.engine == EngineKind.MEDIAPIPE,
                        onClick = { vm.selectEngine(EngineKind.MEDIAPIPE) },
                        label = { Text("On-device", style = MaterialTheme.typography.labelMedium) }
                    )
                    FilterChip(
                        selected = vm.engine == EngineKind.LOCAL_SERVER,
                        onClick = { vm.selectEngine(EngineKind.LOCAL_SERVER) },
                        label = { Text("Local server", style = MaterialTheme.typography.labelMedium) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                val status = vm.status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ready = status is EngineStatus.Ready
                    Pill(if (ready) "READY" else "NOT READY", if (ready) Tone.Positive else Tone.Warning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (status) {
                            is EngineStatus.Ready -> status.detail
                            is EngineStatus.NotReady -> "${status.reason} ${status.hint}"
                            null -> "Status unknown"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.test() }, enabled = !vm.busy) { Text("Test connection") }
            }
        }

        if (vm.notice != null) {
            item { MessageBanner(vm.notice!!, vm.noticeTone) { vm.notice = null } }
        }
        if (vm.busy) item { LoadingRow("Loading the model") }

        if (vm.engine == EngineKind.MEDIAPIPE) {
            item {
                SectionCard(
                    title = "On-device model",
                    subtitle = "Import a .task or .litertlm file you have already downloaded"
                ) {
                    Text(
                        "Artham does not download model weights itself. Obtain a compatible bundle from the official source, then import it here. The file is copied into app-private storage and checksummed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !vm.busy) {
                        Text("Import model file")
                    }
                    if (vm.packs.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        val format = SimpleDateFormat("dd MMM yyyy", Locale.US)
                        vm.packs.forEach { pack ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(pack.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        Spacer(Modifier.width(6.dp))
                                        if (pack.isActive) Pill("ACTIVE", Tone.Positive)
                                    }
                                    Text(
                                        "${pack.sizeBytes / (1024 * 1024)} MB  •  ${format.format(Date(pack.importedAt))}  •  sha256 ${pack.sha256.take(12)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = { vm.delete(pack.id) }) {
                                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Which model should I use?") {
                    ModelPacks.catalog.filter { it.engine == EngineKind.MEDIAPIPE }.forEach { pack ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pack.reference))
                                    runCatching { context.startActivity(intent) }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(pack.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${pack.format}  •  ${pack.approxSize}  •  ${pack.licence}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(pack.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                pack.reference,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        } else {
            item {
                SectionCard(title = "Local server", subtitle = "llama.cpp server, Ollama, LM Studio or any OpenAI-compatible endpoint") {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("http://127.0.0.1:8080") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverModel,
                        onValueChange = { serverModel = it },
                        label = { Text("Model name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("gemma3:1b") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        vm.serverUrl = serverUrl
                        vm.serverModel = serverModel
                        vm.saveServer()
                    }) { Text("Save and test") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Run the server on this phone (llama.cpp built for Android, or Termux) or on any machine on your own network. Artham only ever talks to the address you enter here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "How answers are produced") {
                Text(
                    "The app computes every ratio locally, then places the numbers in the prompt as text. The model is asked to reason only over those figures, so it does not need market data and cannot fetch anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
