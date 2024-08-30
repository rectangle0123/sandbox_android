package com.example.sandbox

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sandbox.ui.theme.SandboxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun PeripheralScreen() {
    val context = LocalContext.current
    val viewModel: PeripheralViewModel = viewModel()

    DisposableEffect(Unit) {
        viewModel.bindService(context)
        onDispose {
            viewModel.unbindService(context)
        }
    }

    DisposableEffect(context) {
        val updateLogReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra("text")
                val subText = intent?.getStringExtra("subText")
                val error = intent?.getBooleanExtra("error", false) ?: false
                val enhanced = intent?.getBooleanExtra("enhanced", false) ?: false
                text?.let {
                    viewModel.addLog(text, subText, error, enhanced)
                }
            }
        }
        val intentFilter = IntentFilter("com.example.sandbox.UPDATE_LOG")
        context.registerReceiver(updateLogReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        onDispose {
            context.unregisterReceiver(updateLogReceiver)
        }
    }

    // Composable
    val isAdvertising by viewModel.isAdvertising
    val isGattServerRunning by viewModel.isGattServerRunning

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn {
                items(viewModel.logs) { log ->
                    Column() {
                        Text(
                            text = log.text,
                            color = if (log.error) Color.Red else if (log.enhanced) Color.Blue else Color.Gray,
                            modifier = Modifier.padding(top = 16.dp, bottom = if (log.subText != null) 0.dp else 16.dp, start = 32.dp, end = 32.dp)
                        )
                        log.subText?.let {
                            Text(
                                text = it,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 16.dp, start = 32.dp, end = 32.dp),
                                style = TextStyle( fontSize = 14.sp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            CustomButton(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = "Start Advertising",
                enabled = !isAdvertising,
                onClick = viewModel::startAdvertising,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            CustomButton(
                imageVector = Icons.Default.PortableWifiOff,
                contentDescription = "Stop Advertising",
                enabled = isAdvertising,
                onClick = viewModel::stopAdvertising,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            CustomButton(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Start Server",
                enabled = !isGattServerRunning,
                onClick = viewModel::startGattServer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            CustomButton(
                imageVector = Icons.Default.StopCircle,
                contentDescription = "Stop Server",
                enabled = isGattServerRunning,
                onClick = viewModel::closeGattServer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CustomButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(64.dp)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true)
@Composable
fun PeripheralScreenPreview() {
    SandboxTheme {
        PeripheralScreen()
    }
}

class PeripheralViewModel() : ViewModel() {
    private var service: WeakReference<PeripheralService>? = null

    private val _isAdvertising = mutableStateOf(false)
    val isAdvertising: State<Boolean> = _isAdvertising

    private val _isGattServerRunning = mutableStateOf(false)
    val isGattServerRunning: State<Boolean> = _isGattServerRunning

    private val _logs = mutableStateListOf<Log>()
    val logs: List<Log> = _logs

    private val serviceConnection = PeripheralServiceConnection()

    init {
        observeServiceState()
    }

    fun bindService(context: Context) {
        val intent = Intent(context, PeripheralService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    fun unbindService(context: Context) {
        context.unbindService(serviceConnection)
    }
    fun startAdvertising() {
        service?.get()?.startAdvertising()
    }
    fun stopAdvertising() {
        service?.get()?.stopAdvertising()
    }
    fun startGattServer() {
        service?.get()?.startGattServer()
    }
    fun closeGattServer() {
        service?.get()?.closeGattServer()
    }
    fun addLog(text: String, subText: String?, error: Boolean, enhanced: Boolean) {
        _logs.add(Log(text, subText, error, enhanced))
    }

    private fun observeServiceState() {
        service?.get()?.let { service ->
            viewModelScope.launch {
                service.isAdvertising.collect { isAdvertising ->
                    _isAdvertising.value = isAdvertising
                }
            }
            viewModelScope.launch {
                service.isGattServerRunning.collect { isGattServerRunning ->
                    _isGattServerRunning.value = isGattServerRunning
                }
            }
        }
    }

    private inner class PeripheralServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            service = WeakReference((binder as PeripheralService.LocalBinder).getService())
            observeServiceState()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            service = null
            _isAdvertising.value = false
            _isGattServerRunning.value = false
        }
    }
}

// ペリフェラルサービス
class PeripheralService : Service() {
    companion object {
        private const val SERVICE_UUID = BuildConfig.BT_SERVICE_UUID
        private const val CHARACTERISTIC_UUID = BuildConfig.BT_CHARACTERISTIC_UUID
    }

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising

    private val _isGattServerRunning = MutableStateFlow(false)
    val isGattServerRunning: StateFlow<Boolean> = _isGattServerRunning

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer : BluetoothGattServer? = null
    private var advertiser : BluetoothLeAdvertiser? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PeripheralService = this@PeripheralService
    }

    override fun onBind(intent: Intent): IBinder = binder

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        initialize()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        closeGattServer()
    }

    // 初期化
    private fun initialize() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }

    // アドバタイズを開始する
    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
            .build()
        advertiser?.startAdvertising(settings, advertiseData, advertiserCallback)
        _isAdvertising.value = true
        log("Start advertising.", subText = SERVICE_UUID, enhanced = true)
    }

    // アドバタイズを終了する
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiserCallback)
        _isAdvertising.value = false
        log("Stop advertising.", subText = SERVICE_UUID, enhanced = true)
    }

    // GATTサーバーを起動する
    @SuppressLint("MissingPermission")
    fun startGattServer() {
        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)
        gattServer?.apply {
            val service = BluetoothGattService(
                UUID.fromString(SERVICE_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            val characteristic = BluetoothGattCharacteristic(
                UUID.fromString(CHARACTERISTIC_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(characteristic)
            addService(service)
            _isGattServerRunning.value = true
            log("Start Service.", subText = SERVICE_UUID, enhanced = true)
        }
    }

    // GATTサーバーを終了する
    @SuppressLint("MissingPermission")
    fun closeGattServer() {
        gattServer?.close()
        _isGattServerRunning.value = false
        log("Stop Service.", subText = SERVICE_UUID, enhanced = true)
    }

    // アドバタイザーコールバック
    private val advertiserCallback = PeripheralAdvertiseCallback()
    private inner class PeripheralAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            // Do nothing
        }
        override fun onStartFailure(errorCode: Int) {
            log("Advertising failed: $errorCode", error = true)
        }
    }

    // GATTサーバーコールバック
    private val gattServerCallback = PeripheralGattServerCallback()
    private inner class PeripheralGattServerCallback : BluetoothGattServerCallback() {
        // 接続・切断のコールバック
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            val deviceName = device?.name ?: "Unknown"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> log("Connected.", subText = deviceName)
                BluetoothProfile.STATE_DISCONNECTED -> log("Disconnected." , subText = deviceName)
            }
        }
        // Readリクエスト受信コールバック
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            log("Received read request.", subText = characteristic?.uuid.toString().uppercase())
            if (characteristic?.uuid == UUID.fromString(CHARACTERISTIC_UUID)) {
                // Readレスポンスを送信する
                val message = "Hello, World!"
                val data = message.toByteArray(Charsets.UTF_8)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
                log("Sent read response.", subText = message)
            }
        }
    }

    // ログ出力
    private fun log(text: String, subText: String? = null, error: Boolean = false, enhanced: Boolean = false) {
        val intent = Intent("com.example.sandbox.UPDATE_LOG").apply {
            putExtra("text", text)
            putExtra("subText", subText)
            putExtra("error", error)
            putExtra("enhanced", enhanced)
        }
        sendBroadcast(intent)
    }
}

// ログ
data class Log (
    val text: String,
    var subText: String?,
    val error: Boolean,
    val enhanced: Boolean
)
