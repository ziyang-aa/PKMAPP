package com.example.pkmapp.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationKotlinInteropTest {

    @Test
    fun bottomBarDestinations_isReadableFromKotlin() {
        assertEquals(
            listOf(
                AppDestination.DETAILS,
                AppDestination.CHARTS,
                AppDestination.SAVINGS,
                AppDestination.VAULT
            ),
            AppDestination.bottomBarDestinations()
        )
    }

    @Test
    fun isStudy_matchesExactlyFourStudyDestinations() {
        val studyDestinations = AppDestination.entries.filter { it.isStudy }
        assertEquals(
            listOf(
                AppDestination.STUDY_FOCUS,
                AppDestination.STUDY_TODO,
                AppDestination.STUDY_STATS,
                AppDestination.STUDY_GARDEN
            ),
            studyDestinations
        )
    }

    @Test
    fun stateFlow_emitsJavaEnumUnderRunTest() = runTest {
        val state = MutableStateFlow(AppDestination.HOME)
        state.value = AppDestination.STUDY_FOCUS
        assertEquals(AppDestination.STUDY_FOCUS, state.first())
    }
}
