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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sandbox.ui.theme.SandboxTheme
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun PeripheralScreen() {
    val context = LocalContext.current

    // サービス
    val connection = remember {
        object : ServiceConnection {
            var service: PeripheralService? = null
            override fun onServiceConnected(className: ComponentName, binder: IBinder) {
                service = (binder as PeripheralService.LocalBinder).getService()
            }
            override fun onServiceDisconnected(arg0: ComponentName) {
                service = null
            }
        }
    }
    DisposableEffect(Unit) {
        val intent = Intent(context, PeripheralService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(connection)
        }
    }

    // ログ
    val logs = remember { mutableStateListOf<String>() }
    val updateLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text")
            text?.let {
                logs.add(it)
            }
        }
    }
    DisposableEffect(context) {
        val intentFilter = IntentFilter("com.example.sandbox.UPDATE_LOG")
        context.registerReceiver(updateLogReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        onDispose {
            context.unregisterReceiver(updateLogReceiver)
        }
    }

    // Composable
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn {
                items(logs) { item ->
                    Text(
                        text = item,
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
                onClick = {
                    connection.service?.startAdvertising()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Wifi, contentDescription = "Start Advertising")
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    connection.service?.stopAdvertising()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.WifiOff, contentDescription = "Stop Advertising")
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    connection.service?.startGattServer()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Power, contentDescription = "Start Server")
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    connection.service?.closeGattServer()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.PowerOff, contentDescription = "Stop Server")
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

// ペリフェラルサービス
class PeripheralService : Service() {
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer : BluetoothGattServer? = null
    private var advertiser : BluetoothLeAdvertiser? = null

    companion object {
        private const val SERVICE_UUID = BuildConfig.BT_SERVICE_UUID
        private const val CHARACTERISTIC_UUID = BuildConfig.BT_CHARACTERISTIC_UUID
    }

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
        log("Start advertising...")
    }

    // アドバタイズを終了する
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiserCallback)
        log("Stop advertising.")
    }

    // アドバタイザーコールバック
    private val advertiserCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            // Do nothing
        }
        override fun onStartFailure(errorCode: Int) {
            log("Advertising failed: $errorCode")
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
            log("Started GATT Server: $SERVICE_UUID")
        }
    }

    // GATTサーバーを終了する
    @SuppressLint("MissingPermission")
    fun closeGattServer() {
        gattServer?.close()
        log("Closed GATT Server: $SERVICE_UUID")
    }

    // GATTサーバーコールバック
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        // 接続・切断のコールバック
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            when (newState) {
                // セントラルに接続した場合、アドバタイズを終了する
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected: ${device?.name?: "Unknown"}.")
                    stopAdvertising()
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
    private fun log(text: String) {
        val intent = Intent("com.example.sandbox.UPDATE_LOG")
        intent.putExtra("text", text)
        sendBroadcast(intent)
    }
}
