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
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
    // ログ
    val logs = remember { mutableStateListOf<String>() }
    // ログ更新 BroadcastReceiver
    val updateLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text")
            Log.d("PeripheralScreen", "onReceive: $text")
            text?.let {
                logs.add(it)
                Log.d("PeripheralScreen", "Received item: $it")
            }
        }
    }
    // ログ更新 BroadcastReceiver登録
    DisposableEffect(context) {
        val intentFilter = IntentFilter("com.example.sandbox.UPDATE_LOG")
        context.registerReceiver(updateLogReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        Log.d("PeripheralScreen", "registered")
        onDispose {
            Log.d("PeripheralScreen", "disposed")
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
            Button(
                onClick = {
                    val intent = Intent(context, PeripheralService::class.java)
                    context.startService(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "Advertise")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    val intent = Intent(context, PeripheralService::class.java)
                    context.stopService(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Disconnect")
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

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        // GATTサーバー起動
        startGattServer()
        // アドバタイズ開始
        startAdvertising()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        // アドバタイズ終了
        stopAdvertising()
        // GATTサーバー終了
        closeGattServer()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    // GATTサーバーを起動する
    @SuppressLint("MissingPermission")
    private fun startGattServer() {
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
    private fun closeGattServer() {
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

    // アドバタイズを開始する
    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        // BluetoothAdapterを取得する
        bluetoothAdapter = bluetoothManager?.adapter
        // BluetoothLeAdvertiserを設定する
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .build()
        // アドバタイズを開始する
        advertiser?.startAdvertising(settings, advertiseData, advertiserCallback)
        log("Start advertising...")
    }

    // アドバタイズを終了する
    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
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

    // ログ出力
    private fun log(text: String) {
        val intent = Intent("com.example.sandbox.UPDATE_LOG")
        intent.putExtra("text", text)
        sendBroadcast(intent)
    }
}
