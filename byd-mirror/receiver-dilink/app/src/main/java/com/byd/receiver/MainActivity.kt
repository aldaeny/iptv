package com.byd.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.byd.receiver.network.NsdServiceRegistrar
import com.byd.receiver.protocol.BydReceiverServer
import com.byd.receiver.ui.ReceiverScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var nsdRegistrar: NsdServiceRegistrar
    private val server = BydReceiverServer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsdRegistrar = NsdServiceRegistrar(this)
        nsdRegistrar.register(port = BydReceiverServer.CONTROL_PORT)
        server.start(lifecycleScope)

        setContent {
            val state by server.state.collectAsState()
            ReceiverScreen(
                state = state,
                onSurfaceReady = { surface -> server.attachSurface(surface) },
                onTouchNormalized = { x, y, action ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        server.sendTouchEvent(x, y, action)
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        nsdRegistrar.unregister()
        server.stop()
        super.onDestroy()
    }
}
