package io.github.mpridal.jakotata.core.voice

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundModesTest {
    @Test fun wiredWinsOverBluetooth() {
        assertEquals(OutputKind.WIRED, SoundModes.outputKind(listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_WIRED_HEADSET)))
        assertEquals(OutputKind.BLUETOOTH, SoundModes.outputKind(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertEquals(OutputKind.SPEAKER, SoundModes.outputKind(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
    }

    @Test fun liveHeadphonesOnlyWithCable() {
        assertEquals(SoundMode.HEADPHONES, SoundModes.effective(SoundMode.HEADPHONES, OutputKind.WIRED))
        assertEquals(SoundMode.PARROT, SoundModes.effective(SoundMode.HEADPHONES, OutputKind.BLUETOOTH))
        assertEquals(SoundMode.PARROT, SoundModes.effective(SoundMode.HEADPHONES, OutputKind.SPEAKER))
        assertEquals(SoundMode.SILENT, SoundModes.effective(SoundMode.SILENT, OutputKind.SPEAKER))
    }

    @Test fun cyclesThroughAvailableModes() {
        assertEquals(SoundMode.SILENT, SoundModes.next(SoundMode.PARROT, OutputKind.SPEAKER))
        assertEquals(SoundMode.PARROT, SoundModes.next(SoundMode.SILENT, OutputKind.SPEAKER))
        assertEquals(SoundMode.HEADPHONES, SoundModes.next(SoundMode.PARROT, OutputKind.WIRED))
        assertEquals(SoundMode.PARROT, SoundModes.next(SoundMode.HEADPHONES, OutputKind.BLUETOOTH))
    }
}
