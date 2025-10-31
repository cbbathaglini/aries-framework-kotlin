package org.hyperledger.ariesframework.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BluetoothClientAV(private val context: Context) {

    private val serviceUuid = UUID.fromString("d14a2b10-9f12-4b2a-b0c1-7b6b2c0a9d99")
    private val characteristicUuid = UUID.fromString("d14a2b11-9f12-4b2a-b0c1-7b6b2c0a9d99")
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null

    private val TAG = "SimpleBleClient"
    private var jsonString: String = ""
    private var newMTU = 512
    private var pendingLatch: CountDownLatch? = null

    @SuppressLint("MissingPermission")
    fun start(jsonString: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Permissão BLUETOOTH_SCAN não concedida")
            return
        }
        this.jsonString = jsonString
        Log.i(TAG, "🔍 Iniciando escaneamento BLE...")
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                if (uuids.contains(serviceUuid)) {
                    Log.i(TAG, "Dispositivo encontrado: ${result.device.name} (${result.device.address})")
                    scanner.stopScan(this)
                    connect(result.device)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Falha no escaneamento: $errorCode")
            }
        }

        scanner.startScan(null, settings, callback)

        Handler(Looper.getMainLooper()).postDelayed({
            scanner.stopScan(callback)
        }, 10000)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        Log.i(TAG, "🔗 Conectando ao dispositivo...")
        gatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "✅ Conectado, descobrindo serviços...")
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.i(TAG, "❌ Permissão BLUETOOTH_CONNECT não concedida")
                    }
                    gatt.requestMtu(newMTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "❌ Desconectado do dispositivo")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "📏 MTU negociada: $mtu bytes (status=$status)")
            gatt.discoverServices() // só descobre serviços após MTU confirmada

        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Erro ao descobrir serviços: $status")
                return
            }

            val service = gatt.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(characteristicUuid)

            if (characteristic == null) {
                Log.e(TAG, "❌ Característica não encontrada!")
                return
            }

            targetCharacteristic = characteristic
            Log.i(TAG, "🧩 Característica encontrada")

            sendLargeData(gatt, jsonString.toByteArray(Charsets.UTF_8), chunkSize = newMTU,
                onProgress = { sent, total ->
                    Log.i(TAG, "📦 Progresso: $sent / $total bytes enviados")
                },
                onComplete = {
                    Log.i(TAG, "🎉 Transmissão finalizada com sucesso!")
                })
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "✅ Escrita confirmada com sucesso!")
            } else {
                Log.e(TAG, "❌ Falha na escrita: status=$status")
            }
            pendingLatch?.countDown()
            pendingLatch = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLargeData(
        gatt: BluetoothGatt,
        data: ByteArray,
        chunkSize: Int = 20,
        onProgress: ((sent: Int, total: Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        Thread {
            var offset = 0
            val totalSize = data.size
            var totalSent = 0

            while (offset < totalSize) {
                val remaining = totalSize - offset
                val currentChunkSize = minOf(chunkSize, remaining)
                val chunk = data.copyOfRange(offset, offset + currentChunkSize)
                val latch = CountDownLatch(1)
                pendingLatch = latch

                try {
                    targetCharacteristic?.apply {
                        writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        value = chunk
                    }

                    val success = gatt.writeCharacteristic(targetCharacteristic)
                    Log.i(TAG, "✉️ writeCharacteristic retornou: $success")

                    if (!success) {
                        Log.e(TAG, "❌ Falha imediata ao iniciar escrita. Abortando.")
                        break
                    }

                    if (!latch.await(3, TimeUnit.SECONDS)) {
                        Log.e(TAG, "⏰ Timeout aguardando confirmação da escrita no chunk ${offset / chunkSize + 1}")
                        break
                    }

                    totalSent += currentChunkSize
                    offset += currentChunkSize
                    onProgress?.invoke(totalSent, totalSize)
                    Thread.sleep(20)

                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao enviar chunk: ${e.message}")
                    break
                }
            }

            targetCharacteristic?.apply {
                writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                value = "<EOF>".toByteArray(Charsets.UTF_8)
            }
            val success = gatt.writeCharacteristic(targetCharacteristic)
            if (success) {
                Log.e(TAG, "Envio do EOF.")
            }

            if (offset >= totalSize) {
                Log.i(TAG, "✅ Envio completo (${totalSent} bytes).")
                onComplete?.invoke()
            } else {
                Log.w(TAG, "⚠️ Envio interrompido em $totalSent bytes.")
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.close()
        gatt = null
        Log.i(TAG,"🔌 Conexão BLE encerrada.")
    }
}
