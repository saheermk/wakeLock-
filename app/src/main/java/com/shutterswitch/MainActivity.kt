package com.shutterswitch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification will show if granted; silently ignored if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request SYSTEM_ALERT_WINDOW permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please allow 'Display over other apps' to keep the screen on", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        setContent {
            ShutterSwitchTheme {
                ShutterSwitchScreen(
                    onSwitchOn = { startWakeLockService() },
                    onSwitchOff = { stopWakeLockService() }
                )
            }
        }
    }

    private fun startWakeLockService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permission required: Display over other apps", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        val intent = Intent(this, WakeLockService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopWakeLockService() {
        val intent = Intent(this, WakeLockService::class.java)
        stopService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the service when activity is destroyed to avoid orphaned wake locks
        stopWakeLockService()
    }
}

// ─────────────────────────────────────────────
// Compose UI
// ─────────────────────────────────────────────

@Composable
fun ShutterSwitchScreen(
    onSwitchOn: () -> Unit,
    onSwitchOff: () -> Unit
) {
    val isOn by WakeLockService.isServiceRunning.collectAsState()

    // Animate background gradient
    val bgColorTop by animateColorAsState(
        targetValue = if (isOn) Color(0xFF0A1628) else Color(0xFF1A1A2E),
        animationSpec = tween(600), label = "bgTop"
    )
    val bgColorBottom by animateColorAsState(
        targetValue = if (isOn) Color(0xFF001F4D) else Color(0xFF16213E),
        animationSpec = tween(600), label = "bgBottom"
    )

    // Glow scale animation
    val glowScale by animateFloatAsState(
        targetValue = if (isOn) 1.4f else 0.6f,
        animationSpec = spring(stiffness = Spring.StiffnessLow), label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColorTop, bgColorBottom))),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow blob behind the switch
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(glowScale)
                .blur(80.dp)
                .background(
                    color = if (isOn) Color(0x6600CFFF) else Color(0x22334455),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App title
            Text(
                text = "NO\nSLEEP",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                lineHeight = 42.sp,
                color = Color(0xFFE0F4FF),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status subtitle
            val statusText by remember(isOn) {
                derivedStateOf { if (isOn) "NO SLEEP ACTIVE" else "DEVICE CAN SLEEP" }
            }
            val statusColor by animateColorAsState(
                targetValue = if (isOn) Color(0xFF00CFFF) else Color(0xFF667788),
                animationSpec = tween(400), label = "statusColor"
            )
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(72.dp))

            // The large Shutter Switch toggle
            LargeShutterToggle(
                isOn = isOn,
                onToggle = { newState ->
                    if (newState) onSwitchOn() else onSwitchOff()
                }
            )

            Spacer(modifier = Modifier.height(56.dp))

            // Info card
            InfoCard(isOn = isOn)

            Spacer(modifier = Modifier.height(32.dp))

            // Developer Info
            DeveloperInfo()
        }
    }
}

@Composable
fun LargeShutterToggle(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF005F7A) else Color(0xFF1E2A38),
        animationSpec = tween(400), label = "track"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF00CFFF) else Color(0xFF445566),
        animationSpec = tween(400), label = "thumb"
    )
    val thumbOffsetDp by animateDpAsState(
        targetValue = if (isOn) 68.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbOffset"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Track
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(trackColor)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Thumb
            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
                contentAlignment = Alignment.Center
            ) {
                // Power icon indicator
                Icon(
                    imageVector = if (isOn) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = "Toggle Icon",
                    modifier = Modifier.size(32.dp),
                    tint = if (isOn) Color(0xFF001A22) else Color(0xFF223344)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ON / OFF tap button
        Button(
            onClick = { onToggle(!isOn) },
            modifier = Modifier
                .width(160.dp)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOn) Color(0xFF00CFFF) else Color(0xFF2A3A4A),
                contentColor = if (isOn) Color(0xFF001A22) else Color(0xFF99BBCC)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (isOn) 8.dp else 2.dp
            )
        ) {
            Text(
                text = if (isOn) "TURN OFF" else "TURN ON",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun InfoCard(isOn: Boolean) {
    val cardBg by animateColorAsState(
        targetValue = if (isOn) Color(0x2200CFFF) else Color(0x111E2A38),
        animationSpec = tween(500), label = "cardBg"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = cardBg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isOn)
                    "☀️ Screen is forced ON\nYour device will not sleep while this is active."
                else
                    "💡 Quick Tip:\nPull down your Quick Settings (notification panel), tap the edit icon, and add the 'No Sleep' tile for easy access!",
                fontSize = 14.sp,
                color = if (isOn) Color(0xFFAAEEFF) else Color(0xFF88AABB),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun DeveloperInfo() {
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Developed by saheermk",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00CFFF)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Creative Developer blending design\nand engineering to create immersive apps.",
                fontSize = 12.sp,
                color = Color(0xFF667788),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Socials Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌐 Website",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFAAEEFF),
                    modifier = Modifier.clickable { uriHandler.openUri("https://saheermk.pages.dev") }
                )
                Text(
                    text = "💼 LinkedIn",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFAAEEFF),
                    modifier = Modifier.clickable { uriHandler.openUri("https://in.linkedin.com/in/saheermk") }
                )
                Text(
                    text = "💻 GitHub",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFAAEEFF),
                    modifier = Modifier.clickable { uriHandler.openUri("https://github.com/saheermk/") }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Theme
// ─────────────────────────────────────────────

@Composable
fun ShutterSwitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00CFFF),
            background = Color(0xFF0A1628),
            surface = Color(0xFF16213E)
        ),
        content = content
    )
}
