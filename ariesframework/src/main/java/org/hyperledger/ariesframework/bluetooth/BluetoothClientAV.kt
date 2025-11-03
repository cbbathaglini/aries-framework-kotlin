package org.hyperledger.ariesframework.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    var onLog: ((String) -> Unit)? = null

    /** 🔒 Verifica permissões conforme versão do Android */
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

    private fun ensureBlePermissions(activity: Activity?): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missing.isNotEmpty()) {
            if (activity != null) {
                ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 1001)
            }
            false
        } else {
            true
        }
    }

    /** 🔑 Solicita permissões dinâmicas de acordo com a versão do Android */
    private fun requestBlePermissionsIfNeeded() {
        val act = context as? Activity ?: run {
            onLog?.invoke("❌ Contexto não é uma Activity — não é possível solicitar permissões dinamicamente.")
            return
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missing = permissions.filter {
            ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(act, missing.toTypedArray(), 1001)
        }
    }

    fun start(jsonString: String) {

        if (!bluetoothAdapter.isEnabled) {
            onLog?.invoke("Bluetooth está desativado — ative antes de escanear")
            return
        }

        if (!hasBlePermissions()) {
            onLog?.invoke("Permissões BLE não concedidas — solicitando…")
            requestBlePermissionsIfNeeded()
        }

        if (!ensureBlePermissions(context as? Activity)) {
            onLog?.invoke("🚫 Permissões BLE não concedidas — solicite manualmente e tente novamente.")
            return
        }

            this.jsonString = jsonString
        onLog?.invoke("🔍 Iniciando escaneamento BLE...")
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    onLog?.invoke("Permissão BLUETOOTH_SCAN não concedida")
                    return
                }
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                if (uuids.contains(serviceUuid)) {
                    onLog?.invoke("Dispositivo encontrado: ${result.device.name} (${result.device.address})")
                    scanner.stopScan(this)
                    connect(result.device)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                onLog?.invoke("Falha no escaneamento: $errorCode")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            val missing = permissions.filter {
                ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

            val act = context as? Activity
            if (act != null && missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(act, missing.toTypedArray(), 1001)
            } else {
                onLog?.invoke("❌ Context não é uma Activity — não é possível solicitar permissões dinamicamente")
            }

        }


        try {
            scanner.stopScan(callback)
        } catch (e: Exception) {
            onLog?.invoke("Nenhum scan ativo para parar")
        }

        if (!bluetoothAdapter.isEnabled) {
            bluetoothAdapter.enable()
            Thread.sleep(1000)
        }

        bluetoothAdapter.cancelDiscovery()
        bluetoothAdapter.bluetoothLeScanner?.stopScan(callback)
        bluetoothAdapter.bluetoothLeScanner?.flushPendingScanResults(callback)
        bluetoothAdapter.name = "AV"
        if (bluetoothAdapter.name != "AV") {
            bluetoothAdapter.name = "AV"
            onLog?.invoke("📛 Nome do cliente forçado para 'AV'")
        }

        Handler(Looper.getMainLooper()).postDelayed({
            scanner.startScan(null, settings, callback)
        }, 1000)

        Handler(Looper.getMainLooper()).postDelayed({
            scanner.stopScan(callback)
        }, 10000)
    }


    private fun connect(device: BluetoothDevice) {
        if (!hasBlePermissions()) {
            onLog?.invoke("Permissões BLE não concedidas")
            return
        }

        if (!ensureBlePermissions(context as? Activity)) {
            onLog?.invoke("🚫 Permissões BLE não concedidas — solicite manualmente e tente novamente.")
            return
        }

        onLog?.invoke("🔗 Conectando ao dispositivo...")
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (se: SecurityException) {
            onLog?.invoke("❌ Falha ao conectar: permissão de Bluetooth negada em tempo de execução (${se.message})")
        } catch (e: Exception) {
            onLog?.invoke("❌ Erro inesperado ao conectar: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onLog?.invoke("✅ Conectado, descobrindo serviços...")
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        onLog?.invoke("❌ Permissão BLUETOOTH_CONNECT não concedida")
                    }
                    gatt.requestMtu(newMTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    onLog?.invoke("❌ Desconectado do dispositivo")
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                onLog?.invoke("Permissão BLUETOOTH_SCAN não concedida")
                return
            }
            onLog?.invoke("📏 MTU negociada: $mtu bytes (status=$status)")
            Handler(Looper.getMainLooper()).postDelayed({
                onLog?.invoke("🔍 Descobrindo serviços (delay 800 ms)…")
                gatt.discoverServices()
            }, 800)

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                onLog?.invoke("Permissão BLUETOOTH_SCAN não concedida")
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("Erro ao descobrir serviços: $status")
                return
            }

            val service = gatt.getService(serviceUuid)
            if (service == null) {
                onLog?.invoke("❌ Serviço não encontrado! UUID=$serviceUuid")
                return
            }
            onLog?.invoke("🔍 Serviço encontrado: ${service.uuid}")

            val characteristic = service.getCharacteristic(characteristicUuid)
            if (characteristic == null) {
                onLog?.invoke("❌ Característica não encontrada! UUID=$characteristicUuid")
                return
            }
            onLog?.invoke("🧩 Característica confirmada no service real")
            targetCharacteristic = characteristic
            onLog?.invoke("🧩 Característica encontrada")


            Handler(Looper.getMainLooper()).postDelayed({
                onLog?.invoke("🤝 Enviando handshake inicial HELLO_AV")
                sendLargeData(
                    gatt,
                    "HELLO_AV".toByteArray(Charsets.UTF_8),
                    chunkSize = newMTU,
                    onComplete = {
                        onLog?.invoke("🤝 Handshake enviado, aguardando 300 ms…")
                        Handler(Looper.getMainLooper()).postDelayed({
                            onLog?.invoke("📤 Enviando JSON agora…")
                            sendLargeData(
                                gatt,
                                jsonString.toByteArray(Charsets.UTF_8),
                                chunkSize = newMTU,
                                onProgress = { sent, total ->
                                    onLog?.invoke("📦 Progresso: $sent / $total bytes enviados")
                                },
                                onComplete = {
                                    onLog?.invoke("🎉 Transmissão finalizada com sucesso!")
                                }
                            )
                        }, 300)
                    }
                )
            }, 500)

//            sendLargeData(gatt, jsonString.toByteArray(Charsets.UTF_8), chunkSize = newMTU,
//                onProgress = { sent, total ->
//                    onLog?.invoke("📦 Progresso: $sent / $total bytes enviados")
//                },
//                onComplete = {
//                    onLog?.invoke("🎉 Transmissão finalizada com sucesso!")
//                })
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("✅ Escrita confirmada com sucesso!")
            } else {
                onLog?.invoke("❌ Falha na escrita: status=$status")
            }
            pendingLatch?.countDown()
            pendingLatch = null
        }
    }

    private fun sendLargeData(
        gatt: BluetoothGatt,
        data: ByteArray,
        chunkSize: Int = 20,
        onProgress: ((sent: Int, total: Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        if (!hasBlePermissions()) {
            onLog?.invoke("Permissões BLE não concedidas — abortando envio.")
            return
        }

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

//                    targetCharacteristic?.apply {
//                        writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
//                        value = chunk
//                    }

                    val success = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            onLog?.invoke("⚠️ Permissão BLUETOOTH_CONNECT não concedida no momento — abortando escrita.")
                            false
                        } else {
                            gatt.writeCharacteristic(targetCharacteristic)
                        }
                    } catch (se: SecurityException) {
                        onLog?.invoke("❌ Falha ao escrever característica: permissão de Bluetooth negada (${se.message})")
                        false
                    } catch (e: Exception) {
                        onLog?.invoke("❌ Erro inesperado ao escrever característica: ${e.message}")
                        false
                    }
                    onLog?.invoke("✉️ writeCharacteristic retornou: $success")

                    if (!success) {
                        onLog?.invoke("❌ Falha imediata ao iniciar escrita. Abortando.")
                        break
                    }

//                    if (!latch.await(3, TimeUnit.SECONDS)) {
//                        onLog?.invoke("⏰ Timeout aguardando confirmação da escrita no chunk ${offset / chunkSize + 1}")
//                        break
//                    }

                    totalSent += currentChunkSize
                    offset += currentChunkSize
                    onProgress?.invoke(totalSent, totalSize)
                    Thread.sleep(200)

                } catch (e: Exception) {
                    onLog?.invoke("Erro ao enviar chunk: ${e.message}")
                    break
                }
            }

            targetCharacteristic?.apply {
                //writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                value = "<EOF>".toByteArray(Charsets.UTF_8)
            }
            val success = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    onLog?.invoke("⚠️ Permissão BLUETOOTH_CONNECT não concedida no momento — abortando escrita.")
                    false
                } else {
                    gatt.writeCharacteristic(targetCharacteristic)
                }
            } catch (se: SecurityException) {
                onLog?.invoke("❌ Falha ao escrever característica: permissão de Bluetooth negada (${se.message})")
                false
            } catch (e: Exception) {
                onLog?.invoke("❌ Erro inesperado ao escrever característica: ${e.message}")
                false
            }
            if (success) {
                onLog?.invoke("Envio do EOF.")
            }

            if (offset >= totalSize) {
                onLog?.invoke("✅ Envio completo (${totalSent} bytes).")
                onComplete?.invoke()
            } else {
                onLog?.invoke("⚠️ Envio interrompido em $totalSent bytes.")
            }
        }.start()
    }

    fun disconnect() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onLog?.invoke("Permissão BLUETOOTH_SCAN não concedida")
            return
        }
        gatt?.close()
        gatt = null
        onLog?.invoke("🔌 Conexão BLE encerrada.")
    }
}