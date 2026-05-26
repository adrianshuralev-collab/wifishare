package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import com.example.data.network.FileTransferManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.TransferRecord
import com.example.data.network.NsdHelper
import com.example.ui.viewmodel.TransferViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val localIp by viewModel.localIp.collectAsState()
    val isWifiConnected by viewModel.isWifiConnected.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val activeTransfer by viewModel.activeTransfer.collectAsState()
    val transferHistory by viewModel.transferHistory.collectAsState()
    val isReceiveModeActive by viewModel.isReceiveModeActive.collectAsState()

    var activeTab by remember { mutableStateOf(0) }
    var selectedPeerForPicker by remember { mutableStateOf<NsdHelper.WifiPeer?>(null) }
    var selectForManualIp by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Launcher for file selection (any file types)
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val peer = selectedPeerForPicker
            if (peer != null) {
                viewModel.sendFileToPeer(peer, it)
                selectedPeerForPicker = null
            } else {
                val manual = selectForManualIp
                if (manual != null) {
                    viewModel.sendFileToManualIp(manual.first, manual.second, it)
                    selectForManualIp = null
                }
            }
        } ?: run {
            selectedPeerForPicker = null
            selectForManualIp = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WiFi Share",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isWifiConnected) Icons.Default.SignalWifi4Bar else Icons.Default.SignalWifiBad,
                                contentDescription = "WiFi Status Indicator",
                                modifier = Modifier.size(12.dp),
                                tint = if (isWifiConnected) MaterialTheme.colorScheme.primary else Color.Red
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isWifiConnected) "WiFi IP: $localIp" else "WiFi: disconnected",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshNetworkInfo() },
                        modifier = Modifier.testTag("refresh_network_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh connection status",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab layout switcher
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Send Tab Icon")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Отправить")
                        }
                    },
                    modifier = Modifier.testTag("send_tab")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Receive Tab Icon")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Принять")
                        }
                    },
                    modifier = Modifier.testTag("receive_tab")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = "History Tab Icon")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("История")
                        }
                    },
                    modifier = Modifier.testTag("history_tab")
                )
            }

            // Connection notification banner if Wi-Fi isn't enabled
            if (!isWifiConnected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0x1AFF5252)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "WiFi is disabled",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Wi-Fi не подключен",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Пожалуйста, подключитесь к одной и той же локальной сети Wi-Fi или создайте мобильную точку доступа для обмена файлами.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (activeTab) {
                    0 -> SendTabContent(
                        discoveredPeers = discoveredPeers,
                        isDiscovering = isDiscovering,
                        localDeviceName = viewModel.localDeviceName,
                        onPeerSelected = { peer ->
                            selectedPeerForPicker = peer
                            fileLauncher.launch("*/*")
                        },
                        onManualIpSend = { ip, port ->
                            selectForManualIp = ip to port
                            fileLauncher.launch("*/*")
                        }
                    )
                    1 -> ReceiveTabContent(
                        isReceiveModeActive = isReceiveModeActive,
                        localIp = localIp,
                        localDeviceName = viewModel.localDeviceName,
                        onToggleReceive = { viewModel.toggleReceiveMode() }
                    )
                    2 -> HistoryTabContent(
                        history = transferHistory,
                        onOpen = { filePath -> viewModel.openCompletedFile(filePath) },
                        onDelete = { record -> viewModel.deleteHistoryRecord(record) },
                        onClearAll = { viewModel.clearAllHistory() }
                    )
                }
            }
        }

        // Active transfer overlay dialog
        if (activeTransfer.isTransferring) {
            TransferProgressOverlay(
                state = activeTransfer,
                onDismiss = { viewModel.dismissActiveTransfer() }
            )
        }
    }
}

@Composable
fun PulseRadar(isSearching: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarAnimation")
    
    // Animate radius scalar
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadius"
    )

    // Animate Alpha
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(170.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (size.minDimension / 2f) * 0.95f

            if (isSearching) {
                // Secondary wave
                drawCircle(
                    color = primaryColor,
                    radius = maxRadius * ((pulseRatio + 0.5f) % 1.0f),
                    center = centerOffset,
                    alpha = ((pulseAlpha + 0.4f) % 0.8f),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Main wave
                drawCircle(
                    color = primaryColor,
                    radius = maxRadius * pulseRatio,
                    center = centerOffset,
                    alpha = pulseAlpha,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Central beacon rings
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = maxRadius * 0.35f,
                center = centerOffset
            )
        }

        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SettingsInputAntenna,
                    contentDescription = "Radar Antenna Signal",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun SendTabContent(
    discoveredPeers: List<NsdHelper.WifiPeer>,
    isDiscovering: Boolean,
    localDeviceName: String,
    onPeerSelected: (NsdHelper.WifiPeer) -> Unit,
    onManualIpSend: (String, String) -> Unit
) {
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8888") }
    var showManualIpFields by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Pulsing scanner core
            PulseRadar(isSearching = isDiscovering)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Поиск устройств...",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Ваше имя в сети: $localDeviceName",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (discoveredPeers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Searching Devices Icon Indicator",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Нет доступных устройств",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Откройте приложение на другом телефоне и включите режим 'Принять' во второй вкладке.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            items(discoveredPeers) { peer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onPeerSelected(peer) }
                        .testTag("peer_card_${peer.name}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (peer.name.lowercase().contains("mac") || peer.name.lowercase().contains("pc")) {
                                        Icons.Default.Computer
                                    } else {
                                        Icons.Default.PhoneAndroid
                                    },
                                    contentDescription = "Peer Device Icon Type",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = peer.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "IP: ${peer.ip}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        ElevatedButton(
                            onClick = { onPeerSelected(peer) },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("peer_send_button_${peer.name}")
                        ) {
                            Text("Выбрать")
                        }
                    }
                }
            }
        }

        // Manual Connection IP Backup Drawer
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showManualIpFields = !showManualIpFields }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Inquiry Info Icon",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showManualIpFields) "Скрыть ручной ввод IP" else "Не видно устройство? Ввод IP вручную",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = showManualIpFields) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Прямая передача по IP-адресу",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                label = { Text("IP адрес") },
                                placeholder = { Text("например: 192.168.1.155") },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .testTag("manual_ip_field"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = manualPort,
                                onValueChange = { manualPort = it },
                                label = { Text("Порт") },
                                modifier = Modifier
                                    .weight(0.7f)
                                    .testTag("manual_port_field"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (manualIp.isNotEmpty()) {
                                    onManualIpSend(manualIp, manualPort)
                                }
                            },
                            enabled = manualIp.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("manual_send_action_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Manual Send Icon Button")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выбрать файл и отправить")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ReceiveTabContent(
    isReceiveModeActive: Boolean,
    localIp: String,
    localDeviceName: String,
    onToggleReceive: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(160.dp),
            shape = CircleShape,
            color = if (isReceiveModeActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "WiFi Pulsing Radar Signal",
                    modifier = Modifier.size(80.dp),
                    tint = if (isReceiveModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (isReceiveModeActive) "Ожидание передачи..." else "Режим приема выключен",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isReceiveModeActive) {
                "Держите это приложение открытым на этом экране для автоматического обнаружения другими устройствами."
            } else {
                "Включите режим приема, чтобы другие устройства в вашей Wi-Fi сети могли видеть этот телефон и отправлять файлы."
            },
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Large robust button toggle
        Button(
            onClick = onToggleReceive,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
                .testTag("toggle_receive_mode_btn"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isReceiveModeActive) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                contentColor = if (isReceiveModeActive) Color.White else MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = if (isReceiveModeActive) Icons.Default.WifiOff else Icons.Default.Wifi,
                contentDescription = "Receive State Toggle Button Icon"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isReceiveModeActive) "Остановить прием" else "Включить 'Принять'",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Receiver Connection Info Details Card
        AnimatedVisibility(visible = isReceiveModeActive) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Параметры подключения",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("WiFi Share IP", localIp)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "IP скопирован!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ваш IP:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = localIp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy IP Address",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DividerLight()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Имя в сети:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = localDeviceName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DividerLight() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
fun HistoryTabContent(
    history: List<TransferRecord>,
    onOpen: (String) -> Unit,
    onDelete: (TransferRecord) -> Unit,
    onClearAll: () -> Unit
) {
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Логи передач (${history.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (history.isNotEmpty()) {
                IconButton(
                    onClick = { showConfirmClearDialog = true },
                    modifier = Modifier.testTag("clear_all_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Clear all logs",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Empty History Clock",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "История пока пуста",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Все оправленные и принятые файлы сохраняются в этой вкладке.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_card_${log.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Direction Circle icon
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = if (log.direction == "SENT") {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    } else {
                                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (log.direction == "SENT") Icons.Default.FileUpload else Icons.Default.FileDownload,
                                            contentDescription = "Direction logo icon",
                                            tint = if (log.direction == "SENT") MaterialTheme.colorScheme.primary else Color(0xFF4CAF50),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatFileSize(log.fileSize),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (log.direction == "SENT") "кому: ${log.peerName}" else "из: ${log.peerName}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // State chip
                                Surface(
                                    modifier = Modifier.padding(start = 8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (log.status) {
                                        "SUCCESS" -> Color(0xFFE8F5E9)
                                        else -> Color(0xFFFFEBEE)
                                    }
                                ) {
                                    Text(
                                        text = if (log.status == "SUCCESS") "Успех" else "Ошибка",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = when (log.status) {
                                            "SUCCESS" -> Color(0xFF2E7D32)
                                            else -> Color(0xFFC62828)
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // If RECEIVED and successful, draw functional "Open File" ribbon
                            if (log.direction == "RECEIVED" && log.status == "SUCCESS" && !log.filePath.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                DividerLight()
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onOpen(log.filePath) }
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Open file link",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Открыть файл",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onDelete(log) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Удалить из истории", fontSize = 11.sp)
                                    }
                                }
                            } else {
                                // Default Send Delete
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = formatDate(log.timestamp),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.align(Alignment.CenterVertically).weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { onDelete(log) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(30.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Text("Удалить", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Confirmation dialog
    if (showConfirmClearDialog) {
        Dialog(onDismissRequest = { showConfirmClearDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Очистить историю?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Вы действительно хотите удалить все логи и записи передач? Сами файлы не будут удалены на устройстве.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showConfirmClearDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Отмена")
                        }
                        Button(
                            onClick = {
                                onClearAll()
                                showConfirmClearDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Удалить всё")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferProgressOverlay(
    state: FileTransferManager.ActiveTransferState,
    onDismiss: () -> Unit
) {
    val isComplete = state.status == "DONE"
    val isError = state.status == "ERROR"

    Dialog(
        onDismissRequest = {
            if (isComplete || isError) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = isComplete || isError,
            dismissOnClickOutside = isComplete || isError
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transfer_overlay_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Direction and state icon header
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = when {
                        isError -> Color(0xFFFFF2F2)
                        isComplete -> Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isError -> Icons.Default.SignalWifiBad
                                isComplete -> Icons.Default.OpenInNew
                                state.isSender -> Icons.Default.FileUpload
                                else -> Icons.Default.FileDownload
                            },
                            contentDescription = "Active status glyph",
                            tint = when {
                                isError -> Color(0xFFC62828)
                                isComplete -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when {
                        isError -> "Ошибка передачи"
                        isComplete -> "Передача завершена!"
                        state.status == "CONNECTING" -> "Подключение..."
                        state.isSender -> "Отправка..."
                        else -> "Получение..."
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (state.isSender) "на устройство: ${state.peerName}" else "с устройства: ${state.peerName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // File Details container
                if (state.fileName.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = state.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Общий размер: ${formatFileSize(state.fileSize)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Indicator standard and detail stats
                if (!isComplete && !isError) {
                    if (state.status == "CONNECTING") {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        // Real-time tracking progress
                        val pct = (state.progress * 100).toInt()
                        LinearProgressIndicator(
                            progress = { state.progress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$pct%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = String.format("%.1f Mbps", state.speedMbps),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatFileSize(state.bytesTransferred)} из ${formatFileSize(state.fileSize)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else if (isError) {
                    Text(
                        text = state.errorMessage ?: "Произошел сбой соединения, повторите попытку.",
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else if (isComplete) {
                    Text(
                        text = "Файл успешно передан и сохранен в памяти устройства.",
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Standard Done Dismiss Actions Buttons
                if (isComplete || isError) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("transfer_dismiss_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isError) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isError) "Закрыть" else "Готово")
                    }
                } else {
                    Text(
                        text = "Пожалуйста, не закрывайте приложение во время передачи.",
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// Helpers for representation sizing conversions
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) return "$size B"
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(date)
}
