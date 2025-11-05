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
import java.net.MulticastSocket  // ← AGREGAR ESTA LÍNEA
// Data classes
data class BroadcastPacket(
    val timestamp: String,
    val sourceIP: String,
    val sourcePort: Int,
    val data: String,
    val protocol: String = "Unknown",
    val deviceName: String? = null,
    val ipv4Address: String? = null
)

data class NetworkDevice(
    val ip: String,
    val isReachable: Boolean,
    val hostname: String = "Unknown",
    val macAddress: String? = null,
    val vendor: String? = null,
    val deviceType: String = "Unknown",
    val openPorts: List<Int> = emptyList(),
    val lastSeen: Long = System.currentTimeMillis()
)

data class NetworkService(
    val name: String,
    val host: String,
    val port: Int,
    val type: String
)
// Agregar ANTES de class MainActivity
class MulticastManager(private val context: Context) {
    private var multicastLock: WifiManager.MulticastLock? = null

    fun acquire() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("DroidShark_Lock").apply {
            setReferenceCounted(true)
            acquire()
        }
        println("✅ Multicast lock adquirido")
    }

    fun release() {
        multicastLock?.release()
        multicastLock = null
        println("❌ Multicast lock liberado")
    }
}
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
                            "🔍 DroidShark",
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
                        text = { Text("🖥️ Dispositivos") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("🌐 Servicios") }
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
    var selectedPort by remember { mutableStateOf(5353) }

    val commonPorts = listOf(
        5353 to "mDNS",
        1900 to "SSDP",
        137 to "NetBIOS",
        138 to "NetBIOS-DGM"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Selector de puerto
        Text(
            "Puerto:",
            color = Color(0xFF00FF00),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonPorts.forEach { (port, name) ->
                Button(
                    onClick = { selectedPort = port },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPort == port) Color(0xFF00FF00) else Color(0xFF161B22)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            name,
                            fontSize = 9.sp,
                            color = if (selectedPort == port) Color.Black else Color.White
                        )
                        Text(
                            port.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedPort == port) Color.Black else Color(0xFF00FF00)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    if (!isListening) {
                        listener = BroadcastListener(context, selectedPort) { packet ->
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
                Text(if (isListening) "⏹ Parar" else "▶ Iniciar")
            }

            Button(onClick = { packets.clear() }) {
                Text("🗑️ Limpiar")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Escuchando: puerto $selectedPort",
            color = Color(0xFF58A6FF),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Capturados: ${packets.size} packets",
            color = Color(0xFF00FF00),
            fontSize = 14.sp
        )

        // Indicador de estado
        if (isListening && packets.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "⏳ Esperando paquetes...",
                        fontSize = 12.sp,
                        color = Color.Yellow
                    )
                    Text(
                        "Deja escuchando 2-5 minutos",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

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
        // Botón de escaneo
        Button(
            onClick = {
                if (!isScanning) {
                    onScan(true)
                    devices.clear()
                    NetworkScanner(context).scanNetwork(
                        onDeviceFound = { device ->
                            devices.add(device)
                        },
                        onComplete = {
                            onScan(false)
                        }
                    )
                }
            },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FF00),
                disabledContainerColor = Color.Gray
            )
        ) {
            Text(
                if (isScanning) "🔄 Escaneando..." else "🔍 Escanear Red",
                color = if (isScanning) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress bar
        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF00FF00)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Escaneando 254 direcciones IP...",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Estadísticas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoChip("Total", "${devices.size}", Color(0xFF00FF00))
            InfoChip("Online", "${devices.count { it.isReachable }}", Color(0xFF00AAFF))
            InfoChip("Tipos", "${devices.map { it.deviceType }.distinct().size}", Color(0xFFFFAA00))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Agrupar por tipo de dispositivo
        val groupedDevices = devices.groupBy { it.deviceType }.toSortedMap()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groupedDevices.forEach { (type, deviceList) ->
                // Header de grupo
                item {
                    Text(
                        "$type (${deviceList.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF00),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Dispositivos del grupo
                items(deviceList) { device ->
                    EnhancedDeviceCard(device)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun InfoChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 10.sp,
            color = Color.Gray
        )
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
            Text(if (isDiscovering) "⏹ Parar descubrimiento" else "🌐 Descubrir servicios")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Encontrados: ${services.size} servicios",
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
            // Header con timestamp y protocolo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    packet.timestamp,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )

                // Badge de protocolo
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = getProtocolColor(packet.protocol).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        getProtocolColor(packet.protocol)
                    )
                ) {
                    Text(
                        packet.protocol,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 9.sp,
                        color = getProtocolColor(packet.protocol),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nombre del dispositivo (si existe)
            if (packet.deviceName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📱 ", fontSize = 14.sp)
                    Text(
                        packet.deviceName,
                        fontSize = 13.sp,
                        color = Color(0xFF00FF00),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // IPv4 (si existe)
            if (packet.ipv4Address != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌐 ", fontSize = 14.sp)
                    Text(
                        "IPv4: ${packet.ipv4Address}",
                        fontSize = 12.sp,
                        color = Color(0xFF58A6FF)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // IP origen
            Text(
                "De: ${packet.sourceIP}:${packet.sourcePort}",
                fontSize = 11.sp,
                color = Color(0xFF8B949E),
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Datos (truncados y limpios)
            Text(
                packet.data,
                fontSize = 10.sp,
                color = Color(0xFF6E7681),
                fontFamily = FontFamily.Monospace,
                maxLines = 3
            )
        }
    }
}

fun getProtocolColor(protocol: String): Color {
    return when (protocol) {
        "mDNS" -> Color(0xFF00FF00)
        "Chromecast" -> Color(0xFF00AAFF)
        "AirPlay" -> Color(0xFFFF00FF)
        "Printer" -> Color(0xFFFFAA00)
        "HTTP Service" -> Color(0xFF00FFFF)
        else -> Color.Gray
    }
}
@Composable
fun EnhancedDeviceCard(device: NetworkDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Tipo + Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.deviceType,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF00)
                    )
                }

                // Estado online/offline
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (device.isReachable)
                        Color(0xFF00FF00).copy(alpha = 0.2f)
                    else
                        Color.Red.copy(alpha = 0.2f)
                ) {
                    Text(
                        if (device.isReachable) "● Online" else "● Offline",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        color = if (device.isReachable) Color(0xFF00FF00) else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // IP Address
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌐 ", fontSize = 14.sp)
                Text(
                    device.ip,
                    fontSize = 14.sp,
                    color = Color(0xFF58A6FF),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Hostname
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📝 ", fontSize = 14.sp)
                Text(
                    device.hostname,
                    fontSize = 13.sp,
                    color = Color(0xFFE6EDF3)
                )
            }

            // Puertos abiertos (si hay)
            if (device.openPorts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔓 ", fontSize = 14.sp)
                    Text(
                        "Puertos: ",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    // Badges de puertos
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        device.openPorts.take(5).forEach { port ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFAA00).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color(0xFFFFAA00)
                                )
                            ) {
                                Text(
                                    getPortName(port),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    color = Color(0xFFFFAA00),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getPortName(port: Int): String {
    return when (port) {
        80 -> "HTTP"
        443 -> "HTTPS"
        22 -> "SSH"
        23 -> "Telnet"
        21 -> "FTP"
        3389 -> "RDP"
        8080 -> "HTTP-Alt"
        5000 -> "UPnP"
        631 -> "IPP"
        else -> "$port"
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

class BroadcastListener(
    private val context: Context,
    private val port: Int = 5353,
    private val onPacket: (BroadcastPacket) -> Unit
) {
    private var socket: MulticastSocket? = null
    private var isRunning = false
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val multicastManager = MulticastManager(context)

    fun start() {
        isRunning = true
        multicastManager.acquire()

        Thread {
            try {
                socket = MulticastSocket(port).apply {
                    reuseAddress = true

                    when (port) {
                        5353 -> {
                            joinGroup(InetAddress.getByName("224.0.0.251"))
                            println("✅ Unido a grupo mDNS (224.0.0.251:$port)")
                        }
                        1900 -> {
                            joinGroup(InetAddress.getByName("239.255.255.250"))
                            println("✅ Unido a grupo SSDP (239.255.255.250:$port)")
                        }
                    }
                }

                println("🎯 Escuchando puerto $port")

                val buffer = ByteArray(65535)
                var packetCount = 0

                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    packetCount++
                    val sourceIP = packet.address.hostAddress ?: "Unknown"
                    println("📦 Paquete #$packetCount de $sourceIP:${packet.port}")

                    val timestamp = dateFormat.format(Date())
                    val rawData = packet.data.copyOfRange(0, packet.length)

                    // Parsear información útil
                    val parsedInfo = parsePacketInfo(rawData, sourceIP)

                    onPacket(
                        BroadcastPacket(
                            timestamp = timestamp,
                            sourceIP = cleanIPAddress(sourceIP),
                            sourcePort = packet.port,
                            data = formatRawData(rawData),
                            protocol = parsedInfo.protocol,
                            deviceName = parsedInfo.deviceName,
                            ipv4Address = parsedInfo.ipv4Address
                        )
                    )
                }
            } catch (e: Exception) {
                if (isRunning) {
                    println("❌ Error: ${e.message}")
                    e.printStackTrace()
                }
            } finally {
                try {
                    when (port) {
                        5353 -> socket?.leaveGroup(InetAddress.getByName("224.0.0.251"))
                        1900 -> socket?.leaveGroup(InetAddress.getByName("239.255.255.250"))
                    }
                } catch (e: Exception) {}
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        socket?.close()
        multicastManager.release()
    }

    private fun cleanIPAddress(ip: String): String {
        // Limpiar IPv6 link-local
        return when {
            ip.startsWith("fe80:") || ip.startsWith("fa80:") -> {
                // Extraer parte significativa de IPv6
                ip.split("%").firstOrNull()?.take(20) ?: ip
            }
            else -> ip
        }
    }

    private fun parsePacketInfo(data: ByteArray, sourceIP: String): PacketInfo {
        val dataString = String(data, Charsets.ISO_8859_1)

        var protocol = "mDNS"
        var deviceName: String? = null
        var ipv4Address: String? = null

        // Extraer nombre de dispositivo
        """([A-Za-z0-9\-]+)-([A-Za-z0-9]+)\.local""".toRegex().find(dataString)?.let {
            deviceName = it.groupValues[1]
        }

        // Buscar Android en el nombre
        if (dataString.contains("Android", ignoreCase = true)) {
            """Android-([A-Za-z0-9]+)""".toRegex().find(dataString)?.let {
                deviceName = "Android-${it.groupValues[1].take(6)}"
            }
        }

        // Extraer direcciones IPv4 (buscar patrones xxx.xxx.xxx.xxx)
        """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""".toRegex().find(dataString)?.let {
            val foundIP = it.groupValues[1]
            // Verificar que sea IP válida
            val parts = foundIP.split(".")
            if (parts.all { p -> p.toIntOrNull() in 0..255 }) {
                ipv4Address = foundIP
            }
        }

        // Detectar tipo de servicio
        when {
            dataString.contains("_googlecast") -> {
                protocol = "Chromecast"
                deviceName = deviceName ?: "Chromecast"
            }
            dataString.contains("_airplay") -> {
                protocol = "AirPlay"
                deviceName = deviceName ?: "Apple Device"
            }
            dataString.contains("_printer") -> {
                protocol = "Printer"
                deviceName = deviceName ?: "Network Printer"
            }
            dataString.contains("_http._tcp") -> {
                protocol = "HTTP Service"
            }
        }

        return PacketInfo(protocol, deviceName, ipv4Address)
    }

    private fun formatRawData(data: ByteArray): String {
        // Mostrar solo caracteres imprimibles
        val printable = data.filter { it in 32..126 || it == 10.toByte() || it == 13.toByte() }
            .map { it.toInt().toChar() }
            .joinToString("")
            .trim()

        return if (printable.length > 10) {
            printable.take(150)
        } else {
            // Si no hay caracteres imprimibles, mostrar hex
            data.take(50).joinToString(" ") { "%02X".format(it) }
        }
    }

    data class PacketInfo(
        val protocol: String,
        val deviceName: String?,
        val ipv4Address: String?
    )
}

// Network Scanner
class NetworkScanner(private val context: Context) {

    fun scanNetwork(onDeviceFound: (NetworkDevice) -> Unit, onComplete: () -> Unit) {
        Thread {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val dhcp = wifiManager.dhcpInfo

                val gateway = intToIp(dhcp.gateway)
                val subnet = gateway.substring(0, gateway.lastIndexOf('.'))

                println("🔍 Iniciando escaneo de red: $subnet.0/24")

                // Escaneo paralelo más rápido
                val executor = java.util.concurrent.Executors.newFixedThreadPool(50)
                val futures = mutableListOf<java.util.concurrent.Future<*>>()

                for (i in 1..254) {
                    val future = executor.submit {
                        val host = "$subnet.$i"
                        scanHost(host, onDeviceFound)
                    }
                    futures.add(future)
                }

                // Esperar a que terminen todos
                futures.forEach { it.get() }
                executor.shutdown()

                println("✅ Escaneo completado")
                onComplete()

            } catch (e: Exception) {
                println("❌ Error en escaneo: ${e.message}")
                e.printStackTrace()
                onComplete()
            }
        }.start()
    }

    private fun scanHost(host: String, onDeviceFound: (NetworkDevice) -> Unit) {
        try {
            val addr = InetAddress.getByName(host)

            if (addr.isReachable(300)) {
                println("✅ Dispositivo en $host")

                // MÉTODO 1: Hostname oficial
                var hostname = try {
                    val canonical = addr.canonicalHostName
                    if (canonical != host && !canonical.contains(host)) {
                        canonical
                    } else null
                } catch (e: Exception) {
                    null
                }

                // MÉTODO 2: Hostname DNS inverso
                if (hostname == null) {
                    hostname = try {
                        addr.hostName.let {
                            if (it != host) it else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                // MÉTODO 3: Detectar por fabricante MAC (si es tu dispositivo)
                if (hostname == null && isLocalDevice(host, context)) {
                    hostname = "Este dispositivo (${android.os.Build.MODEL})"
                }

                // MÉTODO 4: Detectar por puertos abiertos
                val openPorts = scanCommonPorts(host)
                if (hostname == null && openPorts.isNotEmpty()) {
                    hostname = identifyByPorts(openPorts)
                }

                // MÉTODO 5: Nombre genérico descriptivo
                if (hostname == null) {
                    hostname = "Dispositivo-${host.split(".").last()}"
                }

                // Identificar tipo de dispositivo
                val deviceType = identifyDeviceType(hostname, host, openPorts)

                onDeviceFound(
                    NetworkDevice(
                        ip = host,
                        isReachable = true,
                        hostname = hostname,
                        deviceType = deviceType,
                        openPorts = openPorts
                    )
                )
            }
        } catch (e: Exception) {
            // Host no alcanzable
        }
    }

    // Agregar estas funciones nuevas
    private fun isLocalDevice(ip: String, context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val localIP = intToIp(wifiInfo.ipAddress)
            ip == localIP
        } catch (e: Exception) {
            false
        }
    }

    private fun identifyByPorts(ports: List<Int>): String {
        return when {
            631 in ports -> "Impresora de Red"
            8080 in ports || 80 in ports -> "Servidor Web"
            22 in ports -> "Servidor SSH"
            3389 in ports -> "Windows PC (RDP)"
            5000 in ports -> "Dispositivo UPnP"
            else -> null
        } ?: "Dispositivo-Red"
    }

    private fun identifyDeviceType(hostname: String, ip: String, ports: List<Int>): String {
        val lower = hostname.lowercase()

        // Identificar por hostname
        val byHostname = when {
            lower.contains("router") || lower.contains("gateway") -> "🌐 Router"
            ip.endsWith(".1") -> "🌐 Gateway"
            lower.contains("android") -> "📱 Android"
            lower.contains("iphone") || lower.contains("ipad") -> "📱 iOS"
            lower.contains("samsung") -> "📱 Samsung"
            lower.contains("xiaomi") -> "📱 Xiaomi"
            lower.contains("huawei") -> "📱 Huawei"
            lower.contains("oppo") || lower.contains("vivo") -> "📱 Smartphone"
            lower.contains("pc") || lower.contains("desktop") -> "💻 PC"
            lower.contains("laptop") -> "💻 Laptop"
            lower.contains("macbook") || lower.contains("imac") -> "💻 Mac"
            lower.contains("chromecast") -> "📺 Chromecast"
            lower.contains("roku") -> "📺 Roku"
            lower.contains("firetv") -> "📺 Fire TV"
            lower.contains("smarttv") || lower.contains("smart-tv") -> "📺 Smart TV"
            lower.contains("printer") || lower.contains("impresora") -> "🖨️ Impresora"
            lower.contains("hp") || lower.contains("canon") || lower.contains("epson") -> "🖨️ Impresora"
            lower.contains("esp") -> "🔌 ESP"
            lower.contains("arduino") -> "🔌 Arduino"
            lower.contains("raspberry") || lower.contains("raspi") -> "🔌 Raspberry Pi"
            lower.contains("este dispositivo") -> "📱 Tu Dispositivo"
            else -> null
        }

        if (byHostname != null) return byHostname

        // Identificar por puertos abiertos
        val byPorts = when {
            631 in ports -> "🖨️ Impresora"
            3389 in ports -> "💻 PC Windows"
            22 in ports && 80 in ports -> "🖥️ Servidor Linux"
            8080 in ports || 80 in ports -> "🌐 Servidor Web"
            5000 in ports -> "🔌 Dispositivo IoT"
            else -> null
        }

        return byPorts ?: "❓ Dispositivo de Red"
    }

    private fun getHostnameAlternative(ip: String): String {
        // Intentar obtener hostname de forma alternativa
        return try {
            val process = Runtime.getRuntime().exec("getprop net.hostname")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            reader.readLine() ?: "Unknown-$ip"
        } catch (e: Exception) {
            "Device-${ip.split(".").last()}"
        }
    }

    private fun identifyDeviceType(hostname: String, ip: String): String {
        val lower = hostname.lowercase()

        return when {
            // Routers y Gateways
            lower.contains("router") || lower.contains("gateway") -> "🌐 Router"
            ip.endsWith(".1") -> "🌐 Gateway"

            // Dispositivos móviles
            lower.contains("android") -> "📱 Android"
            lower.contains("iphone") || lower.contains("ipad") -> "📱 iOS"
            lower.contains("samsung") -> "📱 Samsung"
            lower.contains("xiaomi") -> "📱 Xiaomi"
            lower.contains("huawei") -> "📱 Huawei"

            // Computadoras
            lower.contains("pc") || lower.contains("desktop") -> "💻 PC"
            lower.contains("laptop") -> "💻 Laptop"
            lower.contains("macbook") || lower.contains("imac") -> "💻 Mac"

            // Smart devices
            lower.contains("chromecast") -> "📺 Chromecast"
            lower.contains("roku") -> "📺 Roku"
            lower.contains("fire") && lower.contains("tv") -> "📺 Fire TV"
            lower.contains("smart") && lower.contains("tv") -> "📺 Smart TV"

            // Impresoras
            lower.contains("printer") || lower.contains("hp") ||
                    lower.contains("canon") || lower.contains("epson") -> "🖨️ Impresora"

            // IoT
            lower.contains("esp") -> "🔌 IoT Device"
            lower.contains("arduino") -> "🔌 Arduino"
            lower.contains("raspberry") -> "🔌 Raspberry Pi"

            // Otros
            else -> "❓ Desconocido"
        }
    }

    private fun scanCommonPorts(host: String): List<Int> {
        val commonPorts = listOf(
            80,   // HTTP
            443,  // HTTPS
            22,   // SSH
            23,   // Telnet
            21,   // FTP
            3389, // RDP
            8080, // HTTP Alt
            5000, // UPnP
            631   // IPP (Impresoras)
        )

        val openPorts = mutableListOf<Int>()

        // Escanear solo los puertos más comunes (rápido)
        for (port in commonPorts.take(5)) { // Limitar a 5 para no tardar mucho
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 100)
                openPorts.add(port)
                socket.close()
            } catch (e: Exception) {
                // Puerto cerrado
            }
        }

        return openPorts
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
                                host = serviceInfo.host?.hostAddress ?: "Desconocido",
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