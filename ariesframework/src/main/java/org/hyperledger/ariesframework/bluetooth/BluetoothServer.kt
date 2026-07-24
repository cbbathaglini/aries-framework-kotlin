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
            onLog?.invoke("BLE permissions not granted - check location (Android 11-) or Bluetooth (Android 12+) permissions.")
            return
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 and earlier - use legacy permissions + location
            (
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startServer() {
        onLog?.invoke("startServer() - preparing advertising and GATT...")
        onLog?.invoke("BLE peripheral supported? ${bluetoothAdapter.isMultipleAdvertisementSupported}")
        onLog?.invoke("Advertiser available? ${bluetoothAdapter.bluetoothLeAdvertiser != null}")
        onLog?.invoke("Android version: ${android.os.Build.VERSION.SDK_INT}")
        onLog?.invoke("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

        if (!hasBlePermissions()) {
            onLog?.invoke("BLE permissions not granted - check location (Android 11-) or Bluetooth (Android 12+) permissions.")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            onLog?.invoke("Bluetooth OFF - enable before proceeding.")
            return
        }

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            onLog?.invoke("Device does not support BLE.")
            return
        }

        if (bluetoothAdapter.bluetoothLeAdvertiser == null) {
            onLog?.invoke("Peripheral mode not supported on this device.")
            return
        }

        Handler(Looper.getMainLooper()).post {
            openGattServerAndAddService()
        }


    }

    fun stopServer() {
        checkPermission()
        stopAdvertising()
        try { gattServer?.close() } catch (_: Throwable) {}
        gattServer = null
        onLog?.invoke("Server stopped.")
    }

    private fun startAdvertising() {
        if (isAdvertising) {
            onLog?.invoke("Advertising already active.")
            return
        }

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onLog?.invoke("advertiser == null (chip/firmware does not support Peripheral mode).")
            return
        }

        // short name to stay within 31 bytes
        checkPermission()
        bluetoothAdapter.name = "ID"

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUUID))
            .setIncludeDeviceName(true)
            .build()

        // Scan response: name
        val scanResp = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        onLog?.invoke("Starting advertising (UUID + name in scanResponse)...")
        checkPermission()
        advertiser.startAdvertising(settings, advData, scanResp, advertiseCallback)
    }

    private fun stopAdvertising() {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        if (!isAdvertising) return
        checkPermission()
        advertiser.stopAdvertising(advertiseCallback)
        isAdvertising = false
        onLog?.invoke("Advertising stopped.")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            onLog?.invoke("Advertising ON (mode=${settingsInEffect.mode}).")
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
            onLog?.invoke("Advertising failed: $errorCode ($reason)")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GATT server

    private fun openGattServerAndAddService() {
        onLog?.invoke("Opening GATT server and adding service...")
        checkPermission()
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        transferCharacteristic = BluetoothGattCharacteristic(
            characteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or
                BluetoothGattCharacteristic.PERMISSION_READ,
        )

        val cccd = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        transferCharacteristic?.addDescriptor(cccd)

        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(transferCharacteristic)

        val added = gattServer?.addService(service) ?: false
        onLog?.invoke("addService returned: $added")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("Service added (OK).")
                startAdvertising()
            } else {
                onLog?.invoke("Failed to add service: status=$status")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val nome = device.name ?: "WNIDD"
            checkPermission()
            onLog?.invoke("Connection: status=$status, newState=$newState (${device.name})")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (nome == "IDDiOS" || nome == "AV" || nome == "WNIDD") {
                    onLog?.invoke("Connection accepted from $nome")
                } else {
                    onLog?.invoke("Connection rejected from $nome")
                    gattServer?.cancelConnection(device)
                    return
                }

                connectedDevice = device
                isConnected = true
                onDeviceConnected?.invoke(device.name ?: "WNIDD")
                Handler(Looper.getMainLooper()).postDelayed({
                    onLog?.invoke("Stopping advertising after stabilizing connection (delay 800ms)")
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
            onLog?.invoke("Descriptor write (len=${value.size})")
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
            onLog?.invoke("onCharacteristicWriteRequest() called - len=${value.size}, prepared=$preparedWrite, response=$responseNeeded")
            val chunk = String(value)

            when {
                chunk == "HELLO_AV" -> {
                    onLog?.invoke("Handshake received from authorized client")
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                    return
                }

                chunk == "<EOF>" -> {
                    if (receivedBuffer.isEmpty()) {
                        onLog?.invoke("EOF received but buffer is empty - ignoring.")
                        return
                    }
                    val full = receivedBuffer.toByteArray()
                    receivedBuffer.clear()
                    val text = String(full)
                    onLog?.invoke("Full JSON received (${full.size} bytes)")
                    onJSONReceived?.invoke(text)
                    Handler(Looper.getMainLooper()).postDelayed({
                        onLog?.invoke("Waiting before disconnecting client...")
                        disconnectClient()
                    }, 1000)
                    onLog?.invoke("Buffer accumulated = ${receivedBuffer.size} bytes")
                }

                else -> {
                    receivedBuffer.addAll(value.toList())
                    onLog?.invoke("Chunk ${value.size} bytes (total=${receivedBuffer.size})")
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
            onLog?.invoke("Disconnecting client: ${device.name}")
            try {
                gattServer?.cancelConnection(device)
            } catch (e: Exception) {
                onLog?.invoke("Error disconnecting: ${e.message}")
            }
            connectedDevice = null
            isConnected = false
        }
    }
}
