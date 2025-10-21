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
import kotlinx.coroutines.delay
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
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()

    private val writeQueue: ArrayDeque<ByteArray> = ArrayDeque()
    @Volatile private var isWriting = false
    private var negotiatedMtu: Int = 23 // padrão

    // ----------------------------------------------------------------
    // SCAN
    // ----------------------------------------------------------------
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun startScan() {

        if (bluetoothGatt != null) {
            onLog?.invoke("⚠️ Já conectado — ignorando novo scan.")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onLog?.invoke("⚠️ Bluetooth desativado ou não suportado")
            return
        }

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

    fun isConnected(): Boolean {
        return bluetoothGatt != null && targetCharacteristic != null
    }

    fun connectToNamedDevice(name: String) {
        val device = discoveredDevices.values.find { it.name == name }
        if (device != null) {
            onLog?.invoke("🔗 Conectando a $name...")
            connectToDevice(device)
        } else {
            onLog?.invoke("❌ Dispositivo $name não encontrado entre os descobertos.")
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
                val address = device.address ?: return

                // 🚫 Evita duplicados
                if (discoveredDevices.containsKey(address)) return

                discoveredDevices[address] = device
                onLog?.invoke("📡 Encontrado: $name (RSSI: ${it.rssi})")
                onDeviceFound?.invoke(name)

                if (name.contains("IDDiOS", ignoreCase = true) ||
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
            gatt.requestMtu(185)
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
                onLog?.invoke("📏 MTU negociada: $mtu")
            }
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isWriting = false
                writeNext()
            } else {
                onLog?.invoke("❌ onCharacteristicWrite status=$status — reintentando")
                isWriting = false
                writeNext()
            }
        }
    }

    // ----------------------------------------------------------------
    // ENVIO DE JSON (Android → iOS)
    // ----------------------------------------------------------------
    fun sendJSON(json: String) {
        val gatt = bluetoothGatt
        val ch = targetCharacteristic

        // write com resposta garante ordem/entrega
        ch?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val data = json.toByteArray(Charsets.UTF_8)
        val payload = (negotiatedMtu - 3).coerceAtLeast(20) // ATT header 3 bytes
        writeQueue.clear()

        // fatia em chunks seguros
        var i = 0
        while (i < data.size) {
            val end = minOf(i + payload, data.size)
            writeQueue.addLast(data.copyOfRange(i, end))
            i = end
        }
        // EOF
        writeQueue.addLast("<EOF>".toByteArray(Charsets.UTF_8))

        onLog?.invoke("📤 Enfileirados ${writeQueue.size} writes (payload≈$payload)")
        if (!isWriting) writeNext()
    }

    // 🔹 Envia o próximo pedaço quando o anterior concluir
    private fun writeNext() {
        val gatt = bluetoothGatt ?: return
        val ch = targetCharacteristic ?: return

        val next = writeQueue.pollFirst() ?: run {
            isWriting = false
            onLog?.invoke("✅ JSON enviado completamente.")
            return
        }
        isWriting = true
        ch.value = next
        val ok = gatt.writeCharacteristic(ch)
        if (!ok) {
            onLog?.invoke("❌ writeCharacteristic falhou (stack ocupado). Tentando novamente…")
            // re-enfila e tenta depois; aqui é simples: recoloca no início
            writeQueue.addFirst(next)
            isWriting = false
        } else {
            onLog?.invoke("➡️ Enviado chunk, restantes: ${writeQueue.size}")
        }
    }


    fun disconnect() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        onLog?.invoke("🔌 Conexão BLE encerrada.")
    }
}