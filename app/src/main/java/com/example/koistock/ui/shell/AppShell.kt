package com.example.koistock.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.koistock.data.remote.HttpLocationRepository
import com.example.koistock.data.remote.HttpProductRepository
import com.example.koistock.data.remote.HttpStockCommandRepository
import com.example.koistock.data.remote.HttpTagRepository
import com.example.koistock.data.remote.HttpTransactionRepository
import com.example.koistock.data.remote.KoiApiConfig
import com.example.koistock.data.remote.KoiApiFactory
import com.example.koistock.device.RfidReader
import com.example.koistock.domain.ExpectedItem
import com.example.koistock.ui.assign.AssignTagScreen
import com.example.koistock.ui.assign.AssignTagViewModel
import com.example.koistock.ui.common.BatteryIndicator
import com.example.koistock.ui.connection.ConnectionViewModel
import com.example.koistock.ui.connection.PairingScreen
import com.example.koistock.ui.count.CountScreen
import com.example.koistock.ui.count.CountViewModel
import com.example.koistock.ui.guide.ConnectionGuideScreen
import com.example.koistock.ui.hardware.HardwareTestScreen
import com.example.koistock.ui.hardware.HardwareTestViewModel
import com.example.koistock.ui.inout.InOutScreen
import com.example.koistock.ui.inout.InOutViewModel
import com.example.koistock.ui.locate.LocateScreen
import com.example.koistock.ui.locate.LocateViewModel
import com.example.koistock.ui.lookup.LookupScreen
import com.example.koistock.ui.lookup.LookupViewModel
import com.example.koistock.ui.putaway.PutawayScreen
import com.example.koistock.ui.putaway.PutawayViewModel
import com.example.koistock.ui.settings.SettingsScreen
import com.example.koistock.ui.zones.ZoneScreen
import com.example.koistock.ui.zones.ZoneViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    vm: ConnectionViewModel,
    reader: RfidReader,
) {
    val navController = rememberNavController()
    val state by vm.state.collectAsState()
    val batteryPercent by vm.batteryPercent.collectAsState()

    val api = remember { KoiApiFactory.create() }
    val productRepo = remember { HttpProductRepository(api) }
    val tagRepo = remember { HttpTagRepository(api) }
    val txRepo = remember { HttpTransactionRepository(api) }
    val locationRepo = remember { HttpLocationRepository(api) }
    val stockCommandRepo = remember { HttpStockCommandRepository(api) }
    val products by productRepo.observeAll().collectAsState(initial = emptyList())
    val expectedItems = remember(products) {
        products.map { ExpectedItem(it.sku, it.name, it.quantity.toInt(), it.locationCode) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val shellScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isDashboard = currentRoute == null || currentRoute == AppDestinations.Dashboard.route
    val title = if (isDashboard) "KOIStock" else AppDestinations.titleByRoute[currentRoute] ?: "KOIStock"

    fun runSync() {
        if (isSyncing) return
        isSyncing = true
        shellScope.launch {
            val ok = runCatching {
                productRepo.refresh()
                locationRepo.refresh()
            }.isSuccess
            if (state is com.example.koistock.device.ConnectionState.Connected) {
                vm.refreshBattery()
            }
            isSyncing = false
            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            snackbarHostState.showSnackbar(
                if (ok) "Đã đồng bộ dữ liệu lúc $now" else "Đồng bộ thất bại, kiểm tra kết nối mạng",
            )
        }
    }

    LaunchedEffect(Unit) {
        productRepo.refresh()
        locationRepo.refresh()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!isDashboard) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                            )
                        }
                    }
                },
                actions = {
                    BatteryIndicator(percent = batteryPercent)
                    if (isDashboard) {
                        IconButton(onClick = { runSync() }, enabled = !isSyncing) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = "Đồng bộ dữ liệu")
                            }
                        }
                        IconButton(onClick = { navController.navigate(AppDestinations.Settings.route) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Cài đặt")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestinations.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestinations.Dashboard.route) {
                DashboardScreen(
                    connectionState = state,
                    batteryPercent = batteryPercent,
                    isSyncing = isSyncing,
                    onOpen = navController::navigate,
                    onSync = { runSync() },
                    onOpenPairing = { navController.navigate(AppDestinations.Pairing.route) },
                )
            }
            composable(AppDestinations.Settings.route) {
                SettingsScreen(
                    connectionState = state,
                    baseUrl = KoiApiConfig.BASE_URL,
                    onOpen = navController::navigate,
                )
            }
            composable(AppDestinations.Pairing.route) {
                PairingScreen(vm = vm) {
                    navController.popBackStack()
                }
            }
            composable(AppDestinations.Guide.route) {
                ConnectionGuideScreen()
            }
            composable(AppDestinations.Hardware.route) {
                val hardwareScope = rememberCoroutineScope()
                val hardwareVm = remember {
                    HardwareTestViewModel(
                        reader = reader,
                        scope = hardwareScope,
                    )
                }
                HardwareTestScreen(vm = hardwareVm)
            }
            composable(AppDestinations.Lookup.route) {
                val lookupScope = rememberCoroutineScope()
                val lookupVm = remember(vm) {
                    LookupViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        scope = lookupScope,
                    )
                }
                LookupScreen(
                    vm = lookupVm,
                    onAssign = { navController.navigate(AppDestinations.Assign.route) },
                )
            }
            composable(AppDestinations.Locate.route) {
                val locateScope = rememberCoroutineScope()
                val locateVm = remember {
                    LocateViewModel(
                        reader = reader,
                        scope = locateScope,
                    )
                }
                LocateScreen(
                    vm = locateVm,
                    products = products,
                    tagRepo = tagRepo,
                    isConnected = state is com.example.koistock.device.ConnectionState.Connected,
                    onOpenPairing = { navController.navigate(AppDestinations.Pairing.route) },
                )
            }
            composable(AppDestinations.Count.route) {
                val countScope = rememberCoroutineScope()
                val countVm = remember {
                    CountViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        txRepo = txRepo,
                        deviceId = "r6-device",
                        now = { System.currentTimeMillis() },
                        scope = countScope,
                    )
                }
                CountScreen(vm = countVm, expectedItems = expectedItems)
            }
            composable(AppDestinations.InOut.route) {
                val inOutScope = rememberCoroutineScope()
                val inOutVm = remember {
                    InOutViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        stockCommandRepo = stockCommandRepo,
                        deviceId = "r6-device",
                        newCommandId = { "cmd-${System.currentTimeMillis()}" },
                        scope = inOutScope,
                    )
                }
                InOutScreen(vm = inOutVm)
            }
            composable(AppDestinations.Zones.route) {
                val zoneScope = rememberCoroutineScope()
                val zoneVm = remember {
                    ZoneViewModel(
                        locationRepo = locationRepo,
                        now = { System.currentTimeMillis() },
                        scope = zoneScope,
                    )
                }
                ZoneScreen(vm = zoneVm)
            }
            composable(AppDestinations.Assign.route) {
                val assignScope = rememberCoroutineScope()
                val assignVm = remember {
                    AssignTagViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        deviceId = "r6-device",
                        now = { System.currentTimeMillis() },
                        scope = assignScope,
                    )
                }
                AssignTagScreen(vm = assignVm, products = products)
            }
            composable(AppDestinations.Putaway.route) {
                val putawayScope = rememberCoroutineScope()
                val putawayVm = remember {
                    PutawayViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        txRepo = txRepo,
                        deviceId = "r6-device",
                        now = { System.currentTimeMillis() },
                        scope = putawayScope,
                    )
                }
                PutawayScreen(vm = putawayVm)
            }
        }
    }
}
