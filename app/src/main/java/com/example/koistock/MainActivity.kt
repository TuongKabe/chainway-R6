package com.example.koistock

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.lifecycle.lifecycleScope
import com.example.koistock.device.ChainwayRfidReader
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ScanProfileStore
import com.example.koistock.remote.RemoteLocateCommand
import com.example.koistock.remote.DeviceRegistrar
import com.example.koistock.ui.connection.ConnectionViewModel
import com.google.firebase.messaging.FirebaseMessaging
import com.example.koistock.ui.shell.AppShell
import com.example.koistock.ui.theme.KOIStockTheme
import kotlinx.coroutines.launch

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

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                DeviceRegistrar().register(token)
                    .onSuccess { Log.i("RemoteLocate", "FCM device registration succeeded") }
                    .onFailure { Log.e("RemoteLocate", "FCM device registration failed", it) }
            }
        }

        setContent {
            val readerPermissions = remember {
                if (Build.VERSION.SDK_INT >= 31) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    )
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            var readerPermissionGranted by remember { mutableStateOf<Boolean?>(null) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                readerPermissionGranted = readerPermissions.all { grants[it] == true }
            }
            val requestedPermissions = remember {
                buildList {
                    addAll(readerPermissions)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            }

            LaunchedEffect(Unit) {
                launcher.launch(requestedPermissions)
            }

            KOIStockTheme(darkTheme = false) {
                AppShell(
                    vm = vm,
                    reader = reader,
                    scanProfileStore = scanProfileStore,
                    dataStore = dataStore,
                    readerPermissionGranted = readerPermissionGranted,
                    onRequestReaderPermissions = { launcher.launch(requestedPermissions) },
                )
            }
        }

        handleIntent(intent)
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
