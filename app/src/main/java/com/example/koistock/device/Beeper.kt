package com.example.koistock.device

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Phát tiếng báo qua LOA ĐIỆN THOẠI (không dùng buzzer của R6).
 *
 * Lý do: khi định vị phải tắt buzzer phần cứng (`setBeep(false)`) vì nó kêu với MỌI thẻ đọc được,
 * không phân biệt được tag mục tiêu — nhưng lệnh đó cũng tắt luôn `triggerBeep` của reader.
 * Demo hãng cũng phát tiếng bằng loa điện thoại (SoundPool) vì lý do này.
 */
interface Beeper {
    fun beep()
    fun release()

    /** Dùng cho test / khi không cần tiếng. */
    object NoOp : Beeper {
        override fun beep() = Unit
        override fun release() = Unit
    }
}

class ToneBeeper(
    private val volumePercent: Int = 100,
    private val toneDurationMs: Int = 40,
) : Beeper {
    private var generator: ToneGenerator? = null

    private fun obtain(): ToneGenerator? {
        if (generator == null) {
            generator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, volumePercent) }.getOrNull()
        }
        return generator
    }

    override fun beep() {
        runCatching { obtain()?.startTone(ToneGenerator.TONE_PROP_BEEP, toneDurationMs) }
    }

    override fun release() {
        runCatching { generator?.release() }
        generator = null
    }
}
