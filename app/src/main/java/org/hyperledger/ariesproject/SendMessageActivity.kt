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
        setContentView(R.layout.activity_send_message)

        // Gets the elements by their IDs
        val messageEditText = findViewById<EditText>(R.id.messageEditText)
        val sendButton = findViewById<Button>(R.id.sendButton)

        // Receives the passed Connection ID
        connectionId = intent.getStringExtra("CONNECTION_ID")

        sendButton.setOnClickListener {
            val messageText = messageEditText.text.toString()

            if (messageText.isNotBlank()) {
                sendMessage(messageText)
            } else {
                Toast.makeText(this, "Type a message to send", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@SendMessageActivity, "Message sent: $message", Toast.LENGTH_LONG).show()
                    finish()  // Closes the Activity after sending
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SendMessageActivity, "Error sending message: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}
