package com.harxhxt.ovsstudio

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Background = Color(0xFF101318)
private val Panel = Color(0xFF1B2028)
private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFF929BA8)

private const val PREFS_NAME = "ovs_workspace"
private const val LAYOUT_VERSION = 1

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

fun defaultDocks(): MutableList<Dock> {
    return mutableListOf(
        Dock("SCENES", "scenes", 0f, 0f, 0.18f, 1f),
        Dock("PREVIEW", "preview", 0.19f, 0f, 0.55f, 0.68f),
        Dock("SOURCES", "sources", 0.19f, 0.70f, 0.55f, 0.28f),
        Dock("AUDIO MIXER", "audio", 0.75f, 0f, 0.24f, 0.68f),
        Dock("SCENE TRANSITIONS", "transitions", 0.75f, 0.70f, 0.24f, 0.28f)
    )
}

fun loadDocks(
    preferences: android.content.SharedPreferences
): MutableList<Dock> {
    val defaults = defaultDocks()

    return defaults.map { dock ->
        val key = dock.type

        Dock(
            dock.title,
            dock.type,
            preferences.getFloat("${key}_x", dock.x),
            preferences.getFloat("${key}_y", dock.y),
            preferences.getFloat("${key}_width", dock.width),
            preferences.getFloat("${key}_height", dock.height),
            preferences.getBoolean("${key}_visible", true)
        )
    }.toMutableList()
}

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

    var docksMenuExpanded by remember {
        mutableStateOf(false)
    }

    val layoutSignature = docks.joinToString("|") {
        "${it.type}:${it.x}:${it.y}:${it.width}:${it.height}:${it.visible}"
    }

    LaunchedEffect(layoutSignature) {
        delay(300)

        preferences.edit().apply {
            putInt("layout_version", LAYOUT_VERSION)

            docks.forEach { dock ->
                val key = dock.type
                putFloat("${key}_x", dock.x)
                putFloat("${key}_y", dock.y)
                putFloat("${key}_width", dock.width)
                putFloat("${key}_height", dock.height)
                putBoolean("${key}_visible", dock.visible)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(6.dp)
    ) {
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
                    onClick = { docksMenuExpanded = true }
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
                                    if (dock.type == "preview")
                                        Color(0xFF080A0D)
                                    else Panel
                                )
                        ) {
                            DockContent(dock.type)

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

        RecordingControls()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            WorkspaceButton("STREAM")
            WorkspaceButton("RECORD")
            WorkspaceButton("STUDIO")
            WorkspaceButton("SETTINGS")
        }
    }
}

@Composable
fun DockContent(type: String) {
    when (type) {
        "preview" -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "OVS STUDIO",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Preview Area",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }

        "scenes" -> {
            Column(Modifier.padding(8.dp)) {
                Text("+ Add Scene", color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text("Scene 1", color = Accent, fontSize = 12.sp)
            }
        }

        "sources" -> {
            Column(Modifier.padding(8.dp)) {
                Text("+ Add Source", color = Color.White, fontSize = 11.sp)
            }
        }

        "audio" -> {
            Column(Modifier.padding(8.dp)) {
                Text("Desktop Audio", color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Text("Mic / Auxiliary", color = Color.White, fontSize = 11.sp)
            }
        }

        "transitions" -> {
            Column(Modifier.padding(8.dp)) {
                Text("Fade", color = Color.White, fontSize = 12.sp)
                Text("Duration: 300 ms", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun RowScope.WorkspaceButton(label: String) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        color = Panel,
        shape = RoundedCornerShape(7.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RecordingControls() {
    val context = LocalContext.current

    var status by remember {
        mutableStateOf("IDLE")
    }

    var seconds by remember {
        mutableLongStateOf(0L)
    }

    var message by remember {
        mutableStateOf("Ready to record")
    }

    val captureLauncher =
        rememberLauncherForActivityResult(
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
                message = "Screen capture permission denied"
            }
        }

    fun requestScreenCapture() {
        captureLauncher.launch(
            RecordingManager.createScreenCaptureIntent(context)
        )
    }

    val microphoneLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                requestScreenCapture()
            } else {
                message = "Microphone permission required"
            }
        }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action ==
                    RecordingManager.ACTION_STATUS
                ) {
                    status = intent.getStringExtra(
                        RecordingManager.EXTRA_STATUS
                    ) ?: "IDLE"

                    seconds = intent.getLongExtra(
                        RecordingManager.EXTRA_SECONDS,
                        0L
                    )

                    message = intent.getStringExtra(
                        RecordingManager.EXTRA_MESSAGE
                    ) ?: ""
                }
            }
        }

        val filter = IntentFilter(
            RecordingManager.ACTION_STATUS
        )

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

    val timeText = String.format(
        "%02d:%02d:%02d",
        seconds / 3600,
        (seconds % 3600) / 60,
        seconds % 60
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "REC",
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )

            Text(
                timeText,
                color = Color.White,
                fontSize = 16.sp
            )

            Text(
                status,
                color = Accent,
                fontSize = 11.sp
            )

            Spacer(Modifier.weight(1f))

            when (status) {
                "IDLE", "STOPPED", "ERROR" -> {
                    Button(
                        onClick = {
                            if (
                                context.checkSelfPermission(
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                requestScreenCapture()
                            } else {
                                microphoneLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        }
                    ) {
                        Text("START")
                    }
                }

                "RECORDING" -> {
                    Button(
                        onClick = {
                            RecordingManager.pause(context)
                        }
                    ) {
                        Text("PAUSE")
                    }

                    Button(
                        onClick = {
                            RecordingManager.stop(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB3261E)
                        )
                    ) {
                        Text("STOP")
                    }
                }

                "PAUSED" -> {
                    Button(
                        onClick = {
                            RecordingManager.resume(context)
                        }
                    ) {
                        Text("RESUME")
                    }

                    Button(
                        onClick = {
                            RecordingManager.stop(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB3261E)
                        )
                    ) {
                        Text("STOP")
                    }
                }
            }
        }

        Text(
            message,
            color = Muted,
            fontSize = 10.sp
        )
    }
}
