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
import androidx.compose.material.icons.filled.BroadcastOnHome
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun PeripheralScreen(viewModel: PeripheralViewModel = viewModel()) {
    val context = LocalContext.current
    val logs = viewModel.logs

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
                val error = intent?.getBooleanExtra("error", false) ?: false
                val enhanced = intent?.getBooleanExtra("enhanced", false) ?: false
                text?.let {
                    viewModel.addLog(text, error, enhanced)
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
                items(logs) { log ->
                    Text(
                        text = log.text,
                        color = if (log.error) Color.Red else if (log.enhanced) Color.Blue else Color.Gray,
                        modifier = Modifier
                            .padding(vertical = 16.dp, horizontal = 32.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = viewModel::startAdvertising,
                enabled = !isAdvertising,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = "Start Advertising",
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = viewModel::stopAdvertising,
                enabled = isAdvertising,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PortableWifiOff,
                    contentDescription = "Stop Advertising",
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = viewModel::startGattServer,
                enabled = !isGattServerRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Start Server",
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = viewModel::closeGattServer,
                enabled = isGattServerRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = "Stop Server",
                    modifier = Modifier.size(64.dp)
                )
            }
        }
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

class PeripheralViewModel : ViewModel() {
    @SuppressLint("StaticFieldLeak")
    private var service: PeripheralService? = null

    private val _isAdvertising = mutableStateOf(false)
    val isAdvertising: State<Boolean> = _isAdvertising
    private val _isGattServerRunning = mutableStateOf(false)
    val isGattServerRunning: State<Boolean> = _isGattServerRunning

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            service = (binder as PeripheralService.LocalBinder).getService()
            // サービスの状態を監視する
            service?.isAdvertising?.let { flow ->
                viewModelScope.launch {
                    flow.collect { isAdvertising ->
                        _isAdvertising.value = isAdvertising
                    }
                }
            }
            service?.isGattServerRunning?.let { flow ->
                viewModelScope.launch {
                    flow.collect { isGattServerRunning ->
                        _isGattServerRunning.value = isGattServerRunning
                    }
                }
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            service = null
        }
    }

    private val _logs = mutableStateListOf<Log>()
    val logs: List<Log> = _logs

    fun bindService(context: Context) {
        val intent = Intent(context, PeripheralService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    fun unbindService(context: Context) {
        context.unbindService(serviceConnection)
    }
    fun startAdvertising() {
        service?.startAdvertising()
    }
    fun stopAdvertising() {
        service?.stopAdvertising()
    }
    fun startGattServer() {
        service?.startGattServer()
    }
    fun closeGattServer() {
        service?.closeGattServer()
    }
    fun addLog(text: String, error: Boolean, enhanced: Boolean) {
        _logs.add(Log(text, error, enhanced))
    }
}

// ログ
data class Log (
    val text: String,
    val error: Boolean,
    val enhanced: Boolean
)

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

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        closeGattServer()
    }

    // アドバタイズを開始する
    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .build()
        advertiser?.startAdvertising(settings, advertiseData, advertiserCallback)
        _isAdvertising.value = true
        log("Start advertising...", enhanced = true)
    }

    // アドバタイズを終了する
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiserCallback)
        _isAdvertising.value = false
        log("Stop advertising.", enhanced = true)
    }

    // アドバタイザーコールバック
    private val advertiserCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            // Do nothing
        }
        override fun onStartFailure(errorCode: Int) {
            log("Advertising failed: $errorCode", error = true)
        }
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
            log("Started GATT Server: $SERVICE_UUID", enhanced = true)
        }
    }

    // GATTサーバーを終了する
    @SuppressLint("MissingPermission")
    fun closeGattServer() {
        gattServer?.close()
        _isGattServerRunning.value = false
        log("Closed GATT Server: $SERVICE_UUID", enhanced = true)
    }

    // GATTサーバーコールバック
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        // 接続・切断のコールバック
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            when (newState) {
                // セントラルに接続した場合
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected: ${device?.name?: "Unknown"}.")
//                    stopAdvertising()
                }
                // セントラルから切断した場合
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("Disconnected: ${device?.name?: "Unknown"}")
                }
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
            log("Received read request: ${characteristic?.uuid.toString()}")
            if (characteristic?.uuid == UUID.fromString(CHARACTERISTIC_UUID)) {
                // Readレスポンスを送信する
                val data = "Hello, World!".toByteArray(Charsets.UTF_8)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
                log("Sent read response.")
            }
        }
    }

    // ログ出力
    private fun log(text: String, error: Boolean = false, enhanced: Boolean = false) {
        val intent = Intent("com.example.sandbox.UPDATE_LOG")
        intent.putExtra("text", text)
        intent.putExtra("error", error)
        intent.putExtra("enhanced", enhanced)
        sendBroadcast(intent)
    }
}
