package org.hyperledger.ariesproject.bluetooth

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import java.util.*
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothServer(private val context: Context) {

    private val serviceUUID = UUID.fromString("d14a2b10-9f12-4b2a-b0c1-7b6b2c0a9d99")
    private val characteristicUUID = UUID.fromString("d14a2b11-9f12-4b2a-b0c1-7b6b2c0a9d99")

    private var bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var transferCharacteristic: BluetoothGattCharacteristic? = null

    private val receivedBuffer = mutableListOf<Byte>()

    var onLog: ((String) -> Unit)? = null
    var onJSONReceived: ((String) -> Unit)? = null
    var onDeviceConnected: ((String) -> Unit)? = null

//    fun startServer() {
//        onLog?.invoke("🚀 startServer() iniciado — criando GATT server...")
//        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            onLog?.invoke("⚠️ Permissão BLUETOOTH_CONNECT não concedida.")
//            return
//        }
//
//        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            onLog?.invoke("⚠️ Permissão BLUETOOTH_ADVERTISE não concedida.")
//            return
//        }
//
//        try {
//            bluetoothAdapter.name = "IDDAndroid"
//            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
//
//            // Cria e adiciona serviço ANTES de iniciar advertising
//            transferCharacteristic = BluetoothGattCharacteristic(
//                characteristicUUID,
//                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
//                BluetoothGattCharacteristic.PERMISSION_WRITE
//            )
//
//            val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
//            service.addCharacteristic(transferCharacteristic)
//            gattServer?.addService(service)
//
//        } catch (e: Exception) {
//            onLog?.invoke("❌ Erro ao inicializar servidor: ${e.localizedMessage}")
//        }
//
//        transferCharacteristic = BluetoothGattCharacteristic(
//            characteristicUUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
//            BluetoothGattCharacteristic.PERMISSION_WRITE,
//        )
//
//        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
//        service.addCharacteristic(transferCharacteristic)
//        onLog?.invoke("🧱 Serviço criado: ${service.uuid}")
//        onLog?.invoke("🔹 Característica criada: ${transferCharacteristic?.uuid}")
//        val added = gattServer?.addService(service)
//        onLog?.invoke("📦 addService retornou: $added")
//
//        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
//        val settings = AdvertiseSettings.Builder()
//            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
//            .setConnectable(true)
//            .build()
//
//        val data = AdvertiseData.Builder()
//            .addServiceUuid(ParcelUuid(serviceUUID))
//            .setIncludeDeviceName(true)
//            .build()
//
//        try {
//            advertiser.startAdvertising(settings, data, advertiseCallback)
//            onLog?.invoke("📡 Anunciando serviço BLE-Proof-Transfer")
//        } catch (e: SecurityException) {
//            onLog?.invoke("❌ Erro ao anunciar BLE: ${e.localizedMessage}")
//        }
//    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        onLog?.invoke("🚀 startServer() iniciado — criando GATT server...")

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("⚠️ Permissão BLUETOOTH_CONNECT não concedida.")
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("⚠️ Permissão BLUETOOTH_ADVERTISE não concedida.")
            return
        }

        try {
            bluetoothAdapter.name = "IDDAndroid"
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
            onLog?.invoke("🔍 Característica propriedades: ${transferCharacteristic?.properties}")

            val descriptor = BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            transferCharacteristic?.addDescriptor(descriptor)

            val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(transferCharacteristic)
            val added = gattServer?.addService(service)

            onLog?.invoke("🧱 Serviço criado: ${service.uuid}")
            onLog?.invoke("🔹 Característica criada: ${transferCharacteristic?.uuid}")
            onLog?.invoke("📦 addService retornou: $added")

        } catch (e: Exception) {
            onLog?.invoke("❌ Erro ao inicializar servidor: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("⚠️ Permissão BLUETOOTH_ADVERTISE não concedida.")
            return
        }

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onLog?.invoke("❌ Este dispositivo não suporta BLE advertising.")
            return
        }

        // Configurações do advertising
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        // Pacote principal — anuncia apenas o UUID (menor que 31 bytes)
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUUID))
            .setIncludeDeviceName(false)
            .build()

        // Pacote de resposta com o nome do dispositivo
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
            onLog?.invoke("📡 Iniciando advertising BLE (UUID + nome via scanResponse)...")
        } catch (e: SecurityException) {
            onLog?.invoke("❌ Erro ao iniciar advertising: ${e.localizedMessage}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            onLog?.invoke("✅ Anúncio BLE iniciado com sucesso.")
        }

        override fun onStartFailure(errorCode: Int) {
            onLog?.invoke("❌ Falha ao anunciar BLE: $errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("🧩 Serviço adicionado, iniciando advertising...")
                startAdvertising()
            } else {
                onLog?.invoke("❌ Falha ao adicionar serviço: status=$status")
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            onLog?.invoke("📤 Notificação enviada para ${device?.name} (status=$status)")
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            onLog?.invoke("📏 MTU alterado: $mtu")
        }
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: android.bluetooth.BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            onLog?.invoke("✏️ onDescriptorWriteRequest: valor=${value.contentToString()}")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                onLog?.invoke("✅ CCCD configurado pelo cliente (${device.name})")
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
            onLog?.invoke("💾 onExecuteWrite executado: $execute")
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            onLog?.invoke("📶 Mudança de estado: status=$status, newState=$newState (${device.name})")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val name = device.name ?: "Dispositivo BLE (sem nome)"
                    onLog?.invoke("✅ Cliente conectado: $name")
                    onDeviceConnected?.invoke(name)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {

                    val name = device.name ?: "Desconhecido"
                    onLog?.invoke("❌ Cliente desconectado: $name")
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            onLog?.invoke("✉️ onCharacteristicWriteRequest chamado! valor=${value.size} bytes")
            val chunk = String(value)
            if (chunk == "<EOF>") {
                val fullData = receivedBuffer.toByteArray()
                val jsonString = String(fullData)
                onLog?.invoke("📥 JSON completo recebido (${fullData.size} bytes)")
                onJSONReceived?.invoke(jsonString)
                receivedBuffer.clear()
            } else {
                receivedBuffer.addAll(value.toList())
                onLog?.invoke("⬇️ Recebido ${value.size} bytes (${receivedBuffer.size} total)")
            }

//            if (responseNeeded) {
//                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
//            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }
}
