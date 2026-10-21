package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.json.*;

final class WeatherLoaderTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    static int run(Context context) throws Exception {
        checks = 0;
        SharedPreferences cache = context.getSharedPreferences("weather-test", 0);
        cache.edit().clear().commit();
        long now = Instant.parse("2026-10-21T12:30:00Z").toEpochMilli();
        JSONObject properties = new JSONObject();
        JSONArray times = new JSONArray();
        for (int h = 12; h < 20; h++) times.put(new JSONObject().put("time", "2026-10-21T" + h + ":00:00Z")
                .put("data", new JSONObject().put("instant", new JSONObject().put("details", new JSONObject().put("air_temperature", h)))
                .put("next_1_hours", new JSONObject().put("summary", new JSONObject().put("symbol_code", "partlycloudy_day")))));
        String met = new JSONObject().put("properties", properties.put("timeseries", times)).toString();
        String sun = "{\"properties\":{\"sunrise\":{\"time\":\"2026-10-21T05:00:00Z\"},\"sunset\":{\"time\":\"2026-10-21T16:00:00Z\"}}}";
        List<String> requests = new ArrayList<>();
        WeatherLoader.Getter timeout = (url, modified) -> {
            requests.add(url);
            if (url.startsWith("https://api.open-meteo.com/")) throw new IOException("timeout");
            return new WeatherLoader.Reply(200, url.contains("sunrise") ? sun : met, now + 3600000, "Wed, 21 Oct 2026 12:00:00 GMT");
        };
        JSONObject result = new JSONObject(WeatherLoader.load(cache, "54.67501", "20.13473", now, timeout));
        check(result.getJSONObject("current").getDouble("temperature_2m") == 12, "timeout still returns current forecast from fallback");
        check(requests.size() == 3 && requests.get(1).contains("lat=54.6750&lon=20.1347"), "use selected coordinates with four decimals");
        check(result.getJSONObject("hourly").getJSONArray("time").length() == 8, "retain forecast hours");
        check("MET Norway".equals(result.getString("source")), "fallback attribution retained");
        check(result.getJSONObject("daily").getJSONArray("sunrise").getString(0).equals("2026-10-21T05:00"), "sunrise normalized to UTC");
        WeatherLoader.load(cache, "54.67501", "20.13473", now + 1000, timeout);
        check(requests.size() == 4, "fresh fallback HTTP cache reused after primary timeout");
        final int[] conditional = {0};
        WeatherLoader.load(cache, "54.67501", "20.13473", now + 3600001, (url, modified) -> {
            if (url.startsWith("https://api.open-meteo.com/")) throw new IOException("timeout");
            check(!modified.isEmpty(), "expired requests send Last-Modified validator"); conditional[0]++;
            return new WeatherLoader.Reply(304, "", now + 7200000, modified);
        });
        check(conditional[0] == 2, "304 reuses both cached bodies");
        int before = requests.size();
        WeatherLoader.load(cache, "55", "21", now + 1000, timeout);
        check(requests.size() == before + 3, "new location never reuses old forecast");
        String primary = "{\"current\":{\"temperature_2m\":15},\"daily\":{},\"hourly\":{}}";
        final int[] calls = {0};
        check(primary.equals(WeatherLoader.load(cache, "54", "20", now, (url, modified) -> {
            calls[0]++; return new WeatherLoader.Reply(200, primary, 0, "");
        })) && calls[0] == 1, "healthy primary does not contact fallback");
        cache.edit().clear().commit();
        JSONObject noSun = new JSONObject(WeatherLoader.load(cache, "54", "20", now, (url, modified) -> {
            if (!url.contains("locationforecast")) throw new IOException("unavailable");
            return new WeatherLoader.Reply(200, met, 0, "");
        }));
        check(noSun.getJSONObject("current").getDouble("temperature_2m") == 12 &&
                noSun.getJSONObject("daily").getJSONArray("sunrise").getString(0).isEmpty(), "sun endpoint failure does not hide forecast or invent sunrise");
        check(WeatherLoader.code("heavysnow_night") == 75 && WeatherLoader.code("lightrainandthunder") == 95
                && WeatherLoader.code("unknown") == -1, "weather symbols mapped without inventing unknown conditions");
        boolean failed = false;
        try { WeatherLoader.normalize(met, "", now + 86400000); } catch (Exception expected) { failed = true; }
        check(failed, "stale forecast cannot become current");
        failed = false;
        try { WeatherLoader.normalize("{\"properties\":{\"timeseries\":[]}}", "", now); } catch (Exception expected) { failed = true; }
        check(failed, "empty forecast rejected");
        cache.edit().clear().commit();
        return checks;
    }
}
