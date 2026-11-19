package com.harxhxt.ovsstudio

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

private val Background = Color(0xFF101318)
private val Panel = Color(0xFF1B2028)
private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFF929BA8)

private const val PREFS_NAME = "ovs_workspace"
private const val SCENE_PREFS = "ovs_scene_data"
private const val LAYOUT_VERSION = 1

// ---------------- DATA MODELS ----------------

enum class SourceType {
    DISPLAY_CAPTURE,
    IMAGE,
    TEXT,
    BROWSER,
    AUDIO_INPUT
}

data class OvsSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val value: String = "",
    val enabled: Boolean = true
)

data class OvsScene(
    val id: String,
    val name: String,
    val sources: List<OvsSource> = emptyList()
)

// ---------------- SCENE STORAGE ----------------

private fun defaultScenes(): List<OvsScene> {
    return listOf(
        OvsScene(
            id = UUID.randomUUID().toString(),
            name = "Scene 1"
        )
    )
}

private fun loadScenes(context: Context): List<OvsScene> {
    return try {
        val prefs = context.getSharedPreferences(SCENE_PREFS, 0)
        val raw = prefs.getString("scenes", null)
            ?: return defaultScenes()

        val array = JSONArray(raw)
        val result = mutableListOf<OvsScene>()

        for (i in 0 until array.length()) {
            val sceneObject = array.getJSONObject(i)
            val sourceArray = sceneObject.optJSONArray("sources")
                ?: JSONArray()

            val sources = mutableListOf<OvsSource>()

            for (j in 0 until sourceArray.length()) {
                val source = sourceArray.getJSONObject(j)

                val type = runCatching {
                    SourceType.valueOf(source.getString("type"))
                }.getOrDefault(SourceType.TEXT)

                sources.add(
                    OvsSource(
                        id = source.optString("id"),
                        name = source.optString("name"),
                        type = type,
                        value = source.optString("value"),
                        enabled = source.optBoolean("enabled", true)
                    )
                )
            }

            result.add(
                OvsScene(
                    id = sceneObject.optString("id"),
                    name = sceneObject.optString("name"),
                    sources = sources
                )
            )
        }

        result.ifEmpty { defaultScenes() }
    } catch (_: Exception) {
        defaultScenes()
    }
}

private fun saveScenes(
    context: Context,
    scenes: List<OvsScene>
) {
    val array = JSONArray()

    scenes.forEach { scene ->
        val sceneObject = JSONObject()
        sceneObject.put("id", scene.id)
        sceneObject.put("name", scene.name)

        val sourceArray = JSONArray()

        scene.sources.forEach { source ->
            val sourceObject = JSONObject()
            sourceObject.put("id", source.id)
            sourceObject.put("name", source.name)
            sourceObject.put("type", source.type.name)
            sourceObject.put("value", source.value)
            sourceObject.put("enabled", source.enabled)
            sourceArray.put(sourceObject)
        }

        sceneObject.put("sources", sourceArray)
        array.put(sceneObject)
    }

    context.getSharedPreferences(SCENE_PREFS, 0)
        .edit()
        .putString("scenes", array.toString())
        .apply()
}

// ---------------- ACTIVITY ----------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Background,
                    surface = Panel,
                    primary = Accent
                )
            ) {
                OvsWorkspace()
            }
        }
    }
}

// ---------------- DOCK MODEL ----------------

class Dock(
    val title: String,
    val type: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    visible: Boolean = true
) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var width by mutableFloatStateOf(width)
    var height by mutableFloatStateOf(height)
    var visible by mutableStateOf(visible)
}

private fun defaultDocks(): MutableList<Dock> {
    return mutableListOf(
        Dock("SCENES", "scenes", 0f, 0f, 0.18f, 1f),
        Dock("PREVIEW", "preview", 0.19f, 0f, 0.55f, 0.68f),
        Dock("SOURCES", "sources", 0.19f, 0.70f, 0.55f, 0.28f),
        Dock("AUDIO MIXER", "audio", 0.75f, 0f, 0.24f, 0.68f),
        Dock("SCENE TRANSITIONS", "transitions", 0.75f, 0.70f, 0.24f, 0.28f)
    )
}

private fun loadDocks(
    preferences: android.content.SharedPreferences
): MutableList<Dock> {
    return defaultDocks().map { dock ->
        Dock(
            dock.title,
            dock.type,
            preferences.getFloat("${dock.type}_x", dock.x),
            preferences.getFloat("${dock.type}_y", dock.y),
            preferences.getFloat("${dock.type}_width", dock.width),
            preferences.getFloat("${dock.type}_height", dock.height),
            preferences.getBoolean("${dock.type}_visible", true)
        )
    }.toMutableList()
}

// ---------------- MAIN WORKSPACE ----------------

@Composable
fun OvsWorkspace() {

    val context = LocalContext.current

    val preferences = remember(context) {
        context.getSharedPreferences(PREFS_NAME, 0)
    }

    val docks = remember(preferences) {
        mutableStateListOf<Dock>().apply {
            addAll(loadDocks(preferences))
        }
    }

    val scenes = remember(context) {
        mutableStateListOf<OvsScene>().apply {
            addAll(loadScenes(context))
        }
    }

    var selectedSceneId by remember {
        mutableStateOf(scenes.first().id)
    }

    var selectedSourceId by remember {
        mutableStateOf("")
    }

    val selectedScene = scenes.firstOrNull {
        it.id == selectedSceneId
    } ?: scenes.first()

    var docksMenuExpanded by remember {
        mutableStateOf(false)
    }

    var sceneDialog by remember {
        mutableStateOf(false)
    }

    var renameDialog by remember {
        mutableStateOf(false)
    }

    var sourceDialog by remember {
        mutableStateOf(false)
    }

    var sceneName by remember {
        mutableStateOf("")
    }

    var sourceName by remember {
        mutableStateOf("")
    }

    var sourceValue by remember {
        mutableStateOf("")
    }

    var sourceType by remember {
        mutableStateOf(SourceType.TEXT)
    }

    var sourceTypeMenu by remember {
        mutableStateOf(false)
    }

    var pendingImageName by remember {
        mutableStateOf("Image")
    }

    // IMAGE PICKER

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            val index = scenes.indexOfFirst {
                it.id == selectedSceneId
            }

            if (index >= 0) {
                val source = OvsSource(
                    id = UUID.randomUUID().toString(),
                    name = pendingImageName,
                    type = SourceType.IMAGE,
                    value = uri.toString()
                )

                scenes[index] = scenes[index].copy(
                    sources = scenes[index].sources + source
                )

                selectedSourceId = source.id
            }
        }
    }

    // SCENE FUNCTIONS

    fun addScene() {
        val name = sceneName.trim()
        if (name.isEmpty()) return

        val scene = OvsScene(
            id = UUID.randomUUID().toString(),
            name = name
        )

        scenes.add(scene)
        selectedSceneId = scene.id
        selectedSourceId = ""
        sceneName = ""
        sceneDialog = false
    }

    fun renameSelectedScene() {
        val name = sceneName.trim()
        if (name.isEmpty()) return

        val index = scenes.indexOfFirst {
            it.id == selectedSceneId
        }

        if (index >= 0) {
            scenes[index] = scenes[index].copy(name = name)
        }

        renameDialog = false
        sceneName = ""
    }

    fun deleteSelectedScene() {
        if (scenes.size <= 1) return

        val index = scenes.indexOfFirst {
            it.id == selectedSceneId
        }

        if (index >= 0) {
            scenes.removeAt(index)
            selectedSceneId = scenes.first().id
            selectedSourceId = ""
        }
    }

    // SOURCE FUNCTIONS

    fun addSource(
        name: String,
        type: SourceType,
        value: String
    ) {
        val index = scenes.indexOfFirst {
            it.id == selectedSceneId
        }

        if (index < 0) return

        val source = OvsSource(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
            value = value
        )

        scenes[index] = scenes[index].copy(
            sources = scenes[index].sources + source
        )

        selectedSourceId = source.id
    }

    fun toggleSource(sourceId: String) {
        val index = scenes.indexOfFirst {
            it.id == selectedSceneId
        }

        if (index < 0) return

        val updated = scenes[index].sources.map { source ->
            if (source.id == sourceId) {
                source.copy(enabled = !source.enabled)
            } else {
                source
            }
        }

        scenes[index] = scenes[index].copy(sources = updated)
    }

    fun removeSource(sourceId: String) {
        val index = scenes.indexOfFirst {
            it.id == selectedSceneId
        }

        if (index < 0) return

        scenes[index] = scenes[index].copy(
            sources = scenes[index].sources.filterNot {
                it.id == sourceId
            }
        )

        if (selectedSourceId == sourceId) {
            selectedSourceId = ""
        }
    }

    LaunchedEffect(scenes.toList()) {
        saveScenes(context, scenes.toList())
    }

    val layoutSignature = docks.joinToString("|") {
        "${it.type}:${it.x}:${it.y}:${it.width}:${it.height}:${it.visible}"
    }

    LaunchedEffect(layoutSignature) {
        delay(300)

        preferences.edit().apply {
            putInt("layout_version", LAYOUT_VERSION)

            docks.forEach { dock ->
                putFloat("${dock.type}_x", dock.x)
                putFloat("${dock.type}_y", dock.y)
                putFloat("${dock.type}_width", dock.width)
                putFloat("${dock.type}_height", dock.height)
                putBoolean("${dock.type}_visible", dock.visible)
            }

            apply()
        }
    }

    fun resetWorkspace() {
        val defaults = defaultDocks()

        docks.zip(defaults).forEach { (current, default) ->
            current.x = default.x
            current.y = default.y
            current.width = default.width
            current.height = default.height
            current.visible = true
        }
    }

    // RECORDING STATE

    var recordingStatus by remember {
        mutableStateOf("IDLE")
    }

    var recordingSeconds by remember {
        mutableLongStateOf(0L)
    }

    var recordingMessage by remember {
        mutableStateOf("Ready to record")
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        if (
            result.resultCode == Activity.RESULT_OK &&
            result.data != null
        ) {
            RecordingManager.startRecording(
                context,
                result.resultCode,
                result.data!!
            )
        } else {
            recordingMessage = "Screen capture permission denied"
        }
    }

    fun requestCapture() {
        captureLauncher.launch(
            RecordingManager.createScreenCaptureIntent(context)
        )
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestCapture()
        } else {
            recordingMessage = "Microphone permission required"
        }
    }

    fun startRecording() {
        if (
            context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            requestCapture()
        } else {
            microphoneLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (intent?.action == RecordingManager.ACTION_STATUS) {
                    recordingStatus = intent.getStringExtra(
                        RecordingManager.EXTRA_STATUS
                    ) ?: "IDLE"

                    recordingSeconds = intent.getLongExtra(
                        RecordingManager.EXTRA_SECONDS,
                        0L
                    )

                    recordingMessage = intent.getStringExtra(
                        RecordingManager.EXTRA_MESSAGE
                    ) ?: ""
                }
            }
        }

        val filter = IntentFilter(RecordingManager.ACTION_STATUS)

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    val timerText = String.format(
        "%02d:%02d:%02d",
        recordingSeconds / 3600,
        (recordingSeconds % 3600) / 60,
        recordingSeconds % 60
    )

    // ---------------- UI ----------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(6.dp)
    ) {

        // TOP BAR

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                "OVS STUDIO",
                color = Accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.width(8.dp))

            Box {
                TextButton(
                    onClick = {
                        docksMenuExpanded = true
                    }
                ) {
                    Text(
                        "DOCKS ▾",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                DropdownMenu(
                    expanded = docksMenuExpanded,
                    onDismissRequest = {
                        docksMenuExpanded = false
                    }
                ) {
                    docks.forEach { dock ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = dock.visible,
                                        onCheckedChange = null
                                    )
                                    Text(dock.title, fontSize = 12.sp)
                                }
                            },
                            onClick = {
                                dock.visible = !dock.visible
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = { resetWorkspace() }) {
                Text(
                    "RESET LAYOUT",
                    color = Accent,
                    fontSize = 9.sp
                )
            }
        }

        HorizontalDivider(color = Color(0xFF303640))

        // WORKSPACE DOCKS

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {

            val workspaceWidth = maxWidth
            val workspaceHeight = maxHeight

            docks.forEachIndexed { index, dock ->

                if (dock.visible) {

                    val panelWidth =
                        (workspaceWidth * dock.width).coerceAtLeast(70.dp)

                    val panelHeight =
                        (workspaceHeight * dock.height).coerceAtLeast(70.dp)

                    Column(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (workspaceWidth.toPx() * dock.x).roundToInt(),
                                    (workspaceHeight.toPx() * dock.y).roundToInt()
                                )
                            }
                            .width(panelWidth)
                            .height(panelHeight)
                            .zIndex(index.toFloat())
                            .clip(RoundedCornerShape(8.dp))
                            .background(Panel)
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .background(Color(0xFF252C36))
                                .pointerInput(
                                    dock,
                                    workspaceWidth,
                                    workspaceHeight
                                ) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()

                                        val dx =
                                            dragAmount.x / workspaceWidth.toPx()
                                        val dy =
                                            dragAmount.y / workspaceHeight.toPx()

                                        dock.x = (dock.x + dx).coerceIn(
                                            0f,
                                            1f - dock.width
                                        )

                                        dock.y = (dock.y + dy).coerceIn(
                                            0f,
                                            1f - dock.height
                                        )
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                "☰",
                                color = Accent,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Text(
                                dock.title,
                                color = Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.weight(1f))

                            Text(
                                "DRAG",
                                color = Muted,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    if (dock.type == "preview") {
                                        Color(0xFF080A0D)
                                    } else {
                                        Panel
                                    }
                                )
                        ) {

                            when (dock.type) {

                                "scenes" -> {
                                    ScenePanel(
                                        scenes = scenes,
                                        selectedId = selectedSceneId,
                                        onSelect = {
                                            selectedSceneId = it
                                            selectedSourceId = ""
                                        },
                                        onAdd = {
                                            sceneName = ""
                                            sceneDialog = true
                                        },
                                        onRename = {
                                            sceneName = selectedScene.name
                                            renameDialog = true
                                        },
                                        onDelete = {
                                            deleteSelectedScene()
                                        }
                                    )
                                }

                                "sources" -> {
                                    SourcePanel(
                                        sources = selectedScene.sources,
                                        selectedId = selectedSourceId,
                                        onSelect = {
                                            selectedSourceId = it
                                        },
                                        onAdd = {
                                            sourceName = ""
                                            sourceValue = ""
                                            sourceType = SourceType.TEXT
                                            sourceDialog = true
                                        },
                                        onToggle = {
                                            toggleSource(it)
                                        },
                                        onRemove = {
                                            removeSource(it)
                                        }
                                    )
                                }

                                "preview" -> {
                                    PreviewPanel(
                                        source = selectedScene.sources.firstOrNull {
                                            it.id == selectedSourceId &&
                                                it.enabled
                                        } ?: selectedScene.sources.firstOrNull {
                                            it.enabled
                                        }
                                    )
                                }

                                "audio" -> {
                                    AudioPanel(
                                        sources = selectedScene.sources
                                    )
                                }

                                "transitions" -> {
                                    Column(
                                        Modifier.padding(8.dp)
                                    ) {
                                        Text(
                                            "FADE",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "Duration: 300 ms",
                                            color = Muted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .align(Alignment.BottomEnd)
                                    .pointerInput(
                                        dock,
                                        workspaceWidth,
                                        workspaceHeight
                                    ) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()

                                            val dw =
                                                dragAmount.x / workspaceWidth.toPx()
                                            val dh =
                                                dragAmount.y / workspaceHeight.toPx()

                                            dock.width = (dock.width + dw).coerceIn(
                                                0.15f,
                                                1f - dock.x
                                            )

                                            dock.height = (dock.height + dh).coerceIn(
                                                0.15f,
                                                1f - dock.y
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "◢",
                                    color = Accent,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM CONTROL BAR

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            BottomButton(
                label = "STREAM",
                color = Panel,
                onClick = {
                    recordingMessage =
                        "Streaming integration is not enabled yet"
                }
            )

            BottomButton(
                label = when (recordingStatus) {
                    "RECORDING", "PAUSED" -> "STOP"
                    else -> "RECORD"
                },
                color = if (
                    recordingStatus == "RECORDING" ||
                    recordingStatus == "PAUSED"
                ) Color(0xFFB3261E) else Panel,
                onClick = {
                    if (
                        recordingStatus == "RECORDING" ||
                        recordingStatus == "PAUSED"
                    ) {
                        RecordingManager.stop(context)
                    } else {
                        startRecording()
                    }
                }
            )

            if (recordingStatus == "RECORDING") {
                BottomButton(
                    label = "PAUSE",
                    color = Panel,
                    onClick = {
                        RecordingManager.pause(context)
                    }
                )
            }

            if (recordingStatus == "PAUSED") {
                BottomButton(
                    label = "RESUME",
                    color = Panel,
                    onClick = {
                        RecordingManager.resume(context)
                    }
                )
            }

            BottomButton(
                label = "STUDIO",
                color = Panel,
                onClick = {
                    recordingMessage = "Studio mode"
                }
            )

            BottomButton(
                label = "SETTINGS",
                color = Panel,
                onClick = {
                    recordingMessage =
                        "Settings panel will be added in its phase"
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "● $recordingStatus",
                color = if (recordingStatus == "RECORDING") {
                    Color.Red
                } else Accent,
                fontSize = 10.sp
            )

            Spacer(Modifier.width(12.dp))

            Text(
                timerText,
                color = Color.White,
                fontSize = 11.sp
            )

            Spacer(Modifier.width(12.dp))

            Text(
                recordingMessage,
                color = Muted,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }

    // ADD SCENE DIALOG

    if (sceneDialog) {
        AlertDialog(
            onDismissRequest = {
                sceneDialog = false
            },
            title = {
                Text("Create Scene")
            },
            text = {
                OutlinedTextField(
                    value = sceneName,
                    onValueChange = {
                        sceneName = it
                    },
                    label = {
                        Text("Scene name")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    addScene()
                }) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    sceneDialog = false
                }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // RENAME SCENE DIALOG

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = {
                renameDialog = false
            },
            title = {
                Text("Rename Scene")
            },
            text = {
                OutlinedTextField(
                    value = sceneName,
                    onValueChange = {
                        sceneName = it
                    },
                    label = {
                        Text("New scene name")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameSelectedScene()
                }) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    renameDialog = false
                }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // ADD SOURCE DIALOG

    if (sourceDialog) {
        AlertDialog(
            onDismissRequest = {
                sourceDialog = false
            },
            title = {
                Text("Add Source")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = {
                            sourceName = it
                        },
                        label = {
                            Text("Source name")
                        },
                        singleLine = true
                    )

                    Box {
                        OutlinedButton(
                            onClick = {
                                sourceTypeMenu = true
                            }
                        ) {
                            Text(sourceType.name.replace("_", " "))
                        }

                        DropdownMenu(
                            expanded = sourceTypeMenu,
                            onDismissRequest = {
                                sourceTypeMenu = false
                            }
                        ) {
                            SourceType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(type.name.replace("_", " "))
                                    },
                                    onClick = {
                                        sourceType = type
                                        sourceTypeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    if (
                        sourceType == SourceType.TEXT ||
                        sourceType == SourceType.BROWSER
                    ) {
                        OutlinedTextField(
                            value = sourceValue,
                            onValueChange = {
                                sourceValue = it
                            },
                            label = {
                                Text(
                                    if (sourceType == SourceType.TEXT) {
                                        "Text to display"
                                    } else {
                                        "Website URL"
                                    }
                                )
                            }
                        )
                    }

                    if (sourceType == SourceType.DISPLAY_CAPTURE) {
                        Text(
                            "Display Capture will use Android screen-capture permission when recording starts.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    if (sourceType == SourceType.AUDIO_INPUT) {
                        Text(
                            "Microphone audio is captured by the recording service.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    if (sourceType == SourceType.IMAGE) {
                        Text(
                            "You will select an image after pressing ADD.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = sourceName.trim()

                        if (name.isNotEmpty()) {
                            if (sourceType == SourceType.IMAGE) {
                                pendingImageName = name
                                sourceDialog = false
                                imagePicker.launch(arrayOf("image/*"))
                            } else {
                                addSource(
                                    name,
                                    sourceType,
                                    sourceValue.trim()
                                )
                                sourceDialog = false
                            }
                        }
                    }
                ) {
                    Text("ADD")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    sourceDialog = false
                }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// ---------------- SCENE PANEL ----------------

@Composable
private fun ScenePanel(
    scenes: List<OvsScene>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
    ) {

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TextButton(onClick = onAdd) {
                Text("+ ADD", fontSize = 9.sp)
            }

            TextButton(onClick = onRename) {
                Text("RENAME", fontSize = 9.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            scenes.forEach { scene ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    color = if (scene.id == selectedId) {
                        Color(0xFF304B4A)
                    } else Panel,
                    shape = RoundedCornerShape(5.dp),
                    onClick = {
                        onSelect(scene.id)
                    }
                ) {
                    Column(
                        Modifier.padding(8.dp)
                    ) {
                        Text(
                            scene.name,
                            color = if (scene.id == selectedId) {
                                Accent else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "${scene.sources.size} sources",
                                color = Muted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = onDelete,
            enabled = scenes.size > 1
        ) {
            Text(
                "DELETE SCENE",
                color = Color(0xFFFF7777),
                fontSize = 9.sp
            )
        }
    }
}

// ---------------- SOURCE PANEL ----------------

@Composable
private fun SourcePanel(
    sources: List<OvsSource>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SCENE SOURCES",
                color = Muted,
                fontSize = 9.sp
            )

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onAdd) {
                Text("+ ADD SOURCE", fontSize = 9.sp)
            }
        }

        if (sources.isEmpty()) {
            Text(
                "No sources in this scene.\nPress + ADD SOURCE.",
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                sources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (source.id == selectedId) {
                                    Color(0xFF304B4A)
                                } else Color(0xFF202630),
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = source.enabled,
                            onCheckedChange = {
                                onToggle(source.id)
                            }
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            TextButton(
                                onClick = {
                                    onSelect(source.id)
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    source.name,
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }

                            Text(
                                source.type.name.replace("_", " "),
                                color = Muted,
                                fontSize = 8.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                onRemove(source.id)
                            },
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            Text(
                                "✕",
                                color = Color(0xFFFF7777),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- PREVIEW PANEL ----------------

@Composable
private fun PreviewPanel(source: OvsSource?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A0D)),
        contentAlignment = Alignment.Center
    ) {
        if (source == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "OVS STUDIO",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Select a scene source to preview",
                    color = Muted,
                    fontSize = 10.sp
                )
            }
        } else if (!source.enabled) {
            Text(
                "SOURCE HIDDEN",
                color = Muted
            )
        } else {
            when (source.type) {
                SourceType.TEXT -> {
                    Text(
                        source.value.ifBlank { source.name },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                SourceType.IMAGE -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { imageView ->
                            try {
                                imageView.setImageURI(Uri.parse(source.value))
                            } catch (_: Exception) {
                                imageView.setImageDrawable(null)
                            }
                        }
                    )
                }

                SourceType.DISPLAY_CAPTURE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "DISPLAY CAPTURE",
                            color = Accent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Screen capture permission is requested when recording starts.",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                }

                SourceType.BROWSER -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "BROWSER SOURCE",
                            color = Accent
                        )
                        Text(
                            source.value,
                            color = Muted,
                            fontSize = 10.sp
                        )
                        Text(
                            "Live browser rendering is not implemented yet.",
                            color = Muted,
                            fontSize = 9.sp
                        )
                    }
                }

                SourceType.AUDIO_INPUT -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "MICROPHONE",
                            color = Accent
                        )
                        Text(
                            "Audio input source",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// ---------------- AUDIO PANEL ----------------

@Composable
private fun AudioPanel(sources: List<OvsSource>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "AUDIO MIXER",
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Microphone",
            color = Color.White,
            fontSize = 10.sp
        )

        LinearProgressIndicator(
            progress = { 0.15f },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = Accent
        )

        Spacer(Modifier.height(10.dp))

        sources.filter {
            it.type == SourceType.AUDIO_INPUT
        }.forEach { source ->
            Text(
                source.name,
                color = Color.White,
                fontSize = 10.sp
            )
        }

        Text(
            "Advanced audio mixing will be added in the audio phase.",
            color = Muted,
            fontSize = 9.sp
        )
    }
}

// ---------------- BOTTOM BUTTON ----------------

@Composable
private fun BottomButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 48.dp)
            .defaultMinSize(minWidth = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 3.dp)
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

      
