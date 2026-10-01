package br.com.obdpulse.connect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginActivity : Activity() {

    private val scope = MainScope()

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var pin: EditText
    private lateinit var save: Switch
    private lateinit var connect: Button
    private lateinit var clear: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        email = findViewById(R.id.uconnect_email)
        password = findViewById(R.id.uconnect_password)
        pin = findViewById(R.id.uconnect_pin)
        save = findViewById(R.id.uconnect_save)
        connect = findViewById(R.id.uconnect_connect)
        clear = findViewById(R.id.uconnect_clear)
        status = findViewById(R.id.uconnect_status)

        connect.setOnClickListener { doLogin(fromUser = true) }
        clear.setOnClickListener { clearCredentials() }

        if (UconnectStore.isSaved(this)) {
            email.setText(UconnectStore.email(this).orEmpty())
            password.setText(UconnectStore.password(this).orEmpty())
            pin.setText(UconnectStore.pin(this).orEmpty())
            doLogin(fromUser = false)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun doLogin(fromUser: Boolean) {
        val user = email.text.toString().trim()
        val pass = password.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            if (fromUser) Toast.makeText(this, R.string.uconnect_need_credentials, Toast.LENGTH_SHORT).show()
            return
        }
        if (fromUser && save.isChecked) UconnectStore.save(this, user, pass, pin.text.toString())
        setBusy(true)
        status.setText(R.string.uconnect_status_connecting)
        scope.launch {
            try {
                val session = UconnectClient.login(user, pass)
                SessionHolder.onLogin(session, pin.text.toString().ifBlank { UconnectStore.pin(this@LoginActivity) })
                val target = SessionHolder.returnTo ?: ActionsActivity::class.java
                SessionHolder.returnTo = null
                startActivity(Intent(this@LoginActivity, target))
                finish()
            } catch (e: Exception) {
                status.text = getString(R.string.uconnect_status_error, e.message.orEmpty())
                setBusy(false)
            }
        }
    }

    private fun clearCredentials() {
        UconnectStore.clear(this)
        SessionHolder.clear()
        email.setText("")
        password.setText("")
        pin.setText("")
        status.setText(R.string.uconnect_cleared)
    }

    private fun setBusy(busy: Boolean) {
        connect.isEnabled = !busy
        clear.isEnabled = !busy
    }
}
