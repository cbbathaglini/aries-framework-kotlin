package org.hyperledger.ariesframework.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.*
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothClient(private val context: Context) {

    private val serviceUUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    private val characteristicUUID: UUID = UUID.fromString("0000ABCD-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null

    var onLog: ((String) -> Unit)? = null
    var onConnected: ((String) -> Unit)? = null

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

        try {
            onLog?.invoke("🔍 Procurando periféricos BLE...")
            bluetoothAdapter!!.bluetoothLeScanner.startScan(scanCallback)
        } catch (e: SecurityException) {
            onLog?.invoke("❌ Erro ao iniciar scan: ${e.localizedMessage}")
        }
    }

    fun stopScan() {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
        } catch (e: SecurityException) {
            onLog?.invoke("⚠️ Falha ao parar scan: ${e.localizedMessage}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device = it.device
                onLog?.invoke("📡 Encontrado: ${device.name ?: "Sem nome"} (RSSI: ${it.rssi})")
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            onLog?.invoke("❌ Falha no scan: $errorCode")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("❌ Permissão BLUETOOTH_CONNECT não concedida")
            return
        }

        try {
            onLog?.invoke("🔗 Conectando a ${device.name ?: "desconhecido"}...")
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            onLog?.invoke("❌ Erro ao conectar: ${e.localizedMessage}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onLog?.invoke("✅ Conectado ao periférico ${gatt.device.name}")
                    onConnected?.invoke(gatt.device.name ?: "Desconhecido")
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        gatt.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED ->
                    onLog?.invoke("❌ Desconectado de ${gatt.device.name}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.services.find { it.uuid == serviceUUID }?.let { service ->
                    onLog?.invoke("🧭 Serviço encontrado: ${service.uuid}")
                    targetCharacteristic = service.getCharacteristic(characteristicUUID)
                    onLog?.invoke("✍️ Pronto para enviar JSON.")
                }
            }
        }
    }

    fun sendJSON(json: String) {
        val characteristic = targetCharacteristic
        val gatt = bluetoothGatt

        if (characteristic == null || gatt == null) {
            onLog?.invoke("⚠️ Nenhum periférico ou characteristic disponível.")
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("❌ Sem permissão BLUETOOTH_CONNECT para envio")
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
        } catch (e: SecurityException) {
            onLog?.invoke("❌ Falha no envio: ${e.localizedMessage}")
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            onLog?.invoke("🔌 Conexão BLE encerrada.")
        } catch (e: Exception) {
            onLog?.invoke("⚠️ Erro ao encerrar conexão: ${e.localizedMessage}")
        }
    }
}
