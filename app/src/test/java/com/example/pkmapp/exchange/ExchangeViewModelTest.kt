package com.example.pkmapp.exchange

import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshRates_convertsSelectedQuoteAndZeroDecimalCurrencies() = runTest {
        val gateway = object : ExchangeRateGateway {
            override suspend fun fetchBatch(
                baseCurrency: String,
                quoteCurrencies: Array<String>
            ): BatchFetchOutcome = BatchFetchOutcome.Success(
                snapshots = mapOf(
                    "USD" to ExchangeRateSnapshot("CNY", "USD", BigDecimal("0.1385"), "2026-11-24"),
                    "JPY" to ExchangeRateSnapshot("CNY", "JPY", BigDecimal("21.436"), "2026-11-24")
                ),
                fromCache = false
            )

            override suspend fun fetchSingle(
                baseCurrency: String,
                quoteCurrency: String
            ): SingleFetchOutcome = error("Should not fall back when batch succeeds")

            override fun syncTimeLabel(): String = "16:20"
        }

        val vm = ExchangeViewModel(gateway)
        vm.refreshRates()

        val usdState = vm.uiState.value
        assertNull(usdState.errorText)
        assertTrue(usdState.statusText.contains("刚刚同步 · 16:20"))
        assertEquals("1 CNY ≈ 0.1385 USD · 2026-11-24", usdState.captionText)
        assertEquals("$ 13.85", usdState.resultText)

        vm.selectQuoteCurrency("JPY")
        vm.setAmountInput("200")
        val jpyState = vm.uiState.value
        assertEquals("¥ 4287", jpyState.resultText)
        assertEquals("1 CNY ≈ 21.436 JPY · 2026-11-24", jpyState.captionText)
    }

    @Test
    fun refreshRates_fallsBackToOfflineCacheWhenBatchAndSingleFail() = runTest {
        val cachedUsd = ExchangeRateSnapshot("CNY", "USD", BigDecimal("0.14"), "2026-11-20")
        val gateway = object : ExchangeRateGateway {
            override suspend fun fetchBatch(
                baseCurrency: String,
                quoteCurrencies: Array<String>
            ): BatchFetchOutcome = BatchFetchOutcome.Failure(
                message = "网络超时",
                cachedSnapshots = mapOf("USD" to cachedUsd)
            )

            override suspend fun fetchSingle(
                baseCurrency: String,
                quoteCurrency: String
            ): SingleFetchOutcome = SingleFetchOutcome.Failure(
                message = "网络超时",
                cachedSnapshot = cachedUsd
            )
        }

        val vm = ExchangeViewModel(gateway)
        vm.refreshRates()

        val state = vm.uiState.value
        assertEquals("离线模式 · 使用最近一次同步", state.statusText)
        assertEquals("网络暂不可用，已自动展示上次同步的参考汇率", state.errorText)
        assertEquals("$ 14.00", state.resultText)
    }
}
