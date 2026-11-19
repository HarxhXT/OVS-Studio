package com.harxhxt.ovsstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    height: Float
) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var width by mutableFloatStateOf(width)
    var height by mutableFloatStateOf(height)
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

    if (preferences.getInt("layout_version", 0) != LAYOUT_VERSION) {
        return defaults
    }

    return defaults.map { dock ->
        val key = dock.type

        Dock(
            dock.title,
            dock.type,
            preferences.getFloat("${key}_x", dock.x),
            preferences.getFloat("${key}_y", dock.y),
            preferences.getFloat("${key}_width", dock.width),
            preferences.getFloat("${key}_height", dock.height)
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

    val layoutSignature = docks.joinToString("|") {
        "${it.type}:${it.x}:${it.y}:${it.width}:${it.height}"
    }

    // Automatic workspace saving
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
            }

            apply()
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
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "OVS STUDIO",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.width(14.dp))

            Text(
                "File   Edit   View   Docks   Help",
                color = Muted,
                fontSize = 11.sp
            )

            Spacer(Modifier.weight(1f))

            Text(
                "WORKSPACE",
                color = Muted,
                fontSize = 10.sp
            )
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

                                    dock.x = (dock.x + dx)
                                        .coerceIn(0f, 1f - dock.width)

                                    dock.y = (dock.y + dy)
                                        .coerceIn(0f, 1f - dock.height)
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

                                        dock.width = (dock.width + dw)
                                            .coerceIn(
                                                0.15f,
                                                1f - dock.x
                                            )

                                        dock.height = (dock.height + dh)
                                            .coerceIn(
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
