package io.freemytv;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Forecast transport and MET Norway fallback; callers keep the last successful display cache. */
final class WeatherLoader {
    private static final String QUERY = "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
            + "&hourly=temperature_2m,weather_code,is_day&timezone=auto&forecast_days=2";
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    interface Getter { Reply get(String url, String modified) throws Exception; }
    static final class Reply {
        final int status; final String body, modified; final long expires;
        Reply(int status, String body, long expires, String modified) {
            this.status = status; this.body = body; this.expires = expires; this.modified = modified;
        }
    }
    private WeatherLoader() {}

    static String load(SharedPreferences cache, String lat, String lon, long now) throws Exception {
        return load(cache, lat, lon, now, WeatherLoader::request);
    }
    static String load(SharedPreferences cache, String lat, String lon, long now, Getter get) throws Exception {
        String primary = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + QUERY;
        try {
            Reply reply = get.get(primary, "");
            if (reply.status != 200) throw new IOException("Weather HTTP " + reply.status);
            JSONObject body = new JSONObject(reply.body);
            body.getJSONObject("current").getDouble("temperature_2m");
            body.getJSONObject("daily"); body.getJSONObject("hourly");
            return reply.body;
        } catch (Exception unavailable) {
            return fallback(cache, lat, lon, now, get);
        }
    }

    private static String fallback(SharedPreferences cache, String lat, String lon, long now, Getter get) throws Exception {
        String coords = "lat=" + String.format(Locale.ROOT, "%.4f", Double.parseDouble(lat))
                + "&lon=" + String.format(Locale.ROOT, "%.4f", Double.parseDouble(lon));
        String forecast = cached(cache, "met_forecast", "https://api.met.no/weatherapi/locationforecast/2.0/compact?" + coords, now, get);
        // Sunrise uses a mean solar day at the selected longitude, independently of the TV timezone.
        String date = Instant.ofEpochMilli(now).plusSeconds(Math.round(Double.parseDouble(lon) * 240)).atOffset(ZoneOffset.UTC).toLocalDate().toString();
        String sun = "";
        try { sun = cached(cache, "met_sun", "https://api.met.no/weatherapi/sunrise/3.0/sun?" + coords + "&date=" + date, now, get); }
        catch (Exception ignored) { /* Missing sun events must not suppress an available forecast. */ }
        return normalize(forecast, sun, now);
    }

    private static String cached(SharedPreferences cache, String key, String url, long now, Getter get) throws Exception {
        boolean same = url.equals(cache.getString(key + "_url", ""));
        String body = same ? cache.getString(key + "_body", "") : "";
        if (!body.isEmpty() && now < cache.getLong(key + "_expires", 0)) return body;
        Reply reply = get.get(url, body.isEmpty() ? "" : cache.getString(key + "_modified", ""));
        if (reply.status == 200) body = reply.body;
        else if (reply.status != 304 || body.isEmpty()) throw new IOException("Weather HTTP " + reply.status);
        new JSONObject(body); // Do not cache error pages.
        cache.edit().putString(key + "_url", url).putString(key + "_body", body)
                .putLong(key + "_expires", reply.expires).putString(key + "_modified", reply.modified).apply();
        return body;
    }

    static String normalize(String forecast, String sun, long now) throws Exception {
        JSONArray series = new JSONObject(forecast).getJSONObject("properties").getJSONArray("timeseries");
        JSONArray times = new JSONArray(), temps = new JSONArray(), codes = new JSONArray(), days = new JSONArray();
        JSONObject current = null;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        long closest = Long.MAX_VALUE;
        for (int i = 0; i < series.length(); i++) {
            JSONObject entry = series.getJSONObject(i), data = entry.getJSONObject("data");
            long at = Instant.parse(entry.getString("time")).toEpochMilli();
            double temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            if (Double.isNaN(temp) || Double.isInfinite(temp)) continue;
            String symbol = "";
            for (String period : new String[]{"next_1_hours", "next_6_hours", "next_12_hours"}) {
                JSONObject part = data.optJSONObject(period);
                if (part != null && part.optJSONObject("summary") != null) {
                    symbol = part.getJSONObject("summary").optString("symbol_code", ""); break;
                }
            }
            int code = code(symbol), day = symbol.endsWith("_night") ? 0 : 1;
            long distance = Math.abs(at - now);
            if (distance < closest) {
                closest = distance;
                current = new JSONObject().put("temperature_2m", temp).put("weather_code", code).put("is_day", day);
            }
            if (at >= now - 3600000 && at <= now + 24 * 3600000L) {
                min = Math.min(min, temp); max = Math.max(max, temp);
                times.put(MINUTE.format(Instant.ofEpochMilli(at))); temps.put(temp); codes.put(code); days.put(day);
            }
        }
        if (current == null || closest > 3600000 || times.length() < 2) throw new IOException("No current fallback forecast");
        return new JSONObject().put("timezone", "UTC").put("source", "MET Norway").put("range_hours", 24)
                .put("current", current).put("daily", new JSONObject()
                        .put("temperature_2m_min", new JSONArray().put(min)).put("temperature_2m_max", new JSONArray().put(max))
                        .put("sunrise", new JSONArray().put(sunTime(sun, "sunrise"))).put("sunset", new JSONArray().put(sunTime(sun, "sunset"))))
                .put("hourly", new JSONObject().put("time", times).put("temperature_2m", temps).put("weather_code", codes).put("is_day", days)).toString();
    }

    private static String sunTime(String body, String event) {
        try {
            String time = new JSONObject(body).getJSONObject("properties").getJSONObject(event).getString("time");
            return MINUTE.format(OffsetDateTime.parse(time).toInstant());
        } catch (Exception ignored) { return ""; }
    }
    static int code(String symbol) {
        String s = symbol.replace("_day", "").replace("_night", "").replace("_polartwilight", "");
        if (s.contains("thunder")) return 95;
        if (s.contains("snow") || s.contains("sleet")) return s.startsWith("heavy") ? 75 : s.startsWith("light") ? 71 : 73;
        if (s.contains("rain")) return s.startsWith("heavy") ? 65 : s.startsWith("light") ? 61 : 63;
        if (s.equals("clearsky")) return 0;
        if (s.equals("fair")) return 1;
        if (s.equals("partlycloudy")) return 2;
        if (s.equals("cloudy")) return 3;
        if (s.equals("fog")) return 45;
        return -1;
    }

    private static Reply request(String url, String modified) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "FreeMyTV/1.3 (https://github.com/FrankHeels/free-my-tv)");
        if (!modified.isEmpty()) c.setRequestProperty("If-Modified-Since", modified);
        try {
            int status = c.getResponseCode();
            long expires = c.getHeaderFieldDate("Expires", 0);
            String last = c.getHeaderField("Last-Modified");
            if (last == null) last = modified;
            if (status == 304) return new Reply(status, "", expires, last);
            if (status != 200) throw new IOException("Weather HTTP " + status);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + n > 1024 * 1024) throw new IOException("Weather response too large");
                    out.write(buf, 0, n);
                }
                return new Reply(status, out.toString("UTF-8"), expires, last);
            }
        } finally { c.disconnect(); }
    }
}
