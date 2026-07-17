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
import androidx.compose.runtime.DisposableEffect
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
import com.example.koistock.data.remote.HttpSyncRepository
import com.example.koistock.data.remote.HttpTagRepository
import com.example.koistock.data.remote.HttpTransactionRepository
import com.example.koistock.data.remote.WarehouseSyncCoordinator
import com.example.koistock.data.remote.WarehouseSyncResult
import com.example.koistock.data.remote.KoiApiConfig
import com.example.koistock.data.remote.KoiApiFactory
import com.example.koistock.device.RfidReader
import com.example.koistock.device.ToneBeeper
import com.example.koistock.device.ScanFunction
import com.example.koistock.device.ScanProfile
import com.example.koistock.device.ScanProfileStore
import com.example.koistock.domain.ExpectedItem
import com.example.koistock.ui.settings.ScanConfigScreen
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
import com.example.koistock.ui.zones.ZoneViewModel
import com.example.koistock.ui.warehouse.ProductManagementViewModel
import com.example.koistock.ui.warehouse.WarehouseManagementScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    vm: ConnectionViewModel,
    reader: RfidReader,
    scanProfileStore: ScanProfileStore,
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
    val syncRepo = remember { HttpSyncRepository(api) }
    val products by productRepo.observeAll().collectAsState(initial = emptyList())
    val locations by locationRepo.observeAll().collectAsState(initial = emptyList())
    val warehouseSync = remember {
        WarehouseSyncCoordinator(
            reconcile = syncRepo::reconcile,
            refreshProducts = productRepo::refresh,
            refreshLocations = locationRepo::refresh,
        )
    }
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
            val outcome = warehouseSync.syncAndRefresh()
            if (state is com.example.koistock.device.ConnectionState.Connected) {
                vm.refreshBattery()
            }
            isSyncing = false
            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val message = when (outcome) {
                WarehouseSyncResult.Success -> "Đồng bộ dữ liệu kho xong lúc $now"
                is WarehouseSyncResult.SavedButSyncFailed -> "Dữ liệu backend đã tải, Google Sheet lỗi: ${outcome.message}"
                is WarehouseSyncResult.LoadFailed -> "Không tải lại được dữ liệu kho: ${outcome.message}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        runSync()
    }

    // Tự làm mới dữ liệu mỗi khi đầu đọc vừa kết nối.
    val isReaderConnected = state is com.example.koistock.device.ConnectionState.Connected
    LaunchedEffect(isReaderConnected) {
        if (isReaderConnected) {
            runCatching {
                productRepo.refresh()
                locationRepo.refresh()
            }
        }
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
            composable(AppDestinations.ScanConfigRoutePattern) { backStackEntry ->
                val key = backStackEntry.arguments?.getString(AppDestinations.ScanConfigArg)
                val function = ScanFunction.entries.firstOrNull { it.key == key } ?: ScanFunction.LOOKUP
                val profile by scanProfileStore.profile(function)
                    .collectAsState(initial = ScanProfile.default(function))
                ScanConfigScreen(
                    function = function,
                    profile = profile,
                    onSave = { shellScope.launch { scanProfileStore.save(function, it) } },
                    onResetDefault = { shellScope.launch { scanProfileStore.reset(function) } },
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
                val profile by scanProfileStore.profile(ScanFunction.LOOKUP)
                    .collectAsState(initial = ScanProfile.default(ScanFunction.LOOKUP))
                val lookupVm = remember(profile) {
                    LookupViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        scope = lookupScope,
                        profile = profile,
                    )
                }
                LookupScreen(
                    vm = lookupVm,
                    onAssign = { navController.navigate(AppDestinations.Assign.route) },
                )
            }
            composable(AppDestinations.Locate.route) {
                val locateScope = rememberCoroutineScope()
                val profile by scanProfileStore.profile(ScanFunction.LOCATE)
                    .collectAsState(initial = ScanProfile.default(ScanFunction.LOCATE))
                // Tiếng báo phát qua loa điện thoại (buzzer R6 bị tắt khi dò).
                val beeper = remember { ToneBeeper() }
                DisposableEffect(beeper) {
                    onDispose { beeper.release() }
                }
                val locateVm = remember(profile) {
                    LocateViewModel(
                        reader = reader,
                        scope = locateScope,
                        profile = profile,
                        beeper = beeper,
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
                val profile by scanProfileStore.profile(ScanFunction.COUNT)
                    .collectAsState(initial = ScanProfile.default(ScanFunction.COUNT))
                val countVm = remember(profile) {
                    CountViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        txRepo = txRepo,
                        deviceId = "r6-device",
                        now = { System.currentTimeMillis() },
                        scope = countScope,
                        profile = profile,
                    )
                }
                CountScreen(vm = countVm, expectedItems = expectedItems)
            }
            composable(AppDestinations.InOut.route) {
                val inOutScope = rememberCoroutineScope()
                val profile by scanProfileStore.profile(ScanFunction.INOUT)
                    .collectAsState(initial = ScanProfile.default(ScanFunction.INOUT))
                val inOutVm = remember(profile) {
                    InOutViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        stockCommandRepo = stockCommandRepo,
                        deviceId = "r6-device",
                        newCommandId = { "cmd-${System.currentTimeMillis()}" },
                        scope = inOutScope,
                        profile = profile,
                    )
                }
                InOutScreen(vm = inOutVm)
            }
            composable(AppDestinations.Warehouse.route) {
                val zoneScope = rememberCoroutineScope()
                val productManagementVm = remember {
                    ProductManagementViewModel(
                        productRepo = productRepo,
                        locationRepo = locationRepo,
                        syncAfterSave = warehouseSync::syncAndRefresh,
                        scope = zoneScope,
                    )
                }
                val zoneVm = remember {
                    ZoneViewModel(
                        locationRepo = locationRepo,
                        now = { System.currentTimeMillis() },
                        scope = zoneScope,
                        syncAfterSave = warehouseSync::syncAndRefresh,
                    )
                }
                WarehouseManagementScreen(productVm = productManagementVm, zoneVm = zoneVm)
            }
            composable(AppDestinations.Assign.route) {
                val assignScope = rememberCoroutineScope()
                val profile by scanProfileStore.profile(ScanFunction.ASSIGN)
                    .collectAsState(initial = ScanProfile.default(ScanFunction.ASSIGN))
                val assignVm = remember(profile) {
                    AssignTagViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        deviceId = "r6-device",
                        now = { System.currentTimeMillis() },
                        scope = assignScope,
                        profile = profile,
                    )
                }
                AssignTagScreen(vm = assignVm, products = products)
            }
            composable(AppDestinations.Putaway.route) {
                val putawayScope = rememberCoroutineScope()
                val profile by scanProfileStore.profile(ScanFunction.PUTAWAY)
                    .collectAsState(initial = ScanProfile.default(ScanFunction.PUTAWAY))
                val putawayVm = remember(profile) {
                    PutawayViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        txRepo = txRepo,
                        deviceId = "r6-device",
                        now = { System.currentTimeMillis() },
                        scope = putawayScope,
                        profile = profile,
                    )
                }
                PutawayScreen(vm = putawayVm, locations = locations)
            }
        }
    }
}
