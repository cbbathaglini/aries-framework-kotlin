package org.hyperledger.ariesproject

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessage

class SendMessageActivity : AppCompatActivity() {

    private var connectionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_message)  // Layout que você vai criar

        // Pega os elementos pelo ID
        val messageEditText = findViewById<EditText>(R.id.messageEditText)
        val sendButton = findViewById<Button>(R.id.sendButton)

        // Recebe a Connection ID passada
        connectionId = intent.getStringExtra("CONNECTION_ID")

        sendButton.setOnClickListener {
            val messageText = messageEditText.text.toString()

            if (messageText.isNotBlank()) {
                sendMessage(messageText)
            } else {
                Toast.makeText(this, "Digite uma mensagem para enviar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessage(message: String) {
        val app = application as WalletApp

        lifecycleScope.launch {
            try {
                val connectionRecord = app.agent.connectionRepository.getById(connectionId!!);

                val messageOut = OutboundMessage(BasicMessage(message),connectionRecord)

                app.agent.messageSender.send(messageOut)

                runOnUiThread {
                    Toast.makeText(this@SendMessageActivity, "Mensagem enviada: $message", Toast.LENGTH_LONG).show()
                    finish()  // Fecha a Activity depois do envio
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SendMessageActivity, "Erro ao enviar mensagem: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}
