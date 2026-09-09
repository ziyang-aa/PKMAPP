package com.example.pkmapp.exchange;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Online exchange-rate loader with a small last-success cache for offline viewing. */
public final class ExchangeRateService {
    private static final String API_ROOT = "https://api.frankfurter.dev/v2/rate/";
    private static final String PREFERENCES = "exchange_rates";
    private static final int TIMEOUT_MILLIS = 10_000;

    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ExchangeRateService(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
    }

    public void fetch(String baseCurrency, String quoteCurrency, Callback callback) {
        String base = normalizeCurrency(baseCurrency);
        String quote = normalizeCurrency(quoteCurrency);
        ExchangeRateSnapshot cached = getCached(base, quote);
        executor.execute(() -> {
            try {
                ExchangeRateSnapshot snapshot = request(base, quote);
                saveCached(snapshot);
                mainHandler.post(() -> callback.onSuccess(snapshot, false));
            } catch (Exception exception) {
                String message = exception.getMessage() == null
                        ? "暂时无法获取最新汇率" : exception.getMessage();
                mainHandler.post(() -> callback.onError(message, cached));
            }
        });
    }

    @Nullable
    public ExchangeRateSnapshot getCached(String baseCurrency, String quoteCurrency) {
        String value = preferences.getString(cacheKey(baseCurrency, quoteCurrency), null);
        if (value == null) {
            return null;
        }
        String[] fields = value.split("\\|", -1);
        if (fields.length != 2) {
            return null;
        }
        try {
            return new ExchangeRateSnapshot(baseCurrency, quoteCurrency,
                    new BigDecimal(fields[0]), fields[1]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void close() {
        executor.shutdownNow();
    }

    private ExchangeRateSnapshot request(String base, String quote) throws IOException {
        if (base.equals(quote)) {
            return new ExchangeRateSnapshot(base, quote, BigDecimal.ONE, "即时");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(API_ROOT + base + "/" + quote)
                .openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        configureLatestRequest(connection);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("汇率服务暂时不可用（" + statusCode + "）");
            }
            return ExchangeRateJsonParser.parse(readBody(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    static void configureLatestRequest(HttpURLConnection connection) {
        connection.setUseCaches(false);
        connection.setDefaultUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Accept", "application/json");
    }

    private void saveCached(ExchangeRateSnapshot snapshot) {
        preferences.edit().putString(cacheKey(snapshot.getBaseCurrency(), snapshot.getQuoteCurrency()),
                snapshot.getRate().toPlainString() + "|" + snapshot.getDate()).apply();
    }

    private String readBody(InputStream inputStream) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private String cacheKey(String baseCurrency, String quoteCurrency) {
        return "rate_" + normalizeCurrency(baseCurrency) + "_" + normalizeCurrency(quoteCurrency);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("币种不能为空");
        }
        return currency.trim().toUpperCase(Locale.US);
    }

    public interface Callback {
        void onSuccess(ExchangeRateSnapshot snapshot, boolean fromCache);

        void onError(String message, @Nullable ExchangeRateSnapshot cachedSnapshot);
    }
}
