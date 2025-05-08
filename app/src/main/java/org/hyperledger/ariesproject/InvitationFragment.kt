package org.hyperledger.ariesproject

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class InvitationFragment : Fragment() {

    private lateinit var qrCodeImageView: ImageView
    private lateinit var qrCodeTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_qr_code, container, false)

        qrCodeImageView = view.findViewById(R.id.qrCodeImageView)
        qrCodeTextView = view.findViewById(R.id.qrCodeTextView)

        val url:String = arguments?.getString("url").toString()
        qrCodeTextView.text = url
        val bitmap = generateQRCode(url)
        qrCodeImageView.setImageBitmap(bitmap)

        return view
    }

    private fun generateQRCode(text: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }
}
