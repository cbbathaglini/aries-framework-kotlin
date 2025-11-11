package org.hyperledger.ariesproject.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
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

    private fun checkPermission() {
        if (!hasBlePermissions()) {
            onLog?.invoke("🚫 Permissões BLE não concedidas — verifique se a localização (Android 11-) ou Bluetooth (Android 12+) estão ativas.")
            return
        }
    }

//    fun startServer() {
//        onLog?.invoke("🚀 startServer() — preparando advertising e GATT…")
//        onLog?.invoke("📱 BLE peripheral suportado? ${bluetoothAdapter.isMultipleAdvertisementSupported}")
//        onLog?.invoke("💡 Advertiser disponível? ${bluetoothAdapter.bluetoothLeAdvertiser != null}")
//        onLog?.invoke("⚙️ Versão Android: ${android.os.Build.VERSION.SDK_INT}")
//        onLog?.invoke("🏷️ Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
//
//        val needAdvertise = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
//        val needConnect   = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)   != PackageManager.PERMISSION_GRANTED
//        if (needAdvertise || needConnect) {
//            onLog?.invoke("⚠️ Permissões de Bluetooth não concedidas (ADVERTISE/CONNECT).")
//            return
//        }
//
//        if (!bluetoothAdapter.isEnabled) {
//            onLog?.invoke("⚠️ Bluetooth OFF — ative antes.")
//            return
//        }
//        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
//            onLog?.invoke("❌ Dispositivo não suporta BLE.")
//            return
//        }
//        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
//            onLog?.invoke("❌ Múltiplos advertisers/Peripheral mode não suportados.")
//            return
//        }
//
//        Handler(Looper.getMainLooper()).post { startAdvertising() }
//
//        Handler(Looper.getMainLooper()).post {
//            openGattServerAndAddService()
//        }
//    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 e anteriores — usar permissões antigas + localização
            (
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startServer() {
        onLog?.invoke("🚀 startServer() — preparando advertising e GATT…")
        onLog?.invoke("📱 BLE peripheral suportado? ${bluetoothAdapter.isMultipleAdvertisementSupported}")
        onLog?.invoke("💡 Advertiser disponível? ${bluetoothAdapter.bluetoothLeAdvertiser != null}")
        onLog?.invoke("⚙️ Versão Android: ${android.os.Build.VERSION.SDK_INT}")
        onLog?.invoke("🏷️ Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

        if (!hasBlePermissions()) {
            onLog?.invoke("🚫 Permissões BLE não concedidas — verifique se a localização (Android 11-) ou Bluetooth (Android 12+) estão ativas.")
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

        if (bluetoothAdapter.bluetoothLeAdvertiser == null) {
            onLog?.invoke("❌ Modo periférico não suportado neste dispositivo.")
            return
        }

        // ✅ Primeiro abre e registra o serviço GATT
        Handler(Looper.getMainLooper()).post {
            openGattServerAndAddService()
        }

        // ✅ Só depois de o serviço estar ativo inicia o advertising
//        Handler(Looper.getMainLooper()).postDelayed({
//            startAdvertising()
//        }, 500)
    }

    fun stopServer() {
        checkPermission()
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
        checkPermission()
        bluetoothAdapter.name = "ID"

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUUID))
            .setIncludeDeviceName(true) // de false para true
            .build()

        // Scan response: nome
        val scanResp = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        onLog?.invoke("📡 Iniciando advertising (UUID + nome no scanResponse)…")
        checkPermission()
        advertiser.startAdvertising(settings, advData, scanResp, advertiseCallback)
    }

    private fun stopAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        if (!isAdvertising) return
        checkPermission()
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

//    private fun openGattServerAndAddService() {
//        onLog?.invoke("🧱 Abrindo GATT server e adicionando serviço…")
//        checkPermission();
//        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
//
// //        transferCharacteristic = BluetoothGattCharacteristic(
// //            characteristicUUID,
// //            BluetoothGattCharacteristic.PROPERTY_WRITE or
// //                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
// //                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
// //                    BluetoothGattCharacteristic.PROPERTY_READ,
// //            BluetoothGattCharacteristic.PERMISSION_WRITE or
// //                    BluetoothGattCharacteristic.PERMISSION_READ
// //        )
//
//        transferCharacteristic = BluetoothGattCharacteristic(
//            characteristicUUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
//                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
//                    BluetoothGattCharacteristic.PROPERTY_READ,
//            BluetoothGattCharacteristic.PERMISSION_WRITE or
//                    BluetoothGattCharacteristic.PERMISSION_READ
//        )
//
//        val cccd = BluetoothGattDescriptor(
//            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
//            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
//        )
//        transferCharacteristic?.addDescriptor(cccd)
//
//        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
//        service.addCharacteristic(transferCharacteristic)
//
//        val added = gattServer?.addService(service) ?: false
//        onLog?.invoke("📦 addService retornou: $added")
//    }

    private fun openGattServerAndAddService() {
        onLog?.invoke("🧱 Abrindo GATT server e adicionando serviço…")
        checkPermission()
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

//        transferCharacteristic = BluetoothGattCharacteristic(
//            characteristicUUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
//                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
//                    BluetoothGattCharacteristic.PROPERTY_READ,
//            BluetoothGattCharacteristic.PERMISSION_WRITE or
//                    BluetoothGattCharacteristic.PERMISSION_READ
//        )

//        transferCharacteristic = BluetoothGattCharacteristic(
//            characteristicUUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE or
//                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
//            BluetoothGattCharacteristic.PERMISSION_WRITE
//        )

        transferCharacteristic = BluetoothGattCharacteristic(
            characteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or
                BluetoothGattCharacteristic.PERMISSION_READ,
        )

//        transferCharacteristic = BluetoothGattCharacteristic(
//            characteristicUUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE or
//                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
//                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
//            BluetoothGattCharacteristic.PERMISSION_WRITE
//        )

        val cccd = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
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
                startAdvertising()
            } else {
                onLog?.invoke("❌ Falha ao adicionar serviço: status=$status")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val nome = device.name ?: "WNIDD"
            checkPermission()
            onLog?.invoke("📶 Conexão: status=$status, newState=$newState (${device.name})")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (nome == "IDDiOS" || nome == "AV" || nome == "WNIDD") {
                    onLog?.invoke("🤝 Conexão aceita de $nome")
                } else {
                    onLog?.invoke("🚫 Conexão rejeitada de $nome")
                    gattServer?.cancelConnection(device)
                    return
                }

                connectedDevice = device
                isConnected = true
                onDeviceConnected?.invoke(device.name ?: "WNIDD")
                Handler(Looper.getMainLooper()).postDelayed({
                    onLog?.invoke("🕒 Parando advertising após estabilizar conexão (delay 800ms)")
                    stopAdvertising()
                }, 800)
            } else {
                connectedDevice = null
                isConnected = false
                Handler(Looper.getMainLooper()).postDelayed({
                    startAdvertising()
                }, 1000)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            onLog?.invoke("✏️ Descriptor write (len=${value.size})")
            checkPermission()
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            onLog?.invoke("🧾 onCharacteristicWriteRequest() chamado — len=${value.size}, prepared=$preparedWrite, response=$responseNeeded")
            val chunk = String(value)
//            if (chunk == "<EOF>") {
//                val full = receivedBuffer.toByteArray()
//                receivedBuffer.clear()
//                val text = String(full)
//                onLog?.invoke("📥 JSON completo recebido (${full.size} bytes)")
//                onJSONReceived?.invoke(text)
//
// //                Handler(Looper.getMainLooper()).post {
// //                    disconnectClient()
// //                }
//
//                Handler(Looper.getMainLooper()).postDelayed({
//                    onLog?.invoke("⏳ Aguardando antes de desconectar cliente…")
//                    disconnectClient()
//                }, 1000)
//
//            } else {
//                receivedBuffer.addAll(value.toList())
//                onLog?.invoke("⬇️ Chunk ${value.size} bytes (total=${receivedBuffer.size})")
//            }

            when {
                chunk == "HELLO_AV" -> {
                    onLog?.invoke("🤝 Handshake recebido de cliente autorizado ✅")
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                    return
                }

                chunk == "<EOF>" -> {
                    if (receivedBuffer.isEmpty()) {
                        onLog?.invoke("⚠️ EOF recebido, mas buffer está vazio — ignorando.")
                        return
                    }
                    val full = receivedBuffer.toByteArray()
                    receivedBuffer.clear()
                    val text = String(full)
                    onLog?.invoke("📥 JSON completo recebido (${full.size} bytes)")
                    onJSONReceived?.invoke(text)
                    Handler(Looper.getMainLooper()).postDelayed({
                        onLog?.invoke("⏳ Aguardando antes de desconectar cliente…")
                        disconnectClient()
                    }, 1000)
                    onLog?.invoke("📥 Buffer acumulado = ${receivedBuffer.size} bytes")
                }

                else -> {
                    receivedBuffer.addAll(value.toList())
                    onLog?.invoke("⬇️ Chunk ${value.size} bytes (total=${receivedBuffer.size})")
                }
            }
            if (responseNeeded) {
                checkPermission()
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    fun disconnectClient() {
        val device = connectedDevice
        checkPermission()
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
