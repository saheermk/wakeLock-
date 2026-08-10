package com.shutterswitch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
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

    private var downloadId: Long = -1

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId && downloadId != -1L) {
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val uri = downloadManager.getUriForDownloadedFile(downloadId)
                if (uri != null) {
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    startActivity(installIntent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

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
                    onSwitchOff = { stopWakeLockService() },
                    packageManager = packageManager,
                    packageName = packageName,
                    onDownloadRequest = { url -> downloadAndInstallUpdate(url) }
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

    private fun downloadAndInstallUpdate(url: String) {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("App Update")
            setDescription("Downloading latest version...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "app-update.apk")
        }
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)
        Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
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
    onSwitchOff: () -> Unit,
    packageManager: PackageManager,
    packageName: String,
    onDownloadRequest: (String) -> Unit
) {
    val isOn by WakeLockService.isServiceRunning.collectAsState()

    val currentVersion = remember {
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var newVersion by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        thread {
            try {
                val url = URL("https://api.github.com/repos/saheermk/no-sleep/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val cleanTagName = tagName.removePrefix("v")
                    
                    if (cleanTagName != currentVersion) {
                        val assets = json.getJSONArray("assets")
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                newVersion = cleanTagName
                                apkUrl = asset.getString("browser_download_url")
                                showUpdateDialog = true
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Animate background gradient
    val bgColorTop by animateColorAsState(
        targetValue = if (isOn) Color(0xFF070B18) else Color(0xFF0A0B0E),
        animationSpec = tween(800), label = "bgTop"
    )
    val bgColorBottom by animateColorAsState(
        targetValue = if (isOn) Color(0xFF0E162D) else Color(0xFF111216),
        animationSpec = tween(800), label = "bgBottom"
    )

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Available") },
            text = { Text("A new version (v$newVersion) is available. Would you like to update?") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    onDownloadRequest(apkUrl)
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColorTop, bgColorBottom))),
        contentAlignment = Alignment.Center
    ) {
        // Layered Ambient Glow Blob
        val glowSize by animateDpAsState(
            targetValue = if (isOn) 340.dp else 240.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
            label = "glowSize"
        )
        val glowAlpha by animateFloatAsState(
            targetValue = if (isOn) 0.28f else 0.08f,
            animationSpec = tween(800),
            label = "glowAlpha"
        )
        Box(
            modifier = Modifier
                .size(glowSize)
                .alpha(glowAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x3D7A5AF8),
                            Color(0x1FDF71FF),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // App Title Group
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "NO SLEEP",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = Color(0xFFEAF2FF),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "KEEP YOUR SCREEN AWAKE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    color = Color(0xFF6B7E96),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // The glowing Power Core control dial
            PowerCoreControl(
                isOn = isOn,
                onToggle = { newState ->
                    if (newState) onSwitchOn() else onSwitchOff()
                }
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Redesigned modern Info Card
            InfoCard(isOn = isOn)

            Spacer(modifier = Modifier.height(20.dp))

            // Redesigned modern Developer Profile
            DeveloperInfo()

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun PowerCoreControl(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Tactile Click Hold-Down state to make rapid touches feel satisfyingly mechanical
    val clickTrigger = remember { mutableStateOf(0) }
    var isAnimatingClick by remember { mutableStateOf(false) }
    LaunchedEffect(clickTrigger.value) {
        if (clickTrigger.value > 0) {
            isAnimatingClick = true
            kotlinx.coroutines.delay(120) // Holds the button down for a realistic duration
            isAnimatingClick = false
        }
    }

    val isVisualPressed = isPressed || isAnimatingClick

    // Snappy physical scaling on press (scale down to 0.93)
    val buttonScale by animateFloatAsState(
        targetValue = if (isVisualPressed) 0.93f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )

    // Translation along both X & Y axes to simulate pushing diagonally down-right into the screen
    val buttonTranslationY by animateDpAsState(
        targetValue = if (isVisualPressed) 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonTranslationY"
    )
    val buttonTranslationX by animateDpAsState(
        targetValue = if (isVisualPressed) 1.5.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonTranslationX"
    )

    // Outer Shadows/Highlights opacity (completely disappear on click/press)
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isVisualPressed) 0f else 0.45f,
        animationSpec = tween(150),
        label = "shadowAlpha"
    )

    // Inner bottom lip bevel thickness (representing depth/inset shadow which flattens on click)
    val insetBevelHeight by animateDpAsState(
        targetValue = if (isVisualPressed) 0.dp else 4.dp,
        animationSpec = tween(150),
        label = "insetBevelHeight"
    )

    // Button colors (Purple/Pink when active, Slate/Obsidian when inactive)
    val btnBgColorStart by animateColorAsState(
        targetValue = if (isOn) Color(0xFF7A5AF8) else Color(0xFF1E2230),
        animationSpec = tween(300), label = "btnBgColorStart"
    )
    val btnBgColorEnd by animateColorAsState(
        targetValue = if (isOn) Color(0xFF7A5AF8) else Color(0xFF151821),
        animationSpec = tween(300), label = "btnBgColorEnd"
    )
    val btnBorderColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF9E7EFE) else Color(0xFF3A3F50),
        animationSpec = tween(300), label = "btnBorderColor"
    )

    // Infinite rotation for the active glowing arc/ring on the socket boundary
    val infiniteTransition = rememberInfiniteTransition(label = "rotationTransition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Particles raw progress loop for floating points
    val rawProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rawProgress"
    )

    // Pulse animation for the central icon when active
    val iconPulse by if (isOn) {
        val pulseTransition = rememberInfiniteTransition(label = "pulseTransition")
        pulseTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconPulse"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    // Glowing rim gradient colors (Purple to Pink/Magenta)
    val glowColorStart by animateColorAsState(
        targetValue = if (isOn) Color(0xFF7A5AF8) else Color(0x224C5B75),
        animationSpec = tween(600), label = "glowColorStart"
    )
    val glowColorEnd by animateColorAsState(
        targetValue = if (isOn) Color(0xFFDF71FF) else Color(0x224C5B75),
        animationSpec = tween(600), label = "glowColorEnd"
    )

    // Active status light color
    val statusLightColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFFDF71FF) else Color(0x224C5B75),
        animationSpec = tween(400), label = "statusLightColor"
    )

    Box(
        modifier = Modifier
            .size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Background Aura (Purple and Pink)
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isOn) {
                            listOf(Color(0x337A5AF8), Color(0x0CDF71FF), Color.Transparent)
                        } else {
                            listOf(Color(0x04FFFFFF), Color.Transparent)
                        }
                    ),
                    shape = CircleShape
                )
        )

        // 2. Sweeping Rotating Bezel Outline (Circular track around the button)
        Box(
            modifier = Modifier
                .size(192.dp)
                .rotate(if (isOn) rotationAngle else 0f)
                .padding(2.dp)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(glowColorStart, glowColorEnd, glowColorStart)
                    ),
                    shape = CircleShape
                )
        )

        // 3. Dynamic Outer 3D Drop Shadows (disappear completely when clicked/pressed)
        // Bottom-Right Dark Shadow
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 10.dp)
                .size(156.dp)
                .alpha(shadowAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x59000000), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        // Top-Left Light Accent Shadow
        Box(
            modifier = Modifier
                .offset(x = (-3).dp, y = (-8).dp)
                .size(156.dp)
                .alpha(shadowAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1FFFFFFF), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // 4. Round Physical 3D Tactile Button Plate (CircleShape)
        Box(
            modifier = Modifier
                .offset(x = buttonTranslationX, y = buttonTranslationY)
                .scale(buttonScale)
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(btnBgColorStart, btnBgColorEnd)
                    )
                )
                .border(2.dp, btnBorderColor, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    clickTrigger.value++
                    onToggle(!isOn)
                }
        ) {
            // Draw radial glow overlay at the bottom when active (matches CSS button::after radial component)
            if (isOn) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xB3DF71FF), Color.Transparent),
                                    radius = size.width * 0.7f,
                                    center = Offset(x = size.width / 2f, y = size.height)
                                )
                            )
                        }
                )
            }

            // 5. Floating Points Animation (Particles field inside the button)
            if (isOn) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                ) {
                    val width = size.width
                    val height = size.height

                    // 10 custom particles mapped from the CSS definition
                    val particles = listOf(
                        // xPercent, delayOffset, durationFactor
                        kotlin.Triple(0.15f, 0.2f, 1.0f),
                        kotlin.Triple(0.32f, 0.5f, 0.9f),
                        kotlin.Triple(0.24f, 0.1f, 1.1f),
                        kotlin.Triple(0.42f, 0.0f, 0.85f),
                        kotlin.Triple(0.50f, 0.3f, 1.05f),
                        kotlin.Triple(0.72f, 0.6f, 1.15f),
                        kotlin.Triple(0.85f, 0.2f, 0.95f),
                        kotlin.Triple(0.58f, 0.8f, 1.0f),
                        kotlin.Triple(0.92f, 0.1f, 0.8f),
                        kotlin.Triple(0.64f, 0.4f, 0.9f)
                    )

                    particles.forEach { pData ->
                        val xPercent = pData.first
                        val delayOffset = pData.second
                        val durationFactor = pData.third
                        
                        // Map progress for this particle
                        val p = (rawProgress / durationFactor + delayOffset) % 1.0f
                        
                        // Calculate coordinate positions
                        val px = width * xPercent
                        val py = height - (p * (height + 20.dp.toPx())) // float upwards
                        
                        // Calculate opacity fade out towards 85% - 100%
                        val alpha = if (p < 0.85f) {
                            1.0f - (p / 0.85f) * 0.4f // gradual fade
                        } else {
                            (1.0f - p) / 0.15f // quick fade to zero
                        }
                        
                        // Render point
                        drawCircle(
                            color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
                            radius = 2.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // 6. Box (Rounded Square) inside the round button (centered, glassmorphic)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isOn) {
                            Brush.linearGradient(
                                listOf(Color(0x22FFFFFF), Color(0x06FFFFFF))
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(Color(0x0DFFFFFF), Color(0x05FFFFFF))
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isOn) Color(0x4DFFFFFF) else Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Central Icon (Sun/Moon)
                Icon(
                    painter = painterResource(id = if (isOn) R.drawable.ic_sun else R.drawable.ic_moon),
                    contentDescription = "Power Icon",
                    modifier = Modifier
                        .size(38.dp)
                        .scale(iconPulse),
                    tint = if (isOn) Color(0xFFFFB300) else Color(0xFF5B6E85)
                )
            }

            // LED Indicator at Top-Center (simulates dynamic switch status)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusLightColor)
            )

            // Inset Bevel Line at the bottom (simulates the inset lip)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(insetBevelHeight)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}

@Composable
fun InfoCard(isOn: Boolean) {
    val cardBg by animateColorAsState(
        targetValue = if (isOn) Color(0x0FFFFFFF) else Color(0x0AFFFFFF),
        animationSpec = tween(500), label = "cardBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon container with dynamic light status
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isOn) Color(0x1FFF9E00) else Color(0x0AFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = if (isOn) R.drawable.ic_sun else R.drawable.ic_lightbulb),
                    contentDescription = "Tips Icon",
                    modifier = Modifier.size(20.dp),
                    tint = if (isOn) Color(0xFFFF9E00) else Color(0xFF8FA3B6)
                )
            }

            // Tip/Status Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isOn) "Active Mode Engaged" else "Quick Settings Setup",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOn) Color(0xFFFFC400) else Color(0xFFEAF2FF)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isOn)
                        "Screen is forced ON. Your device will not sleep while this is active."
                    else
                        "Swipe down twice from the top of your screen, tap the Edit (pencil) icon, then find and drag the 'No Sleep' tile into your active Quick Settings panel for instant access.",
                    fontSize = 12.sp,
                    color = Color(0xFF8FA3B6),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun DeveloperInfo() {
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x07FFFFFF))
            .border(1.dp, Color(0x0CFFFFFF), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title Header
            Text(
                text = "ABOUT THE DEVELOPER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color(0xFF5B6E85)
            )
            
            Spacer(modifier = Modifier.height(14.dp))

            // Profile info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Developer Logo Image
                Image(
                    painter = painterResource(id = R.drawable.saheermk),
                    contentDescription = "saheermk Logo",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x3300E5FF), CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "saheermk",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEAF2FF)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Fullstack Developer & Designer",
                        fontSize = 12.sp,
                        color = Color(0xFF8FA3B6)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Social Tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SocialPill(
                    iconId = R.drawable.ic_website,
                    label = "Website",
                    onClick = { uriHandler.openUri("https://saheermk.pages.dev") },
                    modifier = Modifier.weight(1f)
                )
                SocialPill(
                    iconId = R.drawable.ic_linkedin,
                    label = "LinkedIn",
                    onClick = { uriHandler.openUri("https://in.linkedin.com/in/saheermk") },
                    modifier = Modifier.weight(1f)
                )
                SocialPill(
                    iconId = R.drawable.ic_github,
                    label = "GitHub",
                    onClick = { uriHandler.openUri("https://github.com/saheermk/") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SocialPill(
    iconId: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.16f else 0.06f,
        animationSpec = tween(200), label = "pillBgAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = bgAlpha))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconId),
                contentDescription = label,
                modifier = Modifier.size(15.dp),
                tint = Color(0xFFEAF2FF)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFEAF2FF)
            )
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
            primary = Color(0xFF00E5FF),
            background = Color(0xFF0A0B0E),
            surface = Color(0xFF141720)
        ),
        content = content
    )
}
