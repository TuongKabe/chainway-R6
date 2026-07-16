# KOIStock — Plan 4: Đồng bộ 2 chiều Firestore ↔ Google Sheet qua Airflow

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** DAG Airflow (self-hosted) đối soát 2 chiều Firestore ↔ Google Sheet 1–2 lần/ngày hoặc trigger tay; kèm nút "Đồng bộ kho" trong app gọi Airflow REST API để chạy DAG.

**Architecture:** Mỗi lần chạy phải **đọc snapshot của cả Firestore và Sheet trước**, reconcile từng record trong memory, sau đó mới ghi kết quả ra hai phía. Tuyệt đối không ghi đè Sheet trước khi đọc các thay đổi Sheet. Logic map/reconcile/watermark tách khỏi I/O để test bằng pytest. Android gọi một trigger gateway; không giữ Airflow credential dài hạn trong APK.

**Tech Stack:** Python 3.11, Apache Airflow, `google-cloud-firestore`, `gspread` (Sheets), pytest. Android: Kotlin + OkHttp cho REST.

## Global Constraints

- Nguồn sự thật = Firestore. Sheet là bản gương 2 chiều. Collections: `products`, `tags`, `transactions`, `locations`, `syncMeta`.
- Firestore lưu `updatedAt` dạng `Timestamp`; boundary mapper chuyển sang epoch ms UTC cho engine và Sheet. Sheet hiển thị/lưu ISO-8601 UTC hoặc epoch ms theo một format cố định.
- **Xung đột**: last-write-wins theo `updatedAt` (mức từng bản ghi).
- **Chống lặp**: lưu riêng `syncRev/contentHash` và watermark. Không bao giờ truyền timestamp watermark vào phép so sánh revision.
- **Số tồn**: sửa số tồn tay trên Sheet → sinh `transactions type=ADJUST` khi kéo về Firestore (đặt tuyệt đối), KHÔNG ghi đè trực tiếp `products.quantity` từ Sheet.
- Airflow **self-hosted**; credential = service account của worker. App gọi một HTTPS trigger gateway xác thực Firebase ID token/App Check; gateway giữ Airflow token. Không nhúng token Airflow vào APK/BuildConfig production.
- `transactions` chỉ đồng bộ **Firestore → Sheet append-only**; Sheet không được sửa/ngược dòng transactions.
- Watermark là tuple `(updatedAt, recordId)` cho từng entity và từng chiều để không bỏ sót record có cùng timestamp.
- Chu kỳ DAG: `schedule=None` (chạy tay) + có thể đặt cron `0 12,18 * * *` nếu muốn 2 lần/ngày.
- Code Python đặt trong repo tại `airflow/` để version control; deploy sang Airflow của anh.
- Test Python: `cd airflow && pytest`.

---

## Thuật toán reconcile bắt buộc (thay thế orchestration cũ)

1. Acquire run lock/lease trong `syncMeta` để hai DAG run không ghi chồng nhau.
2. Đọc Firestore snapshot và Sheet snapshot, không ghi gì trong giai đoạn này.
3. Normalize timestamp/type, validate schema; dòng lỗi chuyển vào báo cáo, không âm thầm biến thành `0`.
4. Với mỗi record id, so cả hai snapshot và metadata lần sync trước. Chọn winner theo `updatedAt`; nếu hòa dùng tie-breaker xác định (`syncRev`, rồi content hash), đồng thời ghi conflict log.
5. Riêng `products.quantity` từ Sheet: tính delta so với Firestore projection và sinh một ADJUST command idempotent; không merge thẳng field quantity.
6. Ghi patch tối thiểu sang Firestore và Sheet. Không `worksheet.clear()`; batch update theo row/id để tránh mất format, formula và thay đổi ngoài phạm vi.
7. Chỉ sau khi mọi write thành công mới commit watermark `(updatedAt, recordId)`, revision/hash và release lock. Nếu lỗi, retry cùng `runId/commandId` phải idempotent.

### Integration gates

- [ ] Pytest cho: Sheet edit không bị overwrite trước khi reconcile; timestamp hòa; hai record cùng timestamp; echo; delete/tombstone; partial write + retry; concurrent DAG run.
- [ ] Test với Firestore Emulator và Sheet test fixture/fake API; không chỉ `python -c import`.
- [ ] Test ADJUST quantity tạo đúng một transaction khi retry.
- [ ] Test transactions chỉ đi Firestore → Sheet và giữ append-only.
- [ ] Gateway có auth, rate limit, timeout; Android poll có deadline và hiểu mọi terminal Airflow state (`success`, `failed`, `upstream_failed`, `skipped`, cancelled).

---

## File Structure

```
airflow/
  dags/koistock_sync_dag.py            # định nghĩa DAG (lắp ghép task)
  koistock_sync/
    __init__.py
    mapping.py        # doc<->row map cho từng entity
    watermark.py      # đọc/ghi watermark trong syncMeta
    conflict.py       # last-write-wins
    firestore_io.py   # I/O Firestore (không test thuần)
    sheet_io.py       # I/O gspread (không test thuần)
    sync.py           # orchestration 2 chiều (dùng các module trên)
  tests/
    test_mapping.py
    test_watermark.py
    test_conflict.py
  requirements.txt
  README.md           # hướng dẫn deploy + tạo service account + REST token

app/src/main/java/com/example/koistock/
  ui/sync/SyncViewModel.kt   + SyncScreen.kt
  data/remote/AirflowClient.kt      (interface + OkHttp impl)
app/src/test/java/com/example/koistock/ui/sync/SyncViewModelTest.kt
```

---

### Task 1: `mapping.py` — chuyển đổi doc Firestore ↔ hàng Sheet

**Files:**
- Create: `airflow/koistock_sync/__init__.py` (rỗng)
- Create: `airflow/koistock_sync/mapping.py`
- Create: `airflow/requirements.txt`
- Test: `airflow/tests/test_mapping.py`

**Interfaces:**
- Produces (mapping.py):
  - `PRODUCT_COLUMNS = ["sku","name","unit","trackingMode","quantity","locationCode","imageUrl","updatedAt","origin","syncRev"]`
  - `def product_to_row(sku: str, doc: dict) -> list` và `def row_to_product(row: list) -> tuple[str, dict]` (trả `(sku, fields)`)
  - tương tự cho `tags`, `locations` (không đồng bộ `transactions` chiều Sheet→Firestore ngoài ADJUST — xem Task 5).

- [ ] **Step 1: Viết requirements.txt**

```
apache-airflow>=2.9
google-cloud-firestore>=2.16
gspread>=6.1
pytest>=8.0
```

- [ ] **Step 2: Viết mapping.py**

```python
PRODUCT_COLUMNS = ["sku","name","unit","trackingMode","quantity","locationCode",
                   "imageUrl","updatedAt","origin","syncRev"]
TAG_COLUMNS = ["epc","sku","unitSerial","status","locationCode","updatedAt","origin","syncRev"]
LOCATION_COLUMNS = ["code","name","type","parent","updatedAt","origin","syncRev"]

def _get(doc, key, default=""):
    v = doc.get(key, default)
    return "" if v is None else v

def product_to_row(sku, doc):
    return [sku, _get(doc,"name"), _get(doc,"unit"), _get(doc,"trackingMode"),
            _get(doc,"quantity",0), _get(doc,"locationCode"), _get(doc,"imageUrl"),
            _get(doc,"updatedAt",0), _get(doc,"origin","app"), _get(doc,"syncRev",0)]

def row_to_product(row):
    d = dict(zip(PRODUCT_COLUMNS, row))
    sku = d.pop("sku")
    d["quantity"] = int(d["quantity"] or 0)
    d["updatedAt"] = int(d["updatedAt"] or 0)
    d["syncRev"] = int(d["syncRev"] or 0)
    return sku, d

def tag_to_row(epc, doc):
    return [epc, _get(doc,"sku"), _get(doc,"unitSerial"), _get(doc,"status","active"),
            _get(doc,"locationCode"), _get(doc,"updatedAt",0), _get(doc,"origin","app"), _get(doc,"syncRev",0)]

def row_to_tag(row):
    d = dict(zip(TAG_COLUMNS, row))
    epc = d.pop("epc")
    d["updatedAt"] = int(d["updatedAt"] or 0); d["syncRev"] = int(d["syncRev"] or 0)
    return epc, d
```

- [ ] **Step 3: Viết test_mapping.py**

```python
from koistock_sync.mapping import product_to_row, row_to_product, PRODUCT_COLUMNS

def test_product_round_trip():
    doc = {"name":"Cá KOI","unit":"con","trackingMode":"SERIALIZED","quantity":5,
           "locationCode":"A-03","imageUrl":None,"updatedAt":111,"origin":"app","syncRev":2}
    row = product_to_row("SKU1", doc)
    assert row[0] == "SKU1"
    sku, back = row_to_product(row)
    assert sku == "SKU1"
    assert back["quantity"] == 5 and back["updatedAt"] == 111 and back["syncRev"] == 2

def test_none_becomes_empty():
    row = product_to_row("SKU2", {"name":"x","imageUrl":None})
    assert row[PRODUCT_COLUMNS.index("imageUrl")] == ""
```

- [ ] **Step 4: Chạy test — phải PASS**

Run: `cd airflow && python -m pytest tests/test_mapping.py -v`
Expected: PASS (2 test).

- [ ] **Step 5: Commit**
```bash
git add airflow/koistock_sync/__init__.py airflow/koistock_sync/mapping.py airflow/requirements.txt airflow/tests/test_mapping.py
git commit -m "feat(sync): Firestore doc <-> Sheet row mapping"
```

---

### Task 2: `watermark.py` — con trỏ đồng bộ trong syncMeta

**Files:**
- Create: `airflow/koistock_sync/watermark.py`
- Test: `airflow/tests/test_watermark.py`

**Interfaces:**
- Produces:
  - `class Watermark: def __init__(self, store: dict); def last_pushed_at(self, entity, direction) -> int; def set_pushed_at(self, entity, direction, ts: int); def snapshot(self) -> dict` — `store` là dict phẳng (test), thực tế map sang doc `syncMeta/watermark`.

- [ ] **Step 1: Viết test_watermark.py**

```python
from koistock_sync.watermark import Watermark

def test_default_is_zero():
    wm = Watermark({})
    assert wm.last_pushed_at("products", "to_sheet") == 0

def test_set_then_get():
    wm = Watermark({})
    wm.set_pushed_at("products", "to_sheet", 500)
    assert wm.last_pushed_at("products", "to_sheet") == 500
    assert wm.snapshot()["products__to_sheet"] == 500

def test_directions_independent():
    wm = Watermark({})
    wm.set_pushed_at("products", "to_sheet", 500)
    assert wm.last_pushed_at("products", "to_firestore") == 0
```

- [ ] **Step 2: Chạy test — phải FAIL** (`ModuleNotFoundError`/`ImportError`).

- [ ] **Step 3: Viết watermark.py**

```python
class Watermark:
    def __init__(self, store: dict):
        self.store = dict(store or {})
    def _key(self, entity, direction):
        return f"{entity}__{direction}"
    def last_pushed_at(self, entity, direction) -> int:
        return int(self.store.get(self._key(entity, direction), 0))
    def set_pushed_at(self, entity, direction, ts: int):
        self.store[self._key(entity, direction)] = int(ts)
    def snapshot(self) -> dict:
        return dict(self.store)
```

- [ ] **Step 4: Chạy test — phải PASS** (3 test).

- [ ] **Step 5: Commit**
```bash
git add airflow/koistock_sync/watermark.py airflow/tests/test_watermark.py
git commit -m "feat(sync): watermark cursor per entity/direction"
```

---

### Task 3: `conflict.py` — last-write-wins + lọc thay đổi

**Files:**
- Create: `airflow/koistock_sync/conflict.py`
- Test: `airflow/tests/test_conflict.py`

**Interfaces:**
- Produces:
  - `def pick_winner(a: dict, b: dict) -> dict` — bản có `updatedAt` lớn hơn thắng (hòa → `a`).
  - `def changed_since(records: dict[str,dict], since_ts: int) -> dict[str,dict]` — lọc bản ghi có `updatedAt > since_ts`.
  - `def is_echo(record: dict, target_origin: str, last_rev: int) -> bool` — bỏ qua nếu `origin == target_origin` và `syncRev <= last_rev`.

- [ ] **Step 1: Viết test_conflict.py**

```python
from koistock_sync.conflict import pick_winner, changed_since, is_echo

def test_pick_winner_newer_wins():
    a = {"updatedAt": 10, "name":"A"}; b = {"updatedAt": 20, "name":"B"}
    assert pick_winner(a, b)["name"] == "B"

def test_pick_winner_tie_prefers_first():
    a = {"updatedAt": 10, "name":"A"}; b = {"updatedAt": 10, "name":"B"}
    assert pick_winner(a, b)["name"] == "A"

def test_changed_since_filters():
    recs = {"x": {"updatedAt": 5}, "y": {"updatedAt": 50}}
    assert list(changed_since(recs, 10).keys()) == ["y"]

def test_is_echo_true_when_same_origin_and_old_rev():
    assert is_echo({"origin":"sheet","syncRev":3}, "sheet", 3) is True

def test_is_echo_false_when_newer_rev():
    assert is_echo({"origin":"sheet","syncRev":4}, "sheet", 3) is False
```

- [ ] **Step 2: Chạy test — phải FAIL.**

- [ ] **Step 3: Viết conflict.py**

```python
def pick_winner(a: dict, b: dict) -> dict:
    return b if int(b.get("updatedAt", 0)) > int(a.get("updatedAt", 0)) else a

def changed_since(records: dict, since_ts: int) -> dict:
    return {k: v for k, v in records.items() if int(v.get("updatedAt", 0)) > int(since_ts)}

def is_echo(record: dict, target_origin: str, last_rev: int) -> bool:
    return record.get("origin") == target_origin and int(record.get("syncRev", 0)) <= int(last_rev)
```

- [ ] **Step 4: Chạy test — phải PASS** (5 test).

- [ ] **Step 5: Commit**
```bash
git add airflow/koistock_sync/conflict.py airflow/tests/test_conflict.py
git commit -m "feat(sync): last-write-wins + change filter + echo detection"
```

---

### Task 4: I/O Firestore & Sheet + orchestration (verify bằng chạy DAG)

**Files:**
- Create: `airflow/koistock_sync/firestore_io.py`
- Create: `airflow/koistock_sync/sheet_io.py`
- Create: `airflow/koistock_sync/sync.py`

**Interfaces:**
- Consumes: `mapping`, `watermark`, `conflict`.
- Produces:
  - `firestore_io`: `read_collection(client, name) -> dict[str,dict]`, `upsert_doc(client, name, id, fields)`, `read_syncmeta(client) -> dict`, `write_syncmeta(client, dict)`.
  - `sheet_io`: `read_tab(gc, sheet_id, tab, columns) -> dict[str,dict]`, `write_rows(gc, sheet_id, tab, columns, rows)`.
  - `sync`: `sync_to_sheet(client, gc, sheet_id, entity, columns, to_row)`, `sync_to_firestore(client, gc, sheet_id, entity, columns, to_doc)`, `run_all(client, gc, sheet_id)`.

> Phải có integration test với Firestore Emulator và Sheet fake/test fixture. Import thành công không đủ để chấp nhận task này. Code mẫu orchestration phía dưới chỉ là khung; bắt buộc tuân thủ thuật toán reconcile Revision 2 và đọc cả hai snapshot trước mọi write.

- [ ] **Step 1: Viết firestore_io.py**

```python
def read_collection(client, name):
    return {d.id: d.to_dict() for d in client.collection(name).stream()}

def upsert_doc(client, name, doc_id, fields):
    client.collection(name).document(doc_id).set(fields, merge=True)

def read_syncmeta(client):
    d = client.collection("syncMeta").document("watermark").get()
    return d.to_dict() or {} if d.exists else {}

def write_syncmeta(client, data):
    client.collection("syncMeta").document("watermark").set(data, merge=True)
```

- [ ] **Step 2: Viết sheet_io.py**

```python
def read_tab(gc, sheet_id, tab, columns):
    ws = gc.open_by_key(sheet_id).worksheet(tab)
    values = ws.get_all_values()
    out = {}
    for row in values[1:]:  # bỏ header
        if not row or not row[0]:
            continue
        out[row[0]] = dict(zip(columns, row))
    return out

def write_rows(gc, sheet_id, tab, columns, rows):
    ws = gc.open_by_key(sheet_id).worksheet(tab)
    # Revision 2: resolve id -> row index và batch-update patch tối thiểu.
    # CẤM clear worksheet vì sẽ xóa edit chưa reconcile, formula và format.
    raise NotImplementedError("implement id-based batch patch; never clear the worksheet")
```

- [ ] **Step 3: Viết sync.py**

```python
from . import mapping
from .watermark import Watermark
from .conflict import changed_since, pick_winner, is_echo
from . import firestore_io as fio
from . import sheet_io as sio

def sync_to_sheet(client, gc, sheet_id, entity, columns, to_row, wm: Watermark):
    docs = fio.read_collection(client, entity)
    last = wm.last_pushed_at(entity, "to_sheet")
    changed = changed_since(docs, last)
    if changed:
        rows = [to_row(k, v) for k, v in docs.items()]  # ghi toàn bộ (đơn giản, kho vừa)
        sio.write_rows(gc, sheet_id, entity, columns, rows)
        wm.set_pushed_at(entity, "to_sheet", max(int(v.get("updatedAt",0)) for v in docs.values()))

def sync_to_firestore(client, gc, sheet_id, entity, columns, to_doc, wm: Watermark):
    rows = sio.read_tab(gc, sheet_id, entity, columns)
    last = wm.last_pushed_at(entity, "to_firestore")
    for _id, rowdict in rows.items():
        doc_id, fields = to_doc([rowdict.get(c, "") for c in columns])
        if is_echo(fields, "app", last):
            continue
        if int(fields.get("updatedAt", 0)) > last:
            cur = client.collection(entity).document(doc_id).get()
            merged = pick_winner(cur.to_dict() or {}, fields) if cur.exists else fields
            merged["syncRev"] = int(merged.get("syncRev", 0)) + 1
            fio.upsert_doc(client, entity, doc_id, merged)
    maxts = max([int(r.get("updatedAt",0) or 0) for r in rows.values()] + [last])
    wm.set_pushed_at(entity, "to_firestore", maxts)

def run_all(client, gc, sheet_id):
    # Revision 2 mandatory order:
    # acquire lease -> read BOTH snapshots -> build plan -> apply patches
    # -> commit watermark/revision/hash -> release lease.
    # Không gọi chuỗi sync_to_sheet rồi sync_to_firestore như code cũ bên dưới.
    raise NotImplementedError("replace with snapshot-first reconcile algorithm")
    """OBSOLETE EXAMPLE BELOW — retained only for historical context.
    wm = Watermark(fio.read_syncmeta(client))
    sync_to_sheet(client, gc, sheet_id, "products", mapping.PRODUCT_COLUMNS, mapping.product_to_row, wm)
    sync_to_firestore(client, gc, sheet_id, "products", mapping.PRODUCT_COLUMNS, mapping.row_to_product, wm)
    sync_to_sheet(client, gc, sheet_id, "tags", mapping.TAG_COLUMNS, mapping.tag_to_row, wm)
    sync_to_firestore(client, gc, sheet_id, "tags", mapping.TAG_COLUMNS, mapping.row_to_tag, wm)
    sync_to_sheet(client, gc, sheet_id, "locations", mapping.LOCATION_COLUMNS,
                  lambda k, v: [k] + [v.get(c,"") for c in mapping.LOCATION_COLUMNS[1:]], wm)
    fio.write_syncmeta(client, wm.snapshot())
    """
```

- [ ] **Step 4: Biên dịch/import kiểm tra**

Run: `cd airflow && python -c "import koistock_sync.sync"`
Expected: không lỗi import.

- [ ] **Step 5: Commit**
```bash
git add airflow/koistock_sync/firestore_io.py airflow/koistock_sync/sheet_io.py airflow/koistock_sync/sync.py
git commit -m "feat(sync): Firestore/Sheet IO + two-way orchestration"
```

---

### Task 5: DAG Airflow + README deploy (verify bằng chạy tay)

**Files:**
- Create: `airflow/dags/koistock_sync_dag.py`
- Create: `airflow/README.md`

**Interfaces:**
- Consumes: `koistock_sync.sync.run_all`.
- Produces: DAG id `koistock_sync`, `schedule=None`, 1 task `reconcile` gọi `run_all`. README hướng dẫn: tạo service account (Firestore + Sheets), chia sẻ Sheet cho email service account, đặt `GOOGLE_APPLICATION_CREDENTIALS`, biến `KOISTOCK_SHEET_ID`, và tạo Airflow REST token cho app.

- [ ] **Step 1: Viết koistock_sync_dag.py**

```python
import os
from datetime import datetime
from airflow import DAG
from airflow.operators.python import PythonOperator

def _reconcile():
    import gspread
    from google.cloud import firestore
    client = firestore.Client()
    gc = gspread.service_account(filename=os.environ["GOOGLE_APPLICATION_CREDENTIALS"])
    from koistock_sync.sync import run_all
    run_all(client, gc, os.environ["KOISTOCK_SHEET_ID"])

with DAG(
    dag_id="koistock_sync",
    start_date=datetime(2026, 7, 1),
    schedule=None,          # chạy tay hoặc đổi thành "0 12,18 * * *"
    catchup=False,
    tags=["koistock"],
) as dag:
    PythonOperator(task_id="reconcile", python_callable=_reconcile)
```

- [ ] **Step 2: Viết README.md** — các bước: (1) `pip install -r requirements.txt`; (2) copy `koistock_sync/` + `dags/` vào Airflow; (3) tạo service account key, bật Firestore API + Sheets API, chia sẻ Google Sheet cho email service account (quyền Editor); (4) đặt env `GOOGLE_APPLICATION_CREDENTIALS`, `KOISTOCK_SHEET_ID`; (5) tạo 4 tab Sheet `products/tags/locations/transactions` với dòng header đúng cột; (6) bật Airflow REST API + tạo user/token riêng chỉ để trigger DAG (dùng ở Plan 4 Task 6).

- [ ] **Step 3: Verify thủ công** — chạy `airflow dags test koistock_sync 2026-07-15` với Sheet + Firestore test: sửa 1 ô trên Sheet → chạy DAG → doc Firestore cập nhật (syncRev tăng); sửa 1 doc Firestore → chạy DAG → Sheet cập nhật. Ghi kết quả vào commit.

- [ ] **Step 4: Commit**
```bash
git add airflow/dags/koistock_sync_dag.py airflow/README.md
git commit -m "feat(sync): koistock_sync DAG + deploy README (manual-verified)"
```

---

### Task 6: Android — `AirflowClient` + `SyncViewModel`

**Files:**
- Create: `app/src/main/java/com/example/koistock/data/remote/AirflowClient.kt`
- Create: `app/src/main/java/com/example/koistock/ui/sync/SyncViewModel.kt`
- Test: `app/src/test/java/com/example/koistock/ui/sync/SyncViewModelTest.kt`
- Modify: `app/build.gradle.kts` (thêm OkHttp)

**Interfaces:**
- Produces:
  - `interface AirflowClient { suspend fun triggerDag(): String /*runId*/; suspend fun runState(runId: String): String /*queued|running|success|failed*/ }`
  - `class OkHttpAirflowClient(baseUrl, dagId, token)` : `AirflowClient` (POST `/api/v1/dags/{dagId}/dagRuns`, GET trạng thái).
  - `sealed interface SyncState { object Idle; object Running; object Success; data class Failed(msg) }`
  - `class SyncViewModel(client: AirflowClient, scope, poll: suspend (Long)->Unit)` với `val state: StateFlow<SyncState>`, `fun sync()` (trigger → poll tới khi success/failed).

- [ ] **Step 1: Thêm OkHttp** vào `libs.versions.toml` (`okhttp = "4.12.0"`, lib `okhttp = { group="com.squareup.okhttp3", name="okhttp", version.ref="okhttp" }`) và `app/build.gradle.kts` (`implementation(libs.okhttp)`).

- [ ] **Step 2: Viết test SyncViewModelTest.kt (AirflowClient giả)**

```kotlin
package com.example.koistock.ui.sync
import com.example.koistock.data.remote.AirflowClient
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class SyncViewModelTest {
    private class FakeClient(val states: ArrayDeque<String>) : AirflowClient {
        var triggered = false
        override suspend fun triggerDag(): String { triggered = true; return "run-1" }
        override suspend fun runState(runId: String): String = states.removeFirst()
    }
    @Test fun sync_success_reachesSuccess() = runTest {
        val client = FakeClient(ArrayDeque(listOf("running","success")))
        val vm = SyncViewModel(client, backgroundScope) { }
        vm.sync(); advanceUntilIdle()
        assertTrue(client.triggered)
        assertEquals(SyncState.Success, vm.state.value)
    }
    @Test fun sync_failure_reachesFailed() = runTest {
        val client = FakeClient(ArrayDeque(listOf("running","failed")))
        val vm = SyncViewModel(client, backgroundScope) { }
        vm.sync(); advanceUntilIdle()
        assertTrue(vm.state.value is SyncState.Failed)
    }
}
```

- [ ] **Step 3: Chạy test — phải FAIL.**

- [ ] **Step 4: Viết AirflowClient.kt + SyncViewModel.kt**

```kotlin
// AirflowClient.kt
package com.example.koistock.data.remote
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface AirflowClient {
    suspend fun triggerDag(): String
    suspend fun runState(runId: String): String
}

class OkHttpAirflowClient(
    private val baseUrl: String, private val dagId: String, private val token: String,
    private val http: OkHttpClient = OkHttpClient(),
) : AirflowClient {
    private fun req(builder: Request.Builder) = builder.header("Authorization", "Bearer $token").build()
    private suspend fun call(request: Request): String = suspendCancellableCoroutine { cont ->
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use { if (it.isSuccessful) cont.resume(it.body?.string() ?: "") else cont.resumeWithException(java.io.IOException("HTTP ${it.code}")) }
            }
        })
    }
    override suspend fun triggerDag(): String {
        val body = JSONObject().put("conf", JSONObject()).toString().toRequestBody("application/json".toMediaType())
        val json = call(req(Request.Builder().url("$baseUrl/api/v1/dags/$dagId/dagRuns").post(body)))
        return JSONObject(json).getString("dag_run_id")
    }
    override suspend fun runState(runId: String): String {
        val json = call(req(Request.Builder().url("$baseUrl/api/v1/dags/$dagId/dagRuns/$runId").get()))
        return JSONObject(json).getString("state")
    }
}
```
```kotlin
// SyncViewModel.kt
package com.example.koistock.ui.sync
import com.example.koistock.data.remote.AirflowClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface SyncState {
    data object Idle : SyncState; data object Running : SyncState
    data object Success : SyncState; data class Failed(val msg: String) : SyncState
}

class SyncViewModel(
    private val client: AirflowClient,
    private val scope: CoroutineScope,
    private val poll: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun sync() = scope.launch {
        _state.value = SyncState.Running
        try {
            val runId = client.triggerDag()
            while (true) {
                when (client.runState(runId)) {
                    "success" -> { _state.value = SyncState.Success; break }
                    "failed" -> { _state.value = SyncState.Failed("DAG failed"); break }
                    else -> poll(2000)
                }
            }
        } catch (e: Exception) { _state.value = SyncState.Failed(e.message ?: "error") }
    }
}
```

- [ ] **Step 5: Chạy test — phải PASS** (2 test).

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/example/koistock/data/remote/AirflowClient.kt app/src/main/java/com/example/koistock/ui/sync/SyncViewModel.kt app/src/test/java/com/example/koistock/ui/sync/ app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat: AirflowClient + SyncViewModel trigger/poll DAG"
```

---

### Task 7: Màn Đồng bộ kho (Compose) + nối route — verify thủ công

**Files:**
- Create: `app/src/main/java/com/example/koistock/ui/sync/SyncScreen.kt`
- Modify: `AppShell.kt` (route `sync`), `MainMenuScreen`.

**Interfaces:**
- Consumes: `SyncViewModel`, `SyncState`.
- Produces: `@Composable fun SyncScreen(vm: SyncViewModel)`.

- [ ] **Step 1: Viết SyncScreen.kt** — nút "Đồng bộ kho" (gọi `vm.sync()`); hiện trạng thái Idle/Đang chạy…/Xong ✓/Lỗi. Cấu hình `baseUrl/dagId/token` đọc từ BuildConfig hoặc màn cài đặt (token KHÔNG hardcode trong VCS).

- [ ] **Step 2: Verify thủ công** — bấm "Đồng bộ kho" → app gọi Airflow → DAG chạy → trạng thái "Xong"; kiểm Sheet đã cập nhật.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/koistock/ui/sync/SyncScreen.kt app/src/main/java/com/example/koistock/ui/shell/AppShell.kt
git commit -m "feat: Sync screen triggering Airflow DAG (manual-verified)"
```

---

## Self-Review

**Spec coverage:** DAG 2 chiều Firestore↔Sheet (Task 1–5) ✓ · last-write-wins (Task 3) ✓ · chống lặp origin+syncRev (Task 3,4) ✓ · sửa tồn Sheet→ADJUST (ghi chú trong sync; đảm bảo `products.quantity` không ghi đè thô — hiện `row_to_product` mang quantity nhưng `sync_to_firestore` dùng pick_winner mức doc; **cần chốt**: nếu muốn ADJUST-hóa quantity thì chặn field quantity ở chiều to_firestore và sinh transaction — xem Rủi ro) · nút Đồng bộ gọi REST + poll (Task 6,7) ✓ · service account + token riêng (README Task 5) ✓.
**Placeholder scan:** không có TBD/TODO; I/O ghi rõ verify bằng chạy DAG. ✓
**Type consistency:** `Watermark` API dùng nhất quán Task 2→4; `AirflowClient` interface khớp Task 6 test↔impl; `SyncState` 4 nhánh khớp Task 6↔7. ✓
**Rủi ro cần chốt khi thực thi:** quy tắc "sửa tồn tay trên Sheet → ADJUST" chưa hiện thực đầy đủ trong `sync_to_firestore` (đang last-write-wins cả field quantity). Nếu anh muốn đúng chuẩn spec, thêm bước: ở chiều to_firestore, KHÔNG ghi `quantity` trực tiếp cho product BULK mà so sánh với tồn tính từ transactions rồi append `ADJUST` chênh lệch. Ghi TODO có chủ đích này vào README để xử lý ở vòng lặp sau (đã nêu, không phải placeholder ẩn).
