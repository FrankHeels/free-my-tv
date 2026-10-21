package io.freemytv;

import java.util.*;

public final class ContinueModelTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static ContinueModel.Item item(String pkg, String id, int type, long position, long duration, long engaged) {
        return new ContinueModel.Item(pkg, id, "Same film", pkg, "1", "2", "Episode", "https://example.org/art", "app://film", type, position, duration, engaged);
    }
    public static void main(String[] args) {
        Set<String> none = Collections.emptySet();
        List<ContinueModel.Item> rows = new ArrayList<>();
        check(ContinueModel.visible(rows, none, none).isEmpty(), "empty history");
        for (int i = 0; i < 8; i++) rows.add(item("service" + i, "film", 0, 30, 100, i));
        List<ContinueModel.Item> visible = ContinueModel.visible(rows, none, none);
        check(visible.size() == 8, "more than four cards retained");
        check(visible.get(0).pkg.equals("service7"), "newest first");
        check(ContinueModel.scroll(0, 3, 8) == 0, "four cards fit");
        check(ContinueModel.scroll(0, 4, 8) == 1, "fifth card revealed");
        check(ContinueModel.scroll(4, 2, 8) == 2, "scroll left");
        check(ContinueModel.scroll(4, 1, 2) == 0, "shrink clamps window");
        check(ContinueModel.focus(visible, rows.get(0).key, 0) == 7, "stable focus after reorder");
        check(ContinueModel.focus(visible, "removed", 6) == 6, "nearest surviving index");
        check(ContinueModel.focus(visible, "removed", 99) == 7, "last item removed");
        check(ContinueModel.focus(Collections.emptyList(), "removed", 9) == 0, "empty focus");
        check(ContinueModel.visible(rows, Collections.singleton("service2"), none).size() == 7, "excluded source");
        String hidden = rows.get(0).key;
        check(ContinueModel.visible(rows, none, Collections.singleton(hidden)).size() == 7, "hide one service only");
        ContinueModel.Item reinserted = item("service0", "film", 0, 90, 100, 999);
        check(hidden.equals(reinserted.key), "new progress and row insertion preserve identity");
        check(ContinueModel.visible(Arrays.asList(reinserted), none, Collections.singleton(hidden)).isEmpty(), "hidden until restored");
        check(ContinueModel.visible(Arrays.asList(reinserted), none, none).size() == 1, "manual restore");
        check(ContinueModel.visible(Arrays.asList(item("a", "a", 1, 5, 100, 1), item("a", "b", 2, 5, 100, 1), item("a", "c", 3, 5, 100, 1)), none, none).isEmpty(), "exclude next/new/watchlist");
        check(ContinueModel.visible(Arrays.asList(item("a", "a", 0, 100, 100, 1), item("a", "b", 0, 101, 100, 1)), none, none).isEmpty(), "completed records");
        check(ContinueModel.visible(Arrays.asList(item("a", "a", 0, -1, 100, 1), item("a", "b", 0, 10, -1, 1)), none, none).size() == 2, "missing progress retained");
        check(item("a", "a", 0, -1, 100, 1).progress() == -1, "missing position not invented");
        check(item("a", "a", 0, 10, 0, 1).progress() == -1, "zero duration unknown");
        check(item("a", "a", 0, 0, 100, 1).progress() == 0, "known zero position");
        check(item("a", "a", 0, 25, 100, 1).progress() == .25f, "progress fraction");
        check(item("a", "a", 0, 2_000_000_000L, 4_000_000_000L, 1).progress() == .5f, "long durations");
        List<ContinueModel.Item> ties = Arrays.asList(item("a", "b", 0, 1, 100, 10), item("a", "a", 0, 1, 100, 10));
        List<ContinueModel.Item> first = ContinueModel.visible(ties, none, none);
        Collections.reverse(ties);
        check(first.get(0).key.equals(ContinueModel.visible(ties, none, none).get(0).key), "ties independent of provider order");
        check(ContinueModel.visible(Arrays.asList(item("a", "missing", 0, 1, 10, -1), item("a", "known", 0, 1, 10, 1)), none, none).get(1).engaged == -1, "missing timestamp last");
        ContinueModel.Item noId = item("a", "", 0, 1, 100, 2);
        ContinueModel.Item recreated = new ContinueModel.Item("a", null, "Same film", "renamed app", "1", "2", "Episode", "https://new/art", "app://film?token=new", 0, 99, 100, 100);
        check(noId.key.equals(recreated.key), "fallback excludes mutable art/link/progress");
        check(!noId.key.equals(new ContinueModel.Item("a", "", "Same film", "a", "1", "3", "Episode", "", "", 0, 1, 100, 2).key), "episodes distinct");
        check(ContinueModel.visible(Arrays.asList(item("", "x", 0, 1, 10, 1)), none, none).isEmpty(), "missing source");
        System.out.println("PASS: " + checks + " continuation model checks");
    }
}
