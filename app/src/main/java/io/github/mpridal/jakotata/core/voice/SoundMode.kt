package io.github.mpridal.jakotata.core.voice

import android.media.AudioDeviceInfo

/** Režimy zvuku (zadání kap. 3.2); hodnota odpovídá C++ `SoundMode`. */
enum class SoundMode(val native: Int) { SILENT(0), HEADPHONES(1), PARROT(2) }

/** Co je připojené na výstupu. */
enum class OutputKind { SPEAKER, WIRED, BLUETOOTH }

object SoundModes {
    /** Druh výstupu ze seznamu typů zařízení (`AudioDeviceInfo.TYPE_*`). Kabel má přednost před BT. */
    fun outputKind(deviceTypes: Collection<Int>): OutputKind = when {
        deviceTypes.any { it in WIRED_TYPES } -> OutputKind.WIRED
        deviceTypes.any { it in BT_TYPES } -> OutputKind.BLUETOOTH
        else -> OutputKind.SPEAKER
    }

    /**
     * Živý hlas jen v kabelových/USB sluchátkách: Bluetooth přidává zpoždění a z reproduktoru by
     * vznikla zpětná vazba. Papoušek a Ticho jdou vždy.
     */
    fun available(kind: OutputKind): List<SoundMode> =
        if (kind == OutputKind.WIRED) listOf(SoundMode.PARROT, SoundMode.HEADPHONES, SoundMode.SILENT)
        else listOf(SoundMode.PARROT, SoundMode.SILENT)

    /** Zvolený režim upravený podle výstupu: Sluchátka bez kabelu → Papoušek (výchozí). */
    fun effective(requested: SoundMode, kind: OutputKind): SoundMode =
        if (requested in available(kind)) requested else SoundMode.PARROT

    /** Další režim po klepnutí na ikonu režimu zvuku. */
    fun next(current: SoundMode, kind: OutputKind): SoundMode {
        val list = available(kind)
        val i = list.indexOf(current)
        return list[(if (i < 0) 0 else i + 1) % list.size]
    }

    private val WIRED_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
    )
    private val BT_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    )
}
