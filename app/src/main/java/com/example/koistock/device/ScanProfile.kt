package com.example.koistock.device

/**
 * Chế độ cò (cò R6 phát sự kiện bóp rời rạc, không có "giữ/thả" tin cậy nên dùng toggle):
 * - [SINGLE] Bóp 1 lần: mỗi lần bóp = một hành động quét đơn (điểm) hoặc một đợt quét ngắn (gom).
 * - [CONTINUOUS] Quét liên tục: bóp lần 1 bắt đầu, bóp lần 2 kết thúc.
 */
enum class TriggerMode { SINGLE, CONTINUOUS }

/** Các chức năng có quét RFID, mỗi chức năng có cấu hình riêng. */
enum class ScanFunction(val key: String, val label: String) {
    LOOKUP("lookup", "Tra cứu"),
    LOCATE("locate", "Tìm sản phẩm"),
    COUNT("count", "Quét theo khu"),
    INOUT("inout", "Nhập / Xuất"),
    PUTAWAY("putaway", "Đặt vị trí"),
    ASSIGN("assign", "Gán tag"),
}

/**
 * Cấu hình quét cho một chức năng: chế độ cò + các thông số Chainway ảnh hưởng độ chính xác.
 *
 * - [session] 0..3 (S0 tốt cho quét đơn/định vị lặp; S1 cho đếm nhiều thẻ).
 * - [q] startQ 0..15 (ít thẻ → Q thấp, nhiều thẻ → Q cao).
 * - [millerM] mã hoá Miller: 0=FM0, 1=M2, 2=M4, 3=M8 (M cao = nhạy/chống nhiễu hơn, chậm hơn).
 */
data class ScanProfile(
    val triggerMode: TriggerMode = TriggerMode.SINGLE,
    val power: Int = 26,
    val session: Int = 0,
    val q: Int = 4,
    val tagFocus: Boolean = false,
    val fastId: Boolean = false,
    val millerM: Int = 0,
) {
    fun sanitized(): ScanProfile = copy(
        power = power.coerceIn(1, 30),
        session = session.coerceIn(0, 3),
        q = q.coerceIn(0, 15),
        millerM = millerM.coerceIn(0, 3),
    )

    companion object {
        /** Mặc định hợp lý theo đặc thù từng chức năng. */
        fun default(function: ScanFunction): ScanProfile = when (function) {
            // Quét đơn 1 thẻ ở gần: bóp 1 lần, công suất thấp để tránh đọc nhầm thẻ lân cận, S0.
            ScanFunction.LOOKUP -> ScanProfile(TriggerMode.SINGLE, power = 16, session = 0, q = 2)
            ScanFunction.ASSIGN -> ScanProfile(TriggerMode.SINGLE, power = 12, session = 0, q = 0)
            // Định vị: quét liên tục (bóp bắt đầu/kết thúc), S0 để thẻ trả lời mọi vòng, TagFocus tắt.
            ScanFunction.LOCATE -> ScanProfile(TriggerMode.CONTINUOUS, power = 26, session = 0, q = 4)
            // Đếm/gom nhiều thẻ: quét liên tục, công suất cao, S1, Q cao.
            ScanFunction.COUNT -> ScanProfile(TriggerMode.CONTINUOUS, power = 30, session = 1, q = 6)
            ScanFunction.INOUT -> ScanProfile(TriggerMode.CONTINUOUS, power = 30, session = 1, q = 6)
            ScanFunction.PUTAWAY -> ScanProfile(TriggerMode.CONTINUOUS, power = 26, session = 1, q = 4)
        }
    }
}
