package org.hyperledger.ariesproject.bluetooth

import android.Manifest
import android.annotation.SuppressLint
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
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import java.util.*
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothServer(private val context: Context) {

    private val serviceUUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    private val characteristicUUID: UUID = UUID.fromString("0000ABCD-0000-1000-8000-00805f9b34fb")

    private var bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var transferCharacteristic: BluetoothGattCharacteristic? = null

    private val receivedBuffer = mutableListOf<Byte>()

    var onLog: ((String) -> Unit)? = null
    var onJSONReceived: ((String) -> Unit)? = null

    fun startServer() {
        // ✅ Verifica permissões Bluetooth para Android 12+
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
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        } catch (e: SecurityException) {
            onLog?.invoke("❌ Erro ao abrir GATT Server: ${e.localizedMessage}")
            return
        }

        transferCharacteristic = BluetoothGattCharacteristic(
            characteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(transferCharacteristic)
        gattServer?.addService(service)

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUUID))
            .setIncludeDeviceName(true)
            .build()

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
            onLog?.invoke("📡 Anunciando serviço BLE-Proof-Transfer")
        } catch (e: SecurityException) {
            onLog?.invoke("❌ Erro ao anunciar BLE: ${e.localizedMessage}")
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

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> onLog?.invoke("✅ Cliente conectado: ${device.name}")
                BluetoothProfile.STATE_DISCONNECTED -> onLog?.invoke("❌ Cliente desconectado: ${device.name}")
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

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }
}
