package org.hyperledger.ariesframework.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
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

    // 🔹 Callback opcional quando um dispositivo é encontrado
    var onDeviceFound: ((String) -> Unit)? = null

    // ----------------------------------------------------------------
    // SCAN
    // ----------------------------------------------------------------
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onLog?.invoke("⚠️ Bluetooth desativado ou não suportado")
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("❌ Permissão BLUETOOTH_SCAN não concedida")
            return
        }

        onLog?.invoke("🔍 Iniciando scan por periféricos BLE com UUID: $serviceUUID")

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

    fun connectToNamedDevice(name: String) {
        val device = bluetoothAdapter?.bondedDevices?.find { it.name == name }
        if (device != null) {
            onLog?.invoke("🔗 Conectando a $name...")
            connectToDevice(device)
        } else {
            onLog?.invoke("❌ Dispositivo $name não encontrado entre os pareados.")
        }
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device = it.device
                val name = device.name ?: "Sem nome"
                onLog?.invoke("📡 Encontrado: $name (RSSI: ${it.rssi})")
                onDeviceFound?.invoke(name)

                if (name.contains("IDDAndroid", ignoreCase = true) ||
                    name.contains("BLE-Proof-Transfer", ignoreCase = true)
                ) {
                    onLog?.invoke("📱 Conectando automaticamente a $name")
                    stopScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            onLog?.invoke("❌ Falha no scan: $errorCode")
        }
    }

    // ----------------------------------------------------------------
    // CONEXÃO
    // ----------------------------------------------------------------
    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("❌ Permissão BLUETOOTH_CONNECT não concedida")
            return
        }

        onLog?.invoke("🔗 Conectando a ${device.name ?: "desconhecido"}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onLog?.invoke("✅ Conectado ao periférico ${gatt.device.name}")
                    onConnected?.invoke(gatt.device.name ?: "Desconhecido")
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    onLog?.invoke("❌ Desconectado de ${gatt.device.name}")
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("❌ Falha ao descobrir serviços (status=$status)")
                return
            }

            val service = gatt.getService(serviceUUID)
            if (service == null) {
                onLog?.invoke("❌ Serviço não encontrado: $serviceUUID")
                return
            }

            targetCharacteristic = service.getCharacteristic(characteristicUUID)
            if (targetCharacteristic == null) {
                onLog?.invoke("❌ Característica não encontrada: $characteristicUUID")
                return
            }

            onLog?.invoke("🧭 Serviço e característica encontrados — pronto para envio e leitura.")

            // 🔹 Habilita notificações para receber dados do iOS
            gatt.setCharacteristicNotification(targetCharacteristic, true)
            val descriptor = targetCharacteristic!!.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            val chunk = String(value)

            if (chunk == "<EOF>") {
                val full = receivedBuffer.toByteArray()
                val json = String(full)
                onLog?.invoke("📥 JSON completo recebido (${full.size} bytes)")
                onJSONReceived?.invoke(json)
                receivedBuffer.clear()
            } else {
                receivedBuffer.addAll(value.toList())
                onLog?.invoke("⬇️ Recebido ${value.size} bytes (${receivedBuffer.size} total)")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            onLog?.invoke("📤 Chunk enviado com status=$status")
        }
    }

    // ----------------------------------------------------------------
    // ENVIO DE JSON (Android → iOS)
    // ----------------------------------------------------------------
    fun sendJSON(json: String) {
        val gatt = bluetoothGatt
        val characteristic = targetCharacteristic

        if (gatt == null || characteristic == null) {
            onLog?.invoke("⚠️ Nenhum periférico ou characteristic disponível.")
            return
        }

        val data = json.toByteArray()
        val mtu = 180
        onLog?.invoke("📤 Enviando JSON (${data.size} bytes)...")

        try {
            for (i in data.indices step mtu) {
                val end = minOf(i + mtu, data.size)
                val chunk = data.copyOfRange(i, end)
                characteristic.value = chunk
                gatt.writeCharacteristic(characteristic)
                onLog?.invoke("➡️ Enviado chunk ${i / mtu + 1}")
            }

            characteristic.value = "<EOF>".toByteArray()
            gatt.writeCharacteristic(characteristic)
            onLog?.invoke("✅ JSON enviado completamente.")
        } catch (e: Exception) {
            onLog?.invoke("❌ Erro ao enviar JSON: ${e.localizedMessage}")
        }
    }

    fun disconnect() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        onLog?.invoke("🔌 Conexão BLE encerrada.")
    }
}