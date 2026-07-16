package com.example.koistock.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.koistock.data.remote.HttpLocationRepository
import com.example.koistock.data.remote.HttpProductRepository
import com.example.koistock.data.remote.HttpStockCommandRepository
import com.example.koistock.data.remote.HttpTagRepository
import com.example.koistock.data.remote.HttpTransactionRepository
import com.example.koistock.data.remote.KoiApiFactory
import com.example.koistock.device.ConnectionState
import com.example.koistock.device.RfidReader
import com.example.koistock.domain.ExpectedItem
import com.example.koistock.ui.assign.AssignTagScreen
import com.example.koistock.ui.assign.AssignTagViewModel
import com.example.koistock.ui.common.PlaceholderFeatureScreen
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
import com.example.koistock.ui.zones.ZoneScreen
import com.example.koistock.ui.zones.ZoneViewModel

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

    LaunchedEffect(Unit) {
        productRepo.refresh()
        locationRepo.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KOIStock") },
                actions = {
                    val label = if (state is ConnectionState.Connected) "● R6" else "○ R6"
                    val batteryLabel = batteryPercent?.let { " $it%" } ?: ""
                    Text("$label$batteryLabel", modifier = Modifier.padding(end = 12.dp))
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
                    onOpen = navController::navigate,
                )
            }
            composable(AppDestinations.Pairing.route) {
                PairingScreen(vm = vm) {
                    navController.navigate(AppDestinations.Dashboard.route)
                }
            }
            composable("menu") {
                MainMenuScreen(onOpen = navController::navigate)
            }
            composable(AppDestinations.Guide.route) {
                ConnectionGuideScreen()
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
                LocateScreen(vm = locateVm, sku = "SKU1")
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
            composable("legacy_lookup_placeholder") {
                PlaceholderFeatureScreen(
                    title = "Tra cứu hàng hóa",
                    summary = "Placeholder cũ đã được thay bằng màn hình thật.",
                    readiness = emptyList(),
                    nextStep = "Không cần dùng route này nữa.",
                )
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
            composable(AppDestinations.Sync.route) {
                PlaceholderFeatureScreen(
                    title = "Đồng bộ kho",
                    summary = "Sẽ trigger gateway/Airflow để reconcile PostgreSQL và Google Sheet theo snapshot-first algorithm.",
                    readiness = listOf(
                        "Đã có backend HTTP trên Koi",
                        "Đã có Airflow sync PostgreSQL ↔ Google Sheet",
                        "App đang chuyển từ demo/Firestore sang API thật",
                    ),
                    nextStep = "Thêm gateway client và SyncViewModel để trigger/reconcile theo backend mới.",
                )
            }
        }
    }
}
