package org.hyperledger.ariesproject.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*

class BluetoothServer(private val context: Context) {

    private val serviceUUID = UUID.fromString("d14a2b10-9f12-4b2a-b0c1-7b6b2c0a9d99")
    private val characteristicUUID = UUID.fromString("d14a2b11-9f12-4b2a-b0c1-7b6b2c0a9d99")

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var transferCharacteristic: BluetoothGattCharacteristic? = null

    private var isAdvertising = false
    private var isConnected = false
    private var connectedDevice: BluetoothDevice? = null

    private val receivedBuffer = mutableListOf<Byte>()
    var onLog: ((String) -> Unit)? = null
    var onJSONReceived: ((String) -> Unit)? = null
    var onDeviceConnected: ((String) -> Unit)? = null
    val TAG = "SimpleBleClient"
    // ─────────────────────────────────────────────────────────────────────────────

    private fun checkPermission(){
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Permissão BLUETOOTH_SCAN não concedida")

        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Permissão BLUETOOTH_SCAN não concedida")

        }
    }

    fun startServer() {
        onLog?.invoke("🚀 startServer() — preparando advertising e GATT…")
        onLog?.invoke("📱 BLE peripheral suportado? ${bluetoothAdapter.isMultipleAdvertisementSupported}")
        onLog?.invoke("💡 Advertiser disponível? ${bluetoothAdapter.bluetoothLeAdvertiser != null}")
        onLog?.invoke("⚙️ Versão Android: ${android.os.Build.VERSION.SDK_INT}")
        onLog?.invoke("🏷️ Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

        val needAdvertise = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
        val needConnect   = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)   != PackageManager.PERMISSION_GRANTED
        if (needAdvertise || needConnect) {
            onLog?.invoke("⚠️ Permissões de Bluetooth não concedidas (ADVERTISE/CONNECT).")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            onLog?.invoke("⚠️ Bluetooth OFF — ative antes.")
            return
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            onLog?.invoke("❌ Dispositivo não suporta BLE.")
            return
        }
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            onLog?.invoke("❌ Múltiplos advertisers/Peripheral mode não suportados.")
            return
        }

        Handler(Looper.getMainLooper()).post { startAdvertising() }

        Handler(Looper.getMainLooper()).post {
            openGattServerAndAddService()
        }
    }

    fun stopServer() {
        checkPermission();
        stopAdvertising()
        try { gattServer?.close() } catch (_: Throwable) {}
        gattServer = null
        onLog?.invoke("🛑 Servidor encerrado.")
    }

    private fun startAdvertising() {
        if (isAdvertising) {
            onLog?.invoke("ℹ️ Advertising já ativo.")
            return
        }

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onLog?.invoke("❌ advertiser == null (chip/firmware não suporta Peripheral).")
            return
        }

        // nome curto para não estourar 31 bytes
        checkPermission();
        bluetoothAdapter.name = "IDD"

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUUID))
            .setIncludeDeviceName(false)
            .build()

        // Scan response: nome
        val scanResp = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        onLog?.invoke("📡 Iniciando advertising (UUID + nome no scanResponse)…")
        checkPermission();
        advertiser.startAdvertising(settings, advData, scanResp, advertiseCallback)
    }

    private fun stopAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        if (!isAdvertising) return
        checkPermission();
        advertiser.stopAdvertising(advertiseCallback)
        isAdvertising = false
        onLog?.invoke("🛑 Advertising parado.")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            onLog?.invoke("✅ Advertising ON (mode=${settingsInEffect.mode}).")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE (>31B)"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                else -> "UNKNOWN"
            }
            onLog?.invoke("❌ Advertising falhou: $errorCode ($reason)")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GATT server

    private fun openGattServerAndAddService() {
        onLog?.invoke("🧱 Abrindo GATT server e adicionando serviço…")
        checkPermission();
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        transferCharacteristic = BluetoothGattCharacteristic(
            characteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or
                    BluetoothGattCharacteristic.PERMISSION_READ
        )

        val cccd = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        transferCharacteristic?.addDescriptor(cccd)

        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(transferCharacteristic)

        val added = gattServer?.addService(service) ?: false
        onLog?.invoke("📦 addService retornou: $added")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("🧩 Serviço adicionado (OK).")
            } else {
                onLog?.invoke("❌ Falha ao adicionar serviço: status=$status")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            checkPermission();
            onLog?.invoke("📶 Conexão: status=$status, newState=$newState (${device.name})")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                isConnected = true
                onDeviceConnected?.invoke(device.name ?: "Sem nome")
                Handler(Looper.getMainLooper()).post { stopAdvertising() }
            } else {
                connectedDevice = null
                isConnected = false
                Handler(Looper.getMainLooper()).post { startAdvertising() } // disponível para nvoos clientes
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            onLog?.invoke("✏️ Descriptor write (len=${value.size})")
            checkPermission();
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val chunk = String(value)
            if (chunk == "<EOF>") {
                val full = receivedBuffer.toByteArray()
                receivedBuffer.clear()
                val text = String(full)
                onLog?.invoke("📥 JSON completo recebido (${full.size} bytes)")
                onJSONReceived?.invoke(text)

                Handler(Looper.getMainLooper()).post {
                    disconnectClient()
                }

            } else {
                receivedBuffer.addAll(value.toList())
                onLog?.invoke("⬇️ Chunk ${value.size} bytes (total=${receivedBuffer.size})")
            }
            if (responseNeeded) {
                checkPermission();
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    fun disconnectClient() {
        val device = connectedDevice
        checkPermission();
        if (device != null && isConnected) {
            onLog?.invoke("🔌 Desconectando cliente: ${device.name}")
            try {
                gattServer?.cancelConnection(device)
            } catch (e: Exception) {
                onLog?.invoke("⚠️ Erro ao desconectar: ${e.message}")
            }
            connectedDevice = null
            isConnected = false
        }
    }
}