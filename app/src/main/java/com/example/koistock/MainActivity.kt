package com.example.koistock

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.lifecycle.lifecycleScope
import com.example.koistock.device.ChainwayRfidReader
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ScanProfileStore
import com.example.koistock.remote.LocateMessagingService
import com.example.koistock.remote.RemoteLocateCommand
import com.example.koistock.ui.connection.ConnectionViewModel
import com.google.firebase.messaging.FirebaseMessaging
import com.example.koistock.ui.shell.AppShell
import com.example.koistock.ui.theme.KOIStockTheme

val remoteLocateIntentFlow = MutableSharedFlow<RemoteLocateCommand>(
    replay = 1,
    extraBufferCapacity = 4,
)

class MainActivity : ComponentActivity() {
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create {
            applicationContext.preferencesDataStoreFile("device.preferences_pb")
        }
    }

    private val reader: RfidReader by lazy { ChainwayRfidReader(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = DevicePrefs(dataStore)
        val scanProfileStore = ScanProfileStore(dataStore)
        val vm = ConnectionViewModel(reader, prefs, lifecycleScope)

        setContent {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { }
            val permissions = remember {
                buildList {
                    if (Build.VERSION.SDK_INT >= 31) {
                        addAll(listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        ))
                    } else {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            }

            LaunchedEffect(Unit) {
                launcher.launch(permissions)
            }

            KOIStockTheme(darkTheme = false) {
                AppShell(vm, reader, scanProfileStore, dataStore)
            }
        }

        handleIntent(intent)

        // Register FCM token at startup
        val savedToken = getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            .getString("fcm_token", null)
        if (savedToken != null) {
            LocateMessagingService.registerToken(this, savedToken)
        } else {
            // Fetch token actively on first launch
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                        .edit().putString("fcm_token", token).apply()
                    LocateMessagingService.registerToken(this, token)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val commandId = intent?.getStringExtra("commandId") ?: return
        val sku = intent?.getStringExtra("sku") ?: return
        val expiresAt = intent?.getLongExtra("expiresAt", 0L) ?: return
        if (expiresAt == 0L) return

        remoteLocateIntentFlow.tryEmit(RemoteLocateCommand(commandId, sku, expiresAt))
    }

    override fun onDestroy() {
        reader.release()
        super.onDestroy()
    }
}
