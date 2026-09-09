package com.example.pkmapp.exchange;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;

/** Parses the compact response returned by Frankfurter v2's single-rate endpoint. */
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
}
