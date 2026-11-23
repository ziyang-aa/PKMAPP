package com.example.pkmapp.exchange;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Parses single-rate and batch-rate responses returned by Frankfurter v2. */
public final class ExchangeRateJsonParser {
    private ExchangeRateJsonParser() {
    }

    public static ExchangeRateSnapshot parse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("汇率响应为空");
        }
        try {
            JSONObject object = new JSONObject(response);
            return new ExchangeRateSnapshot(
                    object.getString("base"),
                    object.getString("quote"),
                    new BigDecimal(object.get("rate").toString()),
                    object.getString("date"));
        } catch (JSONException exception) {
            throw new IllegalArgumentException("无法读取汇率响应", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
    }

    public static Map<String, ExchangeRateSnapshot> parseBatch(String response, String fallbackBase) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("汇率响应为空");
        }
        String trimmed = response.trim();
        Map<String, ExchangeRateSnapshot> result = new LinkedHashMap<>();
        try {
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String base = item.optString("base", fallbackBase).toUpperCase(Locale.US);
                    String quote = item.getString("quote").toUpperCase(Locale.US);
                    String date = item.optString("date", "");
                    BigDecimal rate = new BigDecimal(item.get("rate").toString());
                    if (rate.compareTo(BigDecimal.ZERO) > 0) {
                        result.put(quote, new ExchangeRateSnapshot(base, quote, rate, date));
                    }
                }
            } else {
                JSONObject object = new JSONObject(trimmed);
                String base = object.optString("base", fallbackBase).toUpperCase(Locale.US);
                String date = object.optString("date", "");
                JSONObject rates = object.getJSONObject("rates");
                Iterator<String> keys = rates.keys();
                while (keys.hasNext()) {
                    String quote = keys.next();
                    BigDecimal rate = new BigDecimal(rates.get(quote).toString());
                    if (rate.compareTo(BigDecimal.ZERO) > 0) {
                        String normQuote = quote.toUpperCase(Locale.US);
                        result.put(normQuote, new ExchangeRateSnapshot(base, normQuote, rate, date));
                    }
                }
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException("未解析到有效汇率");
            }
            return result;
        } catch (JSONException exception) {
            throw new IllegalArgumentException("无法读取批量汇率响应", exception);
        }
    }
}
