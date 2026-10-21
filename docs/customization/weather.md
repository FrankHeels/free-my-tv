# Weather fallback — Xiaomi, 2026-10-21

The city is Svetly, Kaliningrad Oblast (54.6750, 20.1347). Open-Meteo's geocoder contains this city. On the TV, geocoding returned ten matches in about 0.6 s, while TCP connections to `api.open-meteo.com:443` timed out at both 6 s and 20 s. The computer resolved the same IP and could fetch the forecast. This isolates the observed failure to forecast-server reachability from the TV; it does not establish who blocked or dropped the connection. The user confirmed that searching with the oblast finds the city.

## Behavior

`WeatherLoader` tries Open-Meteo first. A connection error, unsuccessful HTTP status or malformed top-level response invokes MET Norway Locationforecast. Its forecast request returned HTTP 200 on the same TV in about 0.3 s. There is no API key, HTTP downgrade, TLS bypass, proxy, or change to TV networking.

The fallback normalizes UTC temperatures and weather symbols into the existing weather display format. It chooses the closest forecast time within one hour, preserves future hourly points, and rejects an empty or out-of-date series. The temperature range is calculated from the forecast for the next 24 hours and labelled accordingly; it is not presented as a complete calendar-day minimum/maximum. Unknown conditions remain unknown. Sunrise/sunset come from MET Norway Sunrise 3.0; missing events or an unavailable sunrise endpoint do not suppress the forecast or fabricate times.

The widget identifies the data source. The fallback keeps a bounded two-response cache (forecast and sun), keyed by endpoint and coordinates, respects `Expires`, and uses `If-Modified-Since` / HTTP 304. Requests use a descriptive User-Agent, four-decimal coordinates, 6 s timeouts and a 1 MiB response limit. The existing 30-minute weather refresh and last-successful display cache remain. Forecast results for a city changed during the request are discarded.

Local build defaults are set to Svetly in ignored `config.properties`. A city explicitly selected on the TV still takes precedence. Search/geocoding behavior was not changed.

## Checks

The isolated Android regression first failed with `IOException: timeout` when only the primary request was wired. After adding fallback, 15 checks passed: primary timeout recovery, coordinate preservation, source metadata, hourly values, sunrise, fresh and conditional caches, location changes, healthy primary, missing sun data, condition mapping and stale/empty rejection. The existing 56 continuation checks and 2 persistence checks also passed. Fixtures are local; the normal test runner makes no real weather requests. The optional `weather-live` instrumentation phase explicitly exercises the production loader against both services for Svetly.

The live production-loader check passed on Xiaomi. Version 1.3 was installed, and the user confirmed that the weather appeared. Visual inspection showed Svetly, 14°C, six future hours, a labelled 24-hour temperature range, sunrise/sunset, and MET Norway attribution. Both temporary diagnostic packages were removed.

## Data sources and attribution

- [Open-Meteo](https://open-meteo.com/) — [forecast API](https://open-meteo.com/en/docs), [geocoding API](https://open-meteo.com/en/docs/geocoding-api).
- [MET Norway](https://www.met.no/) — [Locationforecast 2.0](https://api.met.no/weatherapi/locationforecast/2.0/documentation), [Sunrise 3.0](https://api.met.no/weatherapi/sunrise/3.0/documentation).
- Weather data: [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/). MET Norway data is adapted to the launcher's weather symbols and a calculated 24-hour temperature range. The providers do not endorse this fork.

The services receive the requested city coordinates and the TV's public IP. No viewing history is included in weather requests.
