
package com.harxhxt.ovsstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF101318)
private val Panel = Color(0xFF1B2028)
private val Accent = Color(0xFF80CBC4)
private val Muted = Color(0xFF929BA8)

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

@Composable
fun OvsWorkspace() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(8.dp)
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
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.width(16.dp))

            Text(
                "File   Edit   View   Docks   Help",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.weight(1f))

            Text(
                "ANDROID WORKSPACE",
                color = Muted,
                fontSize = 10.sp
            )
        }

        Divider(color = Color(0xFF303640))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DockPanel(
                title = "SCENES",
                modifier = Modifier.weight(0.18f)
            )

            Column(
                modifier = Modifier.weight(0.52f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewPanel(
                    modifier = Modifier.weight(1f)
                )

                DockPanel(
                    title = "SOURCES",
                    modifier = Modifier.height(105.dp)
                )
            }

            Column(
                modifier = Modifier.weight(0.30f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DockPanel(
                    title = "AUDIO MIXER",
                    modifier = Modifier.weight(1f)
                )

                DockPanel(
                    title = "SCENE TRANSITIONS",
                    modifier = Modifier.height(105.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkspaceButton("START STREAMING")
            WorkspaceButton("START RECORDING")
            WorkspaceButton("STUDIO MODE")
            WorkspaceButton("SETTINGS")
        }
    }
}

@Composable
fun DockPanel(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            title,
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Not configured yet",
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun PreviewPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF080A0D), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "PREVIEW",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "OVS Studio",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Broadcast workspace foundation",
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun WorkspaceButton(label: String) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        color = Panel,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
