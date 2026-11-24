package com.example.pkmapp.exchange

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class CurrencyInfo(
    @JvmField val code: String,
    @JvmField val name: String,
    @JvmField val symbol: String,
    @JvmField val decimals: Int
)

data class MultiCurrencyItem(
    @JvmField val code: String,
    @JvmField val name: String,
    @JvmField val unitRateText: String?,
    @JvmField val convertedText: String,
    @JvmField val isCurrentQuote: Boolean
)

data class ExchangeUiState(
    @JvmField val baseInfo: CurrencyInfo,
    @JvmField val quoteInfo: CurrencyInfo,
    @JvmField val amountInput: String,
    @JvmField val statusText: String,
    @JvmField val captionText: String,
    @JvmField val errorText: String?,
    @JvmField val resultText: String,
    @JvmField val multiItems: List<MultiCurrencyItem>
)

sealed interface BatchFetchOutcome {
    data class Success(
        val snapshots: Map<String, ExchangeRateSnapshot>,
        val fromCache: Boolean
    ) : BatchFetchOutcome

    data class Failure(
        val message: String,
        val cachedSnapshots: Map<String, ExchangeRateSnapshot>
    ) : BatchFetchOutcome
}

sealed interface SingleFetchOutcome {
    data class Success(
        val snapshot: ExchangeRateSnapshot,
        val fromCache: Boolean
    ) : SingleFetchOutcome

    data class Failure(
        val message: String,
        val cachedSnapshot: ExchangeRateSnapshot?
    ) : SingleFetchOutcome
}

interface ExchangeRateGateway {
    suspend fun fetchBatch(baseCurrency: String, quoteCurrencies: Array<String>): BatchFetchOutcome
    suspend fun fetchSingle(baseCurrency: String, quoteCurrency: String): SingleFetchOutcome
    fun syncTimeLabel(): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
    fun close() {}
}

internal class AndroidExchangeRateGateway(context: Context) : ExchangeRateGateway {
    private val service = ExchangeRateService(context.applicationContext)

    override suspend fun fetchBatch(
        baseCurrency: String,
        quoteCurrencies: Array<String>
    ): BatchFetchOutcome = suspendCancellableCoroutine { cont ->
        service.fetchBatch(baseCurrency, quoteCurrencies, object : ExchangeRateService.BatchCallback {
            override fun onSuccess(snapshots: Map<String, ExchangeRateSnapshot>, fromCache: Boolean) {
                if (cont.isActive) cont.resume(BatchFetchOutcome.Success(snapshots, fromCache))
            }

            override fun onError(message: String, cachedSnapshots: Map<String, ExchangeRateSnapshot>) {
                if (cont.isActive) cont.resume(BatchFetchOutcome.Failure(message, cachedSnapshots))
            }
        })
    }

    override suspend fun fetchSingle(
        baseCurrency: String,
        quoteCurrency: String
    ): SingleFetchOutcome = suspendCancellableCoroutine { cont ->
        service.fetch(baseCurrency, quoteCurrency, object : ExchangeRateService.Callback {
            override fun onSuccess(snapshot: ExchangeRateSnapshot, fromCache: Boolean) {
                if (cont.isActive) cont.resume(SingleFetchOutcome.Success(snapshot, fromCache))
            }

            override fun onError(message: String, cachedSnapshot: ExchangeRateSnapshot?) {
                if (cont.isActive) cont.resume(SingleFetchOutcome.Failure(message, cachedSnapshot))
            }
        })
    }

    override fun close() {
        service.close()
    }
}

class ExchangeViewModel(
    private val gateway: ExchangeRateGateway
) : ViewModel() {

    private var baseCurrency = "CNY"
    private var quoteCurrency = "USD"
    private var amountInput = "100"
    private val ratesByQuote = LinkedHashMap<String, ExchangeRateSnapshot>()
    private var statusText = "正在联网同步最新汇率…"
    private var errorText: String? = null
    private var loadJob: Job? = null
    private var hasLoadedOnce = false

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<ExchangeUiState> = _uiState.asStateFlow()

    fun observe(owner: LifecycleOwner, observer: Consumer<ExchangeUiState>) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                uiState.collect { observer.accept(it) }
            }
        }
    }

    fun ensureInitialLoad() {
        if (!hasLoadedOnce) {
            refreshRates()
        }
    }

    fun setAmountInput(raw: String) {
        if (amountInput == raw) return
        amountInput = raw
        _uiState.value = buildUiState()
    }

    fun swapCurrencies() {
        val oldBase = baseCurrency
        baseCurrency = quoteCurrency
        quoteCurrency = oldBase
        _uiState.value = buildUiState()
        refreshRates()
    }

    fun selectBaseCurrency(code: String) {
        if (baseCurrency == code) return
        baseCurrency = code
        ratesByQuote.clear()
        _uiState.value = buildUiState()
        refreshRates()
    }

    fun selectQuoteCurrency(code: String) {
        if (quoteCurrency == code) return
        quoteCurrency = code
        _uiState.value = buildUiState()
        if (!ratesByQuote.containsKey(quoteCurrency) && baseCurrency != quoteCurrency) {
            refreshRates()
        }
    }

    fun refreshRates() {
        hasLoadedOnce = true
        loadJob?.cancel()
        statusText = "正在联网同步最新汇率…"
        errorText = null
        _uiState.value = buildUiState()

        val targetBase = baseCurrency
        val targetQuote = quoteCurrency
        val allCodes = CURRENCIES.map { it.code }.toTypedArray()

        loadJob = viewModelScope.launch {
            when (val batch = gateway.fetchBatch(targetBase, allCodes)) {
                is BatchFetchOutcome.Success -> {
                    ratesByQuote.clear()
                    ratesByQuote.putAll(batch.snapshots)
                    statusText = if (batch.fromCache) {
                        "显示最近一次同步"
                    } else {
                        "刚刚同步 · ${gateway.syncTimeLabel()} · 支持 ${CURRENCIES.size} 种货币"
                    }
                    errorText = null
                }
                is BatchFetchOutcome.Failure -> {
                    when (val single = gateway.fetchSingle(targetBase, targetQuote)) {
                        is SingleFetchOutcome.Success -> {
                            ratesByQuote.clear()
                            ratesByQuote.putAll(batch.cachedSnapshots)
                            ratesByQuote[single.snapshot.quoteCurrency] = single.snapshot
                            statusText = "刚刚同步 · ${gateway.syncTimeLabel()}"
                            errorText = null
                        }
                        is SingleFetchOutcome.Failure -> {
                            ratesByQuote.clear()
                            ratesByQuote.putAll(batch.cachedSnapshots)
                            if (single.cachedSnapshot != null) {
                                ratesByQuote[single.cachedSnapshot.quoteCurrency] = single.cachedSnapshot
                            }
                            val hasAny = ratesByQuote.containsKey(targetQuote) || targetBase == targetQuote
                            statusText = if (hasAny) "离线模式 · 使用最近一次同步" else "暂未获取到汇率"
                            errorText = if (hasAny) {
                                "网络暂不可用，已自动展示上次同步的参考汇率"
                            } else {
                                single.message
                            }
                        }
                    }
                }
            }
            _uiState.value = buildUiState()
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        gateway.close()
        super.onCleared()
    }

    private fun currentSnapshot(): ExchangeRateSnapshot? {
        if (baseCurrency == quoteCurrency) {
            return ExchangeRateSnapshot(baseCurrency, quoteCurrency, BigDecimal.ONE, "即时")
        }
        return ratesByQuote[quoteCurrency]
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val value = BigDecimal(trimmed)
            if (value < BigDecimal.ZERO) null else value
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun buildUiState(): ExchangeUiState {
        val baseInfo = infoFor(baseCurrency)
        val quoteInfo = infoFor(quoteCurrency)
        val snap = currentSnapshot()
        val parsedAmount = parseAmount(amountInput)

        val caption = if (snap == null) {
            ""
        } else {
            val rateStr = snap.rate.setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            val datePart = if (snap.date.isNullOrEmpty()) "" else " · ${snap.date}"
            "1 ${snap.baseCurrency} ≈ $rateStr ${snap.quoteCurrency}$datePart"
        }

        val result = when {
            snap == null -> "--"
            parsedAmount == null -> "0.00"
            else -> {
                var converted = ExchangeRateCalculator.convert(parsedAmount, snap)
                if (quoteInfo.decimals == 0) {
                    converted = converted.setScale(0, RoundingMode.HALF_UP)
                }
                "${quoteInfo.symbol} ${converted.toPlainString()}"
            }
        }

        val referenceAmount = parsedAmount ?: BigDecimal.valueOf(100)
        val multi = ArrayList<MultiCurrencyItem>()
        for (code in POPULAR_QUOTES) {
            if (code == baseCurrency) continue
            val rowSnap = ratesByQuote[code]
            val info = infoFor(code)
            val unitRate = rowSnap?.let {
                val r = it.rate.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                "1 $baseCurrency ≈ $r $code"
            }
            val valText = if (rowSnap != null) {
                var converted = ExchangeRateCalculator.convert(referenceAmount, rowSnap)
                if (info.decimals == 0) {
                    converted = converted.setScale(0, RoundingMode.HALF_UP)
                }
                "${info.symbol} ${converted.toPlainString()}"
            } else {
                "--"
            }
            multi.add(
                MultiCurrencyItem(
                    code = info.code,
                    name = info.name,
                    unitRateText = unitRate,
                    convertedText = valText,
                    isCurrentQuote = code == quoteCurrency
                )
            )
        }

        return ExchangeUiState(
            baseInfo = baseInfo,
            quoteInfo = quoteInfo,
            amountInput = amountInput,
            statusText = statusText,
            captionText = caption,
            errorText = errorText,
            resultText = result,
            multiItems = multi
        )
    }

    companion object {
        @JvmField
        val CURRENCIES: List<CurrencyInfo> = listOf(
            CurrencyInfo("CNY", "人民币", "¥", 2),
            CurrencyInfo("USD", "美元", "$", 2),
            CurrencyInfo("EUR", "欧元", "€", 2),
            CurrencyInfo("JPY", "日元", "¥", 0),
            CurrencyInfo("HKD", "港元", "HK$", 2),
            CurrencyInfo("GBP", "英镑", "£", 2),
            CurrencyInfo("KRW", "韩元", "₩", 0),
            CurrencyInfo("AUD", "澳元", "A$", 2),
            CurrencyInfo("CAD", "加元", "C$", 2),
            CurrencyInfo("SGD", "新加坡元", "S$", 2),
            CurrencyInfo("CHF", "瑞士法郎", "CHF", 2),
            CurrencyInfo("NZD", "新西兰元", "NZ$", 2),
            CurrencyInfo("THB", "泰铢", "฿", 2),
            CurrencyInfo("MYR", "马来西亚林吉特", "RM", 2),
            CurrencyInfo("IDR", "印尼盾", "Rp", 0),
            CurrencyInfo("PHP", "菲律宾比索", "₱", 2),
            CurrencyInfo("INR", "印度卢比", "₹", 2),
            CurrencyInfo("BRL", "巴西雷亚尔", "R$", 2),
            CurrencyInfo("MXN", "墨西哥比索", "Mex$", 2),
            CurrencyInfo("SEK", "瑞典克朗", "kr", 2),
            CurrencyInfo("NOK", "挪威克朗", "kr", 2),
            CurrencyInfo("TRY", "土耳其里拉", "₺", 2),
            CurrencyInfo("ZAR", "南非兰特", "R", 2),
            CurrencyInfo("PLN", "波兰兹罗提", "zł", 2)
        )

        private val POPULAR_QUOTES = arrayOf("USD", "EUR", "JPY", "HKD", "GBP", "KRW", "AUD", "SGD")

        @JvmStatic
        fun infoFor(code: String): CurrencyInfo =
            CURRENCIES.firstOrNull { it.code == code } ?: CurrencyInfo(code, code, code, 2)

        @JvmStatic
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    ExchangeViewModel(AndroidExchangeRateGateway(appContext))
                }
            }
        }

        @JvmStatic
        fun get(fragment: Fragment): ExchangeViewModel =
            ViewModelProvider(fragment, factory(fragment.requireContext()))[ExchangeViewModel::class.java]
    }
}
