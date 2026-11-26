package com.example.pkmapp.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ForestSoundEngineTest {

    @Test
    fun synthesizeFrame_generatesBoundedNonSilentAudioForEachSound() {
        val silence = ShortArray(1024)
        ForestSoundEngine.synthesizeFrame(
            ForestSoundEngine.AmbientSound.NONE,
            silence,
            ForestSoundEngine.SynthState(42L),
            0.7f
        )
        assertTrue(silence.all { it == 0.toShort() })

        val sounds = listOf(
            ForestSoundEngine.AmbientSound.RAIN,
            ForestSoundEngine.AmbientSound.FIRE,
            ForestSoundEngine.AmbientSound.WIND
        )
        for (sound in sounds) {
            val state = ForestSoundEngine.SynthState(12345L)
            val buf = ShortArray(2048)
            // Warm up fade-in over a few frames
            repeat(5) {
                ForestSoundEngine.synthesizeFrame(sound, buf, state, 0.65f)
            }
            var maxAbs = 0
            for (sample in buf) {
                val a = abs(sample.toInt())
                if (a > maxAbs) maxAbs = a
            }
            assertTrue("Expected audible signal for $sound, peak=$maxAbs", maxAbs > 50)
            assertTrue("Expected bounded PCM for $sound, peak=$maxAbs", maxAbs <= 32000)
        }
        assertEquals(ForestSoundEngine.AmbientSound.RAIN, ForestSoundEngine.AmbientSound.fromId("rain"))
        assertEquals(ForestSoundEngine.AmbientSound.NONE, ForestSoundEngine.AmbientSound.fromId("unknown"))
    }
}
