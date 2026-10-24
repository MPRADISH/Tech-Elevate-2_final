package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CampusAlert
import com.example.data.model.ConnectionState
import com.example.data.model.Hostel
import com.example.data.model.ServerConfig
import com.example.repository.CampusGuardRepository
import com.example.ui.theme.CampusAmber
import com.example.ui.theme.CampusGreen
import com.example.ui.theme.CampusLime
import com.example.ui.theme.CampusNavy
import com.example.ui.theme.CampusPaper
import com.example.ui.theme.CampusRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusGuardApp(
    repository: CampusGuardRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val connectionState by repository.connectionState.collectAsState()
    val connectionError by repository.connectionError.collectAsState()
    val isRegistered by repository.isRegistered.collectAsState()
    val isMonitoringEnabled by repository.isMonitoringEnabled.collectAsState()
    val hostelZone by repository.hostelZone.collectAsState()
    val studentName by repository.studentName.collectAsState()
    val serverUrl by repository.serverUrl.collectAsState()
    val clientId by repository.clientId.collectAsState()
    val serverId by repository.serverId.collectAsState()
    val cursor by repository.cursor.collectAsState()
    val receiptsCount by repository.receiptsCount.collectAsState()
    val clearMarkers by repository.clearMarkers.collectAsState()
    val serverTimeOffset by repository.serverTimeOffset.collectAsState()
    val activeAlert by repository.activeAlert.collectAsState()
    val alertsHistory by repository.alertsHistory.collectAsState()
    val myResponses by repository.myResponses.collectAsState()
    val isAlarmSounding by repository.isAlarmSounding.collectAsState()
    val serverConfig by repository.serverConfig.collectAsState()

    // Open initial setup screen first when phone is not registered yet
    var selectedTab by remember { mutableIntStateOf(if (repository.preferences.isRegistered) 0 else 1) }

    var showHelpDialog by remember { mutableStateOf(false) }
    var helpMessageInput by remember { mutableStateOf("") }
    var targetHelpAlertId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CampusLime,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Shield",
                                    tint = CampusNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CampusGuard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "STUDENT SAFETY APP",
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                },
                actions = {
                    ConnectionPill(connectionState = connectionState)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (isRegistered) {
                                    repository.syncNow()
                                    snackbarHostState.showSnackbar("Sync requested with campus server")
                                } else {
                                    repository.testServerConnection(serverUrl)
                                }
                            }
                        },
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh & Sync",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampusNavy
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(CampusPaper)
        ) {
            // High-priority audio alarm warning banner
            AnimatedVisibility(visible = isAlarmSounding) {
                Surface(
                    color = CampusRed,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EMERGENCY ALARM SOUNDING",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { repository.silenceAlarm() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = CampusRed
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("silence_alarm_button")
                        ) {
                            Text("Silence", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tab Navigation
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = CampusNavy
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Safety & Alarm") },
                    modifier = Modifier.testTag("tab_safety")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Hostel & Server") },
                    modifier = Modifier.testTag("tab_setup")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History & Info") },
                    modifier = Modifier.testTag("tab_history")
                )
            }

            when (selectedTab) {
                0 -> SafetyTab(
                    isRegistered = isRegistered,
                    isMonitoringEnabled = isMonitoringEnabled,
                    hostelZone = hostelZone,
                    serverId = serverId,
                    cursor = cursor,
                    receiptsCount = receiptsCount,
                    connectionError = connectionError,
                    activeAlert = activeAlert,
                    myResponses = myResponses,
                    onGoToSetup = { selectedTab = 1 },
                    onStartMonitoring = { repository.startMonitoring() },
                    onStopMonitoring = { repository.stopMonitoring() },
                    onOpenHelpDialog = { alertId ->
                        targetHelpAlertId = alertId
                        helpMessageInput = ""
                        showHelpDialog = true
                    },
                    onMarkSafe = { alertId ->
                        coroutineScope.launch {
                            val res = repository.respondToAlert(alertId, "safe", null)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Marked safe! Admin dashboard updated.")
                            } else {
                                snackbarHostState.showSnackbar("Saved locally; server unreachable.")
                            }
                        }
                    },
                    onTestAlarm = {
                        repository.triggerTestAlarm()
                    }
                )

                1 -> SetupTab(
                    repository = repository,
                    isRegistered = isRegistered,
                    savedServerUrl = serverUrl,
                    savedStudentName = studentName,
                    savedHostelZone = hostelZone,
                    clientId = clientId,
                    serverConfig = serverConfig,
                    connectionError = connectionError,
                    onShowMessage = { msg ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onRegisteredSuccess = {
                        selectedTab = 0
                    }
                )

                2 -> HistoryAndInfoTab(
                    alerts = alertsHistory,
                    studentZone = hostelZone,
                    clearMarkers = clearMarkers,
                    serverTimeOffset = serverTimeOffset,
                    myResponses = myResponses
                )
            }
        }
    }

    if (showHelpDialog && targetHelpAlertId != null) {
        val alertId = targetHelpAlertId!!
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CampusRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Assistance", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Tell campus staff what you need and your room/location details (max 400 bytes). This will be shown on the admin operations dashboard.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = helpMessageInput,
                        onValueChange = { if (it.toByteArray(Charsets.UTF_8).size <= 400) helpMessageInput = it },
                        label = { Text("Where are you and what help do you need?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("help_message_input"),
                        minLines = 3,
                        maxLines = 5
                    )
                    Text(
                        text = "${helpMessageInput.toByteArray(Charsets.UTF_8).size} / 400 bytes",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = helpMessageInput.trim()
                        showHelpDialog = false
                        coroutineScope.launch {
                            val res = repository.respondToAlert(alertId, "needs-help", msg)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Help request sent to Admin Operations!")
                            } else {
                                snackbarHostState.showSnackbar("Help request saved locally; server unreachable.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CampusRed),
                    modifier = Modifier.testTag("submit_help_button")
                ) {
                    Text("Send Help Request", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showHelpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ConnectionPill(connectionState: ConnectionState) {
    val (color, text) = when (connectionState) {
        ConnectionState.CONNECTED_WS -> Pair(CampusLime, "WS LIVE")
        ConnectionState.CONNECTED_POLLING -> Pair(CampusAmber, "POLLING")
        ConnectionState.CONNECTING -> Pair(CampusAmber, "CONNECTING")
        ConnectionState.OFFLINE -> Pair(Color.LightGray, "OFFLINE")
        ConnectionState.SERVER_ERROR -> Pair(CampusRed, "UNREACHABLE")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CampusNavy.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, color),
        modifier = Modifier
            .padding(end = 4.dp)
            .testTag("connection_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ConnectionErrorBanner(error: String?) {
    if (!error.isNullOrBlank()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0ED)),
            border = BorderStroke(1.dp, CampusRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connection_error_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Connection Error",
                    tint = CampusRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Laptop Server Unreachable",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CampusRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = CampusNavy
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ensure the phone and laptop are on the same Wi-Fi/hotspot and START-CAMPUSGUARD.cmd is running.",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

@Composable
fun SafetyTab(
    isRegistered: Boolean,
    isMonitoringEnabled: Boolean,
    hostelZone: Int,
    serverId: String?,
    cursor: Long,
    receiptsCount: Int,
    connectionError: String?,
    activeAlert: CampusAlert?,
    myResponses: Map<String, String>,
    onGoToSetup: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onOpenHelpDialog: (String) -> Unit,
    onMarkSafe: (String) -> Unit,
    onTestAlarm: () -> Unit
) {
    val studentHostel = Hostel.getDisplayName(hostelZone)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!connectionError.isNullOrBlank()) {
            item {
                ConnectionErrorBanner(error = connectionError)
            }
        }

        if (!isRegistered) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4EE)),
                    border = BorderStroke(1.dp, CampusAmber),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Phone Not Registered Yet",
                            fontWeight = FontWeight.Bold,
                            color = CampusNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Configure your laptop server URL, student name, and hostel to receive live campus alerts.",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onGoToSetup,
                            colors = ButtonDefaults.buttonColors(containerColor = CampusNavy),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("open_setup_button")
                        ) {
                            Text("Open Hostel & Server Setup →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (activeAlert != null) {
            val alert = activeAlert
            val userResponse = myResponses[alert.id]

            item {
                val cardColor = when (alert.severity) {
                    3 -> Color(0xFFFFF0ED)
                    2 -> Color(0xFFFFF9EE)
                    else -> Color(0xFFF0F7EE)
                }
                val borderColor = when (alert.severity) {
                    3 -> CampusRed
                    2 -> CampusAmber
                    else -> CampusGreen
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = BorderStroke(2.dp, borderColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = borderColor,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ACTIVE ALERT • SEVERITY ${alert.severity}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "Seq #${alert.seq}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "${alert.type.uppercase()} ALERT",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = CampusNavy
                        )

                        Text(
                            text = "Target Hostels: ${alert.zonesLabel}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = alert.message,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = CampusNavy
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFD0D7CD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = CampusGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "DESIGNATED ASSEMBLY POINT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CampusGreen
                                    )
                                    Text(
                                        text = alert.assemblyPointDisplayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CampusNavy
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onMarkSafe(alert.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (userResponse == "safe") CampusGreen.copy(alpha = 0.8f) else CampusGreen
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mark_safe_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (userResponse == "safe") "✓ Marked Safe" else "I'm Safe",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = { onOpenHelpDialog(alert.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (userResponse == "needs-help") CampusRed.copy(alpha = 0.8f) else CampusRed
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("need_help_button")
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (userResponse == "needs-help") "Help Sent" else "I Need Help",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (userResponse != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your response status: ${userResponse.uppercase()}",
                                fontSize = 11.sp,
                                color = if (userResponse == "safe") CampusGreen else CampusRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1E1)),
                    border = BorderStroke(1.dp, Color(0xFFD4E1C5)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFDCEBCF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Calm",
                                    tint = CampusGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "CAMPUSGUARD IS LISTENING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = CampusGreen
                                )
                                Text(
                                    text = "No active alerts for $studentHostel",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CampusNavy
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Foreground monitoring runs independently of this screen. When an emergency or drill is broadcast for your hostel, audible sirens, vibration, and response actions trigger automatically.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF556A5B)
                        )
                    }
                }
            }
        }

        // Monitoring Service Control Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E5DC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FOREGROUND MONITORING SERVICE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            color = if (isMonitoringEnabled) Color(0xFFEDF7E8) else Color(0xFFF2F2F2),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isMonitoringEnabled) "ACTIVE" else "STOPPED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMonitoringEnabled) CampusGreen else Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Continuous Background Protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CampusNavy
                    )
                    Text(
                        text = "Maintains the WebSocket and periodic sync connection with an ongoing notification even when the app screen is closed.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isMonitoringEnabled) {
                            Button(
                                onClick = onStopMonitoring,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF708084)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_monitoring_button")
                            ) {
                                Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Monitoring", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = onStartMonitoring,
                                colors = ButtonDefaults.buttonColors(containerColor = CampusNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_monitoring_button")
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Monitoring", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = onTestAlarm,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_alarm_button")
                        ) {
                            Text("Test Notification", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Live Delivery Diagnostics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E5DC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LIVE PROTOCOL DIAGNOSTICS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiagnosticItem(label = "Server ID", value = serverId?.uppercase() ?: "NONE")
                        DiagnosticItem(label = "Sequence Cursor", value = "#$cursor")
                        DiagnosticItem(label = "Receipts Sent", value = "$receiptsCount")
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 9.sp, color = Color.Gray)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = CampusNavy
        )
    }
}

@Composable
fun SetupTab(
    repository: CampusGuardRepository,
    isRegistered: Boolean,
    savedServerUrl: String,
    savedStudentName: String,
    savedHostelZone: Int,
    clientId: String?,
    serverConfig: ServerConfig?,
    connectionError: String?,
    onShowMessage: (String) -> Unit,
    onRegisteredSuccess: () -> Unit
) {
    var serverUrlInput by remember(savedServerUrl) { mutableStateOf(savedServerUrl) }
    var studentNameInput by remember(savedStudentName) { mutableStateOf(savedStudentName) }
    var selectedHostelId by remember(savedHostelZone) { mutableIntStateOf(savedHostelZone) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var isRegistering by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!connectionError.isNullOrBlank()) {
            item {
                ConnectionErrorBanner(error = connectionError)
            }
        }

        // Laptop Server URL Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E5DC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "01 • LAPTOP SERVER CONFIGURATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Local Wi-Fi Server Address",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CampusNavy
                    )
                    Text(
                        text = "Enter your laptop's printed LAN URL including port (e.g. http://192.168.1.100:8080).",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Server URL (including port)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isTestingConnection = true
                            coroutineScope.launch {
                                val res = repository.testServerConnection(serverUrlInput)
                                isTestingConnection = false
                                if (res.isSuccess) {
                                    val config = res.getOrNull()
                                    onShowMessage("Connected! Server ID: ${config?.serverId?.uppercase()}")
                                } else {
                                    onShowMessage("Server unreachable: ${res.exceptionOrNull()?.localizedMessage}")
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        colors = ButtonDefaults.buttonColors(containerColor = CampusNavy),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_server_button")
                    ) {
                        Text(
                            text = if (isTestingConnection) "Testing Connection..." else "Test & Save Server URL",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (serverConfig != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "✓ Connected to server ${serverConfig.serverId.uppercase()}",
                            fontSize = 11.sp,
                            color = CampusGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Student Profile & Hostel Selection Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E5DC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "02 • STUDENT ENROLLMENT & HOSTEL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Register Phone Credentials",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CampusNavy
                    )
                    Text(
                        text = "Select your hostel from the 4 campus residences. Credentials persist locally on this phone.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = studentNameInput,
                        onValueChange = { studentNameInput = it },
                        label = { Text("Student Name (e.g. Pradish)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_name_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Select Hostel Residence:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CampusNavy
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2x2 Grid for the 4 Hostels (1..4)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Hostel.ALL_HOSTELS.chunked(2).forEach { rowHostels ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowHostels.forEach { hostel ->
                                    val isSelected = selectedHostelId == hostel.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedHostelId = hostel.id },
                                        label = {
                                            Text(
                                                text = "${hostel.id} • ${hostel.name}",
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CampusLime,
                                            selectedLabelColor = CampusNavy
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("hostel_select_${hostel.id}")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isRegistered) {
                        Button(
                            onClick = {
                                isRegistering = true
                                coroutineScope.launch {
                                    val res = repository.register(studentNameInput, selectedHostelId, serverUrlInput)
                                    isRegistering = false
                                    if (res.isSuccess) {
                                        onShowMessage("Registered in ${Hostel.getDisplayName(selectedHostelId)}!")
                                        onRegisteredSuccess()
                                    } else {
                                        onShowMessage("Registration failed: ${res.exceptionOrNull()?.localizedMessage}")
                                    }
                                }
                            },
                            enabled = !isRegistering,
                            colors = ButtonDefaults.buttonColors(containerColor = CampusGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_button")
                        ) {
                            Text(
                                text = if (isRegistering) "Registering..." else "Register Phone as Student",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.saveServerUrlOnly(serverUrlInput)
                                        val res = repository.changeHostel(selectedHostelId)
                                        if (res.isSuccess) {
                                            onShowMessage("Hostel updated to ${Hostel.getDisplayName(selectedHostelId)}!")
                                        } else {
                                            onShowMessage("Update failed: ${res.exceptionOrNull()?.localizedMessage}")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CampusNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("change_hostel_button")
                            ) {
                                Text("Update Hostel", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    isRegistering = true
                                    coroutineScope.launch {
                                        val res = repository.register(studentNameInput, selectedHostelId, serverUrlInput)
                                        isRegistering = false
                                        if (res.isSuccess) {
                                            onShowMessage("Re-registered as ${studentNameInput.ifBlank { "Student" }}!")
                                        }
                                    }
                                },
                                enabled = !isRegistering,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Re-Register", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enrolled as: $savedStudentName • ${Hostel.getDisplayName(savedHostelZone)} (ID: ${clientId?.take(8)}...)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CampusGreen
                        )
                    }
                }
            }
        }

        // Campus Emergency Contacts
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E5DC))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EMERGENCY CAMPUS CONTACTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ContactRow(
                        title = "Medical Support",
                        number = serverConfig?.medicalPhone ?: "8571893627"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ContactRow(
                        title = "Campus Security",
                        number = serverConfig?.securityPhone?.takeIf { it.isNotEmpty() } ?: "Configured on Admin"
                    )
                }
            }
        }
    }
}

@Composable
fun ContactRow(title: String, number: String) {
    Surface(
        color = Color(0xFFF7F9F5),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CampusNavy)
                Text(text = number, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color.DarkGray)
            }
            Icon(Icons.Default.Phone, contentDescription = null, tint = CampusGreen, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun HistoryAndInfoTab(
    alerts: List<CampusAlert>,
    studentZone: Int,
    clearMarkers: Map<Int, Long>,
    serverTimeOffset: Long,
    myResponses: Map<String, String>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6FB)),
                border = BorderStroke(1.dp, Color(0xFFD4DCED)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2C56A6))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android System Limitations & Demo Notes",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A70)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Local Wi-Fi: Works over local router without internet. Devices must be connected to the same Wi-Fi or laptop hotspot.\n" +
                            "• Battery Optimization / Doze: On modern Android (12+), disable battery restrictions for CampusGuard under App Info -> Battery -> Unrestricted for instantaneous sirens during drills.\n" +
                            "• Force-Stop: If the user explicitly force-stops the app in Android Settings, the OS blocks background services until the app is opened again.\n" +
                            "• Expiry & All-Clear: All-clear messages immediately silence sirens and mark prior alerts as cleared.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF334B75)
                    )
                }
            }
        }

        item {
            Text(
                text = "Alert History (${alerts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CampusNavy
            )
        }

        if (alerts.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0E5DC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No alerts recorded yet. Send a test drill from the web operations center.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            items(alerts, key = { it.id }) { alert ->
                AlertHistoryCard(
                    alert = alert,
                    studentZone = studentZone,
                    clearMarkers = clearMarkers,
                    serverTimeOffset = serverTimeOffset,
                    responseStatus = myResponses[alert.id]
                )
            }
        }
    }
}

@Composable
fun AlertHistoryCard(
    alert: CampusAlert,
    studentZone: Int,
    clearMarkers: Map<Int, Long>,
    serverTimeOffset: Long,
    responseStatus: String?
) {
    val isApplicable = alert.isApplicable(studentZone)
    val isCleared = alert.isCleared(clearMarkers, studentZone)
    val isExpired = alert.isExpired(serverTimeOffset)

    val statusTag = when {
        alert.type == "all-clear" -> "All-Clear Notice"
        isCleared -> "Cleared by All-Clear"
        isExpired -> "Expired"
        isApplicable -> "Active Alert"
        else -> "Targeted to Other Hostels"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E5DC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${alert.type.uppercase()} • Seq #${alert.seq}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CampusNavy
                )
                Surface(
                    color = when {
                        alert.type == "all-clear" -> Color(0xFFEDF7E8)
                        isCleared || isExpired -> Color(0xFFF0F0F0)
                        else -> Color(0xFFFFF0ED)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusTag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            alert.type == "all-clear" -> CampusGreen
                            isCleared || isExpired -> Color.Gray
                            else -> CampusRed
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = alert.message,
                fontSize = 13.sp,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hostels: ${alert.zonesLabel}",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                if (responseStatus != null) {
                    Text(
                        text = "Response: ${responseStatus.uppercase()}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (responseStatus == "safe") CampusGreen else CampusRed
                    )
                }
            }
        }
    }
}
