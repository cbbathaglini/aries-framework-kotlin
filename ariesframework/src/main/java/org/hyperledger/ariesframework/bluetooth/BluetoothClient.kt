package org.hyperledger.ariesframework.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothClient(private val context: Context) {

    private val serviceUUID = UUID.fromString("d14a2b10-9f12-4b2a-b0c1-7b6b2c0a9d99")
    private val characteristicUUID = UUID.fromString("d14a2b11-9f12-4b2a-b0c1-7b6b2c0a9d99")

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null

    private val receivedBuffer = mutableListOf<Byte>()

    var onLog: ((String) -> Unit)? = null
    var onConnected: ((String) -> Unit)? = null
    var onJSONReceived: ((String) -> Unit)? = null

    var onDeviceFound: ((String) -> Unit)? = null
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()

    private val writeQueue: ArrayDeque<ByteArray> = ArrayDeque()

    @Volatile private var isWriting = false
    private var negotiatedMtu: Int = 23

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun startScan() {
        if (bluetoothGatt != null) {
            onLog?.invoke("Already connected - ignoring new scan.")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onLog?.invoke("Bluetooth disabled or not supported")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onLog?.invoke("Bluetooth disabled or not supported")
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("BLUETOOTH_SCAN permission not granted")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )

            val missing = permissions.filter {
                ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

            val act = context as? Activity
            if (act != null && missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(act, missing.toTypedArray(), 1001)
            }
        }

        onLog?.invoke("Starting BLE peripheral scan with UUID: $serviceUUID")

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothAdapter?.bluetoothLeScanner?.apply {
            stopScan(scanCallback)
            startScan(listOf(filter), settings, scanCallback)
        }
    }

    fun isConnected(): Boolean {
        return bluetoothGatt != null && targetCharacteristic != null
    }

    fun connectToNamedDevice(name: String) {
        val device = discoveredDevices.values.find { it.name == name }
        if (device != null) {
            onLog?.invoke("Connecting to $name...")
            connectToDevice(device)
        } else {
            onLog?.invoke("Device $name not found among discovered devices.")
        }
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device = it.device
                val name = device.name ?: "IDDAn"
                val address = device.address ?: return

                if (discoveredDevices.containsKey(address)) return

                discoveredDevices[address] = device
                onLog?.invoke("Found: $name (RSSI: ${it.rssi})")
                onDeviceFound?.invoke(name)

                if (name.contains("IDDiOS", ignoreCase = true) ||
                    name.contains("BLE-Proof-Transfer", ignoreCase = true)
                ) {
                    onLog?.invoke("Auto-connecting to $name")
                    stopScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            onLog?.invoke("Scan failed: $errorCode")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("BLUETOOTH_CONNECT permission not granted")
            return
        }

        onLog?.invoke("Connecting to ${device.name ?: "unknown"}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onLog?.invoke("Connected to peripheral ${gatt.device.name}")
                    onConnected?.invoke(gatt.device.name ?: "Desconhecido")
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    onLog?.invoke("Disconnected from ${gatt.device.name}")
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("Failed to discover services (status=$status)")
                return
            }

            val service = gatt.getService(serviceUUID)
            if (service == null) {
                onLog?.invoke("Service not found: $serviceUUID")
                return
            }

            targetCharacteristic = service.getCharacteristic(characteristicUUID)
            if (targetCharacteristic == null) {
                onLog?.invoke("Characteristic not found: $characteristicUUID")
                return
            }

            onLog?.invoke("Service and characteristic found - ready for sending and reading.")

            // Enable notifications to receive data from iOS
            gatt.setCharacteristicNotification(targetCharacteristic, true)
            val descriptor = targetCharacteristic!!.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            )
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            gatt.requestMtu(512)
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
                onLog?.invoke("MTU negotiated: $mtu")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            val chunk = String(value)

            if (chunk == "<EOF>") {
                val full = receivedBuffer.toByteArray()
                val json = String(full)
                onLog?.invoke("Full JSON received (${full.size} bytes)")
                onJSONReceived?.invoke(json)
                receivedBuffer.clear()
            } else {
                receivedBuffer.addAll(value.toList())
                onLog?.invoke("Received ${value.size} bytes (${receivedBuffer.size} total)")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isWriting = false
                writeNext()
            } else {
                onLog?.invoke("onCharacteristicWrite status=$status - retrying")
                isWriting = false
                writeNext()
            }
        }
    }

    fun sendJSONFast(json: String) {
        val gatt = bluetoothGatt ?: return
        val ch = targetCharacteristic ?: return
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val data = json.toByteArray(Charsets.UTF_8)
        val mtuPayload = (negotiatedMtu - 3).coerceAtLeast(20)
        var i = 0
        while (i < data.size) {
            val end = minOf(i + mtuPayload, data.size)
            ch.value = data.copyOfRange(i, end)
            gatt.writeCharacteristic(ch)
            i = end
            Thread.sleep(10)
        }
        ch.value = "<EOF>".toByteArray(Charsets.UTF_8)
        gatt.writeCharacteristic(ch)
        onLog?.invoke("JSON sent (${data.size} bytes).")
    }

    fun sendJSON(json: String) {
        val gatt = bluetoothGatt
        val ch = targetCharacteristic

        // write with response guarantees order/delivery
        ch?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val data = json.toByteArray(Charsets.UTF_8)
        val payload = (negotiatedMtu - 3).coerceAtLeast(20)
        writeQueue.clear()

        // split into safe chunks
        var i = 0
        while (i < data.size) {
            val end = minOf(i + payload, data.size)
            writeQueue.addLast(data.copyOfRange(i, end))
            i = end
        }
        // EOF
        writeQueue.addLast("<EOF>".toByteArray(Charsets.UTF_8))

        onLog?.invoke("Queued ${writeQueue.size} writes (payload≈$payload)")
        if (!isWriting) writeNext()
    }

    // Sends the next chunk when the previous one completes

    private fun writeNext() {
        val gatt = bluetoothGatt ?: return
        val ch = targetCharacteristic ?: return

        synchronized(writeQueue) {
            if (isWriting) return
            isWriting = true

            val next = writeQueue.pollFirst() ?: run {
                isWriting = false
                return
            }

            ch.value = next
            val ok = gatt.writeCharacteristic(ch)

            if (ok) {
                Handler(Looper.getMainLooper()).postDelayed({
                    synchronized(writeQueue) {
                        isWriting = false
                        writeNext()
                    }
                }, 40)
            } else {
                onLog?.invoke("Failed to send chunk - retrying...")
                writeQueue.addFirst(next)
                isWriting = false
                Handler(Looper.getMainLooper()).postDelayed({
                    writeNext()
                }, 100)
            }
        }
    }

    fun disconnect() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        onLog?.invoke("BLE connection closed.")
    }
}
