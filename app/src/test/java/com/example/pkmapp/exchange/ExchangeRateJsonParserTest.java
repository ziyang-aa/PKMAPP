package com.example.pkmapp.exchange;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public final class ExchangeRateJsonParserTest {
    @Test
    public void parsesFrankfurterV2RateResponse() {
        ExchangeRateSnapshot snapshot = ExchangeRateJsonParser.parse(
                "{\"date\":\"2026-09-08\",\"base\":\"USD\","
                        + "\"quote\":\"CNY\",\"rate\":7.1234}");

        assertEquals("USD", snapshot.getBaseCurrency());
        assertEquals("CNY", snapshot.getQuoteCurrency());
        assertEquals(new BigDecimal("7.1234"), snapshot.getRate());
        assertEquals("2026-09-08", snapshot.getDate());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingOrNonPositiveRate() {
        ExchangeRateJsonParser.parse(
                "{\"date\":\"2026-09-08\",\"base\":\"USD\","
                        + "\"quote\":\"CNY\",\"rate\":0}");
    }

    @Test
    public void latestRateRequest_bypassesHttpCache() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost").openConnection();
        Method method = ExchangeRateService.class.getDeclaredMethod(
                "configureLatestRequest", HttpURLConnection.class);
        method.setAccessible(true);

        method.invoke(null, connection);

        assertEquals(false, connection.getUseCaches());
        assertEquals("no-cache", connection.getRequestProperty("Cache-Control"));
        assertEquals("no-cache", connection.getRequestProperty("Pragma"));
    }

    @Test
    public void parsesFrankfurterV2BatchArrayResponse() {
        Map<String, ExchangeRateSnapshot> batch = ExchangeRateJsonParser.parseBatch(
                "[{\"date\":\"2026-11-23\",\"base\":\"CNY\",\"quote\":\"USD\",\"rate\":0.1412},"
                        + "{\"date\":\"2026-11-23\",\"base\":\"CNY\",\"quote\":\"JPY\",\"rate\":21.35}]",
                "CNY");

        assertEquals(2, batch.size());
        assertEquals(new BigDecimal("0.1412"), batch.get("USD").getRate());
        assertEquals(new BigDecimal("21.35"), batch.get("JPY").getRate());
    }

    @Test
    public void parsesBatchRatesObjectResponse() {
        Map<String, ExchangeRateSnapshot> batch = ExchangeRateJsonParser.parseBatch(
                "{\"date\":\"2026-11-23\",\"base\":\"CNY\",\"rates\":{\"EUR\":0.1301,\"GBP\":0.1112}}",
                "CNY");

        assertEquals(2, batch.size());
        assertEquals(new BigDecimal("0.1301"), batch.get("EUR").getRate());
        assertEquals(new BigDecimal("0.1112"), batch.get("GBP").getRate());
    }
}
