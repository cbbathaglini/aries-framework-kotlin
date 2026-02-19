package org.hyperledger.ariesproject

import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeScannerActivity : BaseCameraActivity() {
    private val CAMERA_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        }

        binding.cameraView.addFrameProcessor {
            val image = InputImage.fromByteArray(
                it.getData(),
                it.size.width,
                it.size.height,
                0, // Rotação (ajuste conforme necessário)
                InputImage.IMAGE_FORMAT_NV21
            )
            runBarcodeScanner(image)
        }
    }

    private fun runBarcodeScanner(image: InputImage) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        val scanner: BarcodeScanner = BarcodeScanning.getClient(options)

        scanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                when (barcode.valueType) {
                    Barcode.TYPE_URL -> {
                        val data = Intent()
                        data.putExtra("qrcode", barcode.rawValue)
                        setResult(RESULT_OK, data)
                        finish()
                    }
                    Barcode.TYPE_TEXT -> {
                        val data = Intent()
                        data.putExtra("qrcode", barcode.rawValue)
                        setResult(RESULT_OK, data)
                        finish()
                    }
                }
            }
        }
        .addOnFailureListener {
            Toast.makeText(baseContext, "Sorry, something went wrong!", Toast.LENGTH_SHORT).show()
        }

    }
}
