@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.droidshark60

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

// Data classes
data class BroadcastPacket(
    val timestamp: String,
    val sourceIP: String,
    val sourcePort: Int,
    val data: String
)

data class NetworkDevice(
    val ip: String,
    val isReachable: Boolean,
    val hostname: String = "Unknown"
)

data class NetworkService(
    val name: String,
    val host: String,
    val port: Int,
    val type: String
)

// Main Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetSnifferApp(this)
        }
    }
}

@Composable
fun NetSnifferApp(context: Context) {
    var selectedTab by remember { mutableStateOf(0) }
    val broadcastPackets = remember { mutableStateListOf<BroadcastPacket>() }
    val devices = remember { mutableStateListOf<NetworkDevice>() }
    val services = remember { mutableStateListOf<NetworkService>() }

    var isListening by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00FF00),
            background = Color(0xFF0D1117),
            surface = Color(0xFF161B22),
            onSurface = Color(0xFFE6EDF3)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "🔍 NetSniffer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF161B22)
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF0D1117))
            ) {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF161B22)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("📡 Broadcasts") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("🖥️ Devices") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("🌐 Services") }
                    )
                }

                // Content
                when (selectedTab) {
                    0 -> BroadcastTab(
                        context = context,
                        packets = broadcastPackets,
                        isListening = isListening,
                        onToggle = { isListening = it }
                    )
                    1 -> DevicesTab(
                        context = context,
                        devices = devices,
                        isScanning = isScanning,
                        onScan = { isScanning = it }
                    )
                    2 -> ServicesTab(
                        context = context,
                        services = services,
                        isDiscovering = isDiscovering,
                        onDiscover = { isDiscovering = it }
                    )
                }
            }
        }
    }
}

@Composable
fun BroadcastTab(
    context: Context,
    packets: MutableList<BroadcastPacket>,
    isListening: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var listener: BroadcastListener? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    if (!isListening) {
                        listener = BroadcastListener { packet ->
                            packets.add(0, packet)
                            if (packets.size > 100) packets.removeAt(packets.lastIndex)
                        }
                        listener?.start()
                        onToggle(true)
                    } else {
                        listener?.stop()
                        onToggle(false)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) Color.Red else Color(0xFF00FF00)
                )
            ) {
                Text(if (isListening) "⏹ Stop" else "▶ Start")
            }

            Button(onClick = { packets.clear() }) {
                Text("🗑️ Clear")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Captured: ${packets.size} packets",
            color = Color(0xFF00FF00),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Packet list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(packets) { packet ->
                PacketCard(packet)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DevicesTab(
    context: Context,
    devices: MutableList<NetworkDevice>,
    isScanning: Boolean,
    onScan: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = {
                if (!isScanning) {
                    onScan(true)
                    devices.clear()
                    NetworkScanner(context).scanNetwork { device ->
                        devices.add(device)
                        onScan(false)
                    }
                }
            },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "🔄 Scanning..." else "🔍 Scan Network")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Found: ${devices.size} devices",
            color = Color(0xFF00FF00),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(devices) { device ->
                DeviceCard(device)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ServicesTab(
    context: Context,
    services: MutableList<NetworkService>,
    isDiscovering: Boolean,
    onDiscover: (Boolean) -> Unit
) {
    var discoveryManager: MDNSDiscovery? by remember { mutableStateOf(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = {
                if (!isDiscovering) {
                    onDiscover(true)
                    services.clear()
                    discoveryManager = MDNSDiscovery(context) { service ->
                        services.add(service)
                    }
                    discoveryManager?.start()
                } else {
                    discoveryManager?.stop()
                    onDiscover(false)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDiscovering) Color.Red else Color(0xFF00FF00)
            )
        ) {
            Text(if (isDiscovering) "⏹ Stop Discovery" else "🌐 Discover Services")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Found: ${services.size} services",
            color = Color(0xFF00FF00),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(services) { service ->
                ServiceCard(service)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PacketCard(packet: BroadcastPacket) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                packet.timestamp,
                fontSize = 10.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "From: ${packet.sourceIP}:${packet.sourcePort}",
                fontSize = 12.sp,
                color = Color(0xFF58A6FF),
                fontWeight = FontWeight.Bold
            )
            Text(
                packet.data.take(100) + if (packet.data.length > 100) "..." else "",
                fontSize = 11.sp,
                color = Color(0xFFE6EDF3),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun DeviceCard(device: NetworkDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    device.ip,
                    fontSize = 14.sp,
                    color = Color(0xFF58A6FF),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    device.hostname,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Text(
                if (device.isReachable) "🟢 Online" else "🔴 Offline",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ServiceCard(service: NetworkService) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                service.name,
                fontSize = 14.sp,
                color = Color(0xFF58A6FF),
                fontWeight = FontWeight.Bold
            )
            Text(
                "${service.host}:${service.port}",
                fontSize = 12.sp,
                color = Color(0xFFE6EDF3)
            )
            Text(
                service.type,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}

// Broadcast Listener
class BroadcastListener(private val onPacket: (BroadcastPacket) -> Unit) {
    private var socket: DatagramSocket? = null
    private var isRunning = false
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun start() {
        isRunning = true
        Thread {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(null) // Sistema asigna puerto
                }

                val buffer = ByteArray(65535)
                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val timestamp = dateFormat.format(Date())
                    val data = String(packet.data, 0, packet.length)

                    onPacket(
                        BroadcastPacket(
                            timestamp = timestamp,
                            sourceIP = packet.address.hostAddress ?: "Unknown",
                            sourcePort = packet.port,
                            data = data
                        )
                    )
                }
            } catch (e: Exception) {
                if (isRunning) e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        socket?.close()
    }
}

// Network Scanner
class NetworkScanner(private val context: Context) {
    fun scanNetwork(onComplete: (NetworkDevice) -> Unit) {
        Thread {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo

            val gateway = intToIp(dhcp.gateway)
            val subnet = gateway.substring(0, gateway.lastIndexOf('.'))

            for (i in 1..254) {
                val host = "$subnet.$i"
                try {
                    val addr = InetAddress.getByName(host)
                    val reachable = addr.isReachable(500)
                    if (reachable) {
                        onComplete(
                            NetworkDevice(
                                ip = host,
                                isReachable = true,
                                hostname = try { addr.hostName } catch (e: Exception) { "Unknown" }
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip unreachable hosts
                }
            }
        }.start()
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}

// mDNS Discovery
class MDNSDiscovery(
    private val context: Context,
    private val onService: (NetworkService) -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        onService(
                            NetworkService(
                                name = serviceInfo.serviceName,
                                host = serviceInfo.host?.hostAddress ?: "Unknown",
                                port = serviceInfo.port,
                                type = serviceInfo.serviceType
                            )
                        )
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stop() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
    }
}