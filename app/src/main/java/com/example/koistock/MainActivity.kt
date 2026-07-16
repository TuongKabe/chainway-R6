package com.example.koistock

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.lifecycleScope
import com.example.koistock.device.ChainwayRfidReader
import com.example.koistock.device.DevicePrefs
import com.example.koistock.device.RfidReader
import com.example.koistock.ui.connection.ConnectionViewModel
import com.example.koistock.ui.shell.AppShell
import com.example.koistock.ui.theme.KOIStockTheme

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

        val prefs = DevicePrefs(dataStore)
        val vm = ConnectionViewModel(reader, prefs, lifecycleScope)

        setContent {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { }
            val permissions = remember {
                if (Build.VERSION.SDK_INT >= 31) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    )
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            LaunchedEffect(Unit) {
                launcher.launch(permissions)
            }

            KOIStockTheme {
                AppShell(vm, reader)
            }
        }
    }

    override fun onDestroy() {
        reader.release()
        super.onDestroy()
    }
}
