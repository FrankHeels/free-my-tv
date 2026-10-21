package io.freemytv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Provider-independent rules, also run by the plain Java test suite. */
final class ContinueModel {
    static final int VISIBLE = 4;

    static final class Item {
        final String pkg, title, source, season, episodeNumber, episodeTitle, art, intent, key;
        final int type;
        final long position, duration, engaged;

        Item(String pkg, String id, String title, String source, String season, String episodeNumber,
                String episodeTitle, String art, String intent, int type, long position, long duration, long engaged) {
            this.pkg = value(pkg);
            this.title = value(title);
            this.source = value(source);
            this.season = value(season);
            this.episodeNumber = value(episodeNumber);
            this.episodeTitle = value(episodeTitle);
            this.art = value(art);
            this.intent = value(intent);
            this.type = type;
            this.position = position;
            this.duration = duration;
            this.engaged = engaged;
            // Never include provider row id, artwork, engagement or playback progress. Reinsertions stay hidden.
            // Length prefixes avoid collisions between fields containing separators.
            String identity = !value(id).isEmpty() ? fields("id", id)
                    : !this.title.isEmpty() ? fields("metadata", title, season, episodeNumber, episodeTitle)
                    : fields("intent", intent);
            key = this.pkg + ":" + digest(identity);
        }

        float progress() {
            return position < 0 || duration <= 0 ? -1 : Math.min(1f, (float) position / duration);
        }
    }

    static List<Item> visible(List<Item> rows, Set<String> excluded, Set<String> hidden) {
        List<Item> out = new ArrayList<>();
        for (Item item : rows) {
            if (item.type != 0 || item.pkg.isEmpty() || excluded.contains(item.pkg) || hidden.contains(item.key)) continue;
            if (item.duration > 0 && item.position >= item.duration) continue;
            out.add(item);
        }
        // A deterministic secondary key keeps ties stable even if a provider returns rows in a different order.
        out.sort(Comparator.comparingLong((Item i) -> Math.max(0, i.engaged)).reversed().thenComparing(i -> i.key));
        return out;
    }

    static int focus(List<Item> rows, String previousKey, int previousIndex) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).key.equals(previousKey)) return i;
        return Math.max(0, Math.min(previousIndex, rows.size() - 1));
    }

    static int scroll(int first, int focus, int count) {
        first = Math.max(0, Math.min(first, Math.max(0, count - VISIBLE)));
        if (focus < first) return focus;
        if (focus >= first + VISIBLE) return focus - VISIBLE + 1;
        return first;
    }

    private static String value(String s) { return s == null ? "" : s; }
    private static String fields(String... values) {
        StringBuilder out = new StringBuilder();
        for (String s : values) { s = value(s); out.append(s.length()).append(':').append(s); }
        return out.toString();
    }
    private static String digest(String s) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
