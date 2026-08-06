package org.hyperledger.ariesproject

import android.os.Bundle
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import org.hyperledger.ariesproject.databinding.ActivityQrcodeBinding

abstract class BaseCameraActivity : AppCompatActivity() {

    lateinit var binding: ActivityQrcodeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.teal_700)
        window.navigationBarColor = getColor(R.color.black)
        window.insetsController?.setSystemBarsAppearance(
            0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )
        binding.cameraView.setLifecycleOwner(this)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
