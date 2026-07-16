package com.example.koistock.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.koistock.data.model.LocationNode
import com.example.koistock.data.model.LocationType
import com.example.koistock.data.model.Product
import com.example.koistock.data.model.TagMapping
import com.example.koistock.data.model.Transaction
import com.example.koistock.data.model.TrackingMode
import com.example.koistock.data.model.TxType
import com.example.koistock.data.remote.CommitStockResult
import com.example.koistock.data.remote.LocationRepo
import com.example.koistock.data.remote.ProductRepo
import com.example.koistock.data.remote.StockCommandRepo
import com.example.koistock.data.remote.StockMovement
import com.example.koistock.data.remote.TagRepo
import com.example.koistock.data.remote.TransactionRepo
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    vm: ConnectionViewModel,
    reader: RfidReader,
) {
    val navController = rememberNavController()
    val state by vm.state.collectAsState()
    val batteryPercent by vm.batteryPercent.collectAsState()

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
                val tagRepo = remember { DemoTagRepo() }
                val productRepo = remember { DemoProductRepo() }
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
                val tagRepo = remember {
                    DemoTagRepo(
                        mutableMapOf(
                            "KOI-SKU1-1" to TagMapping("KOI-SKU1-1", "SKU1", locationCode = "A-03"),
                            "KOI-SKU1-2" to TagMapping("KOI-SKU1-2", "SKU1", locationCode = "A-03"),
                            "KOI-SKU2-1" to TagMapping("KOI-SKU2-1", "SKU2", locationCode = "B-01"),
                        ),
                    )
                }
                val productRepo = remember { DemoProductRepo() }
                val countVm = remember {
                    CountViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        txRepo = DemoTransactionRepo(),
                        deviceId = "demo-device",
                        now = { 100L },
                        scope = countScope,
                    )
                }
                CountScreen(vm = countVm, expectedItems = demoExpectedItems())
            }
            composable(AppDestinations.InOut.route) {
                val inOutScope = rememberCoroutineScope()
                val tagRepo = remember {
                    DemoTagRepo(
                        mutableMapOf(
                            "KOI-SKU2-1" to TagMapping("KOI-SKU2-1", "SKU2", locationCode = "B-01"),
                            "KOI-SKU2-2" to TagMapping("KOI-SKU2-2", "SKU2", locationCode = "B-01"),
                        ),
                    )
                }
                val inOutVm = remember {
                    InOutViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        stockCommandRepo = DemoStockCommandRepo(),
                        deviceId = "demo-device",
                        newCommandId = { "cmd-demo-1" },
                        scope = inOutScope,
                    )
                }
                InOutScreen(vm = inOutVm)
            }
            composable(AppDestinations.Zones.route) {
                val zoneScope = rememberCoroutineScope()
                val zoneVm = remember {
                    ZoneViewModel(
                        locationRepo = DemoLocationRepo(),
                        now = { 100L },
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
                val tagRepo = remember { DemoTagRepo() }
                val productRepo = remember { DemoProductRepo() }
                val assignVm = remember {
                    AssignTagViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = productRepo,
                        deviceId = "demo-device",
                        now = { 100L },
                        scope = assignScope,
                    )
                }
                AssignTagScreen(vm = assignVm, products = demoProducts())
            }
            composable(AppDestinations.Putaway.route) {
                val putawayScope = rememberCoroutineScope()
                val tagRepo = remember {
                    DemoTagRepo(
                        mutableMapOf(
                            "KOI-SKU1-1" to TagMapping("KOI-SKU1-1", "SKU1", locationCode = "A-01"),
                        ),
                    )
                }
                val putawayVm = remember {
                    PutawayViewModel(
                        reader = reader,
                        tagRepo = tagRepo,
                        productRepo = DemoProductRepo(),
                        txRepo = DemoTransactionRepo(),
                        deviceId = "demo-device",
                        now = { 100L },
                        scope = putawayScope,
                    )
                }
                PutawayScreen(vm = putawayVm)
            }
            composable(AppDestinations.Sync.route) {
                PlaceholderFeatureScreen(
                    title = "Đồng bộ kho",
                    summary = "Sẽ trigger gateway/Airflow để reconcile Firestore và Google Sheet theo snapshot-first algorithm.",
                    readiness = listOf(
                        "Đã có route product-level trong app",
                        "Đã có spec và plan Airflow sync",
                        "Đã tách hướng bổ sung google-services sau",
                    ),
                    nextStep = "Thêm gateway client, SyncViewModel và backend Airflow khi credential sẵn sàng theo Plan 4.",
                )
            }
        }
    }
}

private fun demoExpectedItems(): List<ExpectedItem> = listOf(
    ExpectedItem("SKU1", "Cá KOI Showa", 2, "A-03"),
    ExpectedItem("SKU2", "Cá KOI Sanke", 1, "B-01"),
)

private fun demoProducts(): List<Product> = listOf(
    Product(
        sku = "SKU1",
        name = "Cá KOI Showa",
        unit = "con",
        trackingMode = TrackingMode.SERIALIZED,
        quantity = 3,
        locationCode = "A-03",
    ),
    Product(
        sku = "SKU2",
        name = "Cá KOI Sanke",
        unit = "con",
        trackingMode = TrackingMode.BULK,
        quantity = 5,
        locationCode = "B-01",
    ),
)

private class DemoProductRepo : ProductRepo {
    private val items = demoProducts().associateBy { it.sku }

    override suspend fun getBySku(sku: String): Product? = items[sku]

    override fun observeAll(): Flow<List<Product>> = MutableStateFlow(items.values.toList())

    override suspend fun upsert(product: Product) = Unit
}

private class DemoTagRepo(
    private val items: MutableMap<String, TagMapping> = mutableMapOf(
        "KOI-SKU1-1" to TagMapping("KOI-SKU1-1", "SKU1", locationCode = "A-03"),
    ),
) : TagRepo {
    override suspend fun getByEpc(epc: String): TagMapping? = items[epc]

    override suspend fun upsert(tag: TagMapping) {
        items[tag.epc] = tag
    }

    override suspend fun listBySku(sku: String): List<TagMapping> {
        return items.values.filter { it.sku == sku }
    }
}

private class DemoTransactionRepo : TransactionRepo {
    override suspend fun append(transaction: Transaction) = Unit
}

private class DemoLocationRepo : LocationRepo {
    private val flow = MutableStateFlow(
        listOf(
            LocationNode("A", "Khu A", LocationType.ZONE),
            LocationNode("A-03", "Kệ 03", LocationType.SHELF, parent = "A"),
        ),
    )

    override fun observeAll(): Flow<List<LocationNode>> = flow

    override suspend fun upsert(location: LocationNode) {
        flow.value = flow.value.filterNot { it.code == location.code } + location
    }
}

private class DemoStockCommandRepo : StockCommandRepo {
    override suspend fun commit(
        commandId: String,
        type: TxType,
        deviceId: String,
        movements: List<StockMovement>,
    ): CommitStockResult = CommitStockResult.Success(commandId)
}
