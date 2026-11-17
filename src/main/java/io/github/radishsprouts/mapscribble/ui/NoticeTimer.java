package io.github.radishsprouts.mapscribble.ui;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 一時のお知らせ（帯の説明の行に 4 秒出る）の待ち行列（画面設計書 3.9・4）。最後の 0.5 秒で不透明度を 1 → 0 にする。
 * 時刻は呼び出し側が渡す（試験できるようにするため）。
 *
 * <p>お知らせは 2 種類に分ける（設計書 3.9）。
 * <ul>
 *   <li><b>操作の結果</b>（3.1 の一時のお知らせと {@code msg.move_busy}）: 待たせず、出ているお知らせをすぐ置き換える。
 *       置き換えられたのが「起きたこと」で、まだ {@link #RETURN_BELOW_MS} 未満しか出ていなかったものは、待ちの先頭へ戻す。</li>
 *   <li><b>起きたこと</b>（{@link #isEvent} が真のキー）: 待ちは最大 {@link #MAX_WAITING} 件。3 件目が来たら一番古い待ちを捨てる。
 *       同じキー・同じ引数のものは足さない。表示の時間は、帯が出て最初に問い合わせた時から数え、
 *       帯が出ていない間（{@link #tick}）は数えない。</li>
 * </ul>
 */
public final class NoticeTimer {
    public static final long DURATION_MS = 4000;
    public static final long FADE_MS = 500;
    /** 待てる「起きたこと」の数（表示中は含まない）。 */
    public static final int MAX_WAITING = 2;
    /** 「起きたこと」が操作の結果に置き換えられた時、これ未満しか出ていなければ待ちの先頭へ戻す。 */
    public static final long RETURN_BELOW_MS = 2000;

    /** 「起きたこと」のキー（設計書 3.9 の表。{@code move_busy} は操作の結果なので含めない）。 */
    private static final Set<String> EVENT_KEYS = Set.of(
            HintResolver.NOTICE_NOTEBOOK_SWITCHED,
            HintResolver.NOTICE_SPLIT_STARTED,
            HintResolver.NOTICE_NO_WORLD_ID,
            HintResolver.NOTICE_MOVED,
            HintResolver.NOTICE_MOVE_UNDONE,
            HintResolver.NOTICE_MOVE_FAILED,
            HintResolver.NOTICE_JM010_IMPORTED);

    /** 数え始めていない（帯が出てから数える）印。 */
    private static final long UNSET = Long.MIN_VALUE;
    /** 描画の間隔がこれを超えて空いたら、帯が出ていなかったとみなして、その間は数えない。 */
    private static final long MAX_FRAME_GAP_MS = 250;

    private static final NoticeTimer SHARED = new NoticeTimer();

    /** 全画面で共有する待ち行列。帯の画面が作り直されても、帯が出ていない間のお知らせを失わない。主スレッドからだけ使う。 */
    public static NoticeTimer shared() {
        return SHARED;
    }

    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** 「起きたこと」のキーか。 */
    public static boolean isEvent(String translationKey) {
        return EVENT_KEYS.contains(translationKey);
    }

    private static final class Entry {
        final String key;
        final List<Object> args;
        final boolean operation;
        /** 表示を始めた時刻。UNSET は、先頭になって最初に問い合わせた時に決める。 */
        long startedAt;

        Entry(String key, List<Object> args, boolean operation, long startedAt) {
            this.key = key;
            this.args = args;
            this.operation = operation;
            this.startedAt = startedAt;
        }

        boolean same(String otherKey, List<Object> otherArgs) {
            return key.equals(otherKey) && args.equals(otherArgs);
        }
    }

    /** 先頭が表示中。残りが待ち（「起きたこと」だけ）。 */
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private long lastTick = UNSET;

    /** お知らせを積む。種類（操作の結果か起きたこと）はキーで決まる。 */
    public void push(String translationKey, long nowMs) {
        push(translationKey, nowMs, new Object[0]);
    }

    /** 翻訳キーの引数（%s など）つきで積む。 */
    public void push(String translationKey, long nowMs, Object... translationArgs) {
        List<Object> args = translationArgs == null ? List.of() : List.copyOf(Arrays.asList(translationArgs));
        if (isEvent(translationKey)) {
            pushEvent(translationKey, args);
        } else {
            pushOperation(translationKey, args, nowMs);
        }
    }

    private void pushOperation(String key, List<Object> args, long nowMs) {
        advance(nowMs);
        Entry head = queue.peekFirst();
        Entry returned = null;
        if (head != null) {
            queue.pollFirst();
            long shown = head.startedAt == UNSET ? 0 : nowMs - head.startedAt;
            if (!head.operation && shown < RETURN_BELOW_MS && queue.size() < MAX_WAITING) {
                // 起きたことを短い間しか見せられなかったので、操作の結果の後にもう一度出す
                head.startedAt = UNSET;
                returned = head;
            }
        }
        if (returned != null) {
            queue.addFirst(returned);
        }
        queue.addFirst(new Entry(key, args, true, nowMs));
    }

    private void pushEvent(String key, List<Object> args) {
        for (Entry e : queue) {
            if (e.same(key, args)) {
                return;
            }
        }
        if (queue.size() > MAX_WAITING) {
            // 表示中 1 ＋待ち 2 が埋まっている。一番古い待ち（表示中の次）を捨てる
            Iterator<Entry> it = queue.iterator();
            it.next();
            it.next();
            it.remove();
        }
        queue.addLast(new Entry(key, args, false, UNSET));
    }

    /** 期限が切れたものを捨て、次を先頭に進める。 */
    private void advance(long nowMs) {
        while (true) {
            Entry head = queue.peekFirst();
            if (head == null) {
                return;
            }
            if (head.startedAt == UNSET) {
                head.startedAt = nowMs;
                return;
            }
            if (nowMs - head.startedAt < DURATION_MS) {
                return;
            }
            queue.pollFirst();
        }
    }

    /** 出ているお知らせの翻訳の引数。無ければ空。 */
    public List<Object> activeArgs() {
        Entry head = queue.peekFirst();
        return head == null ? List.of() : head.args;
    }

    /** 出ているお知らせの翻訳キー。無ければ（期限切れも含む）null。 */
    public String activeKey(long nowMs) {
        advance(nowMs);
        Entry head = queue.peekFirst();
        return head == null ? null : head.key;
    }

    /** 今の不透明度（0.0〜1.0）。 */
    public float alpha(long nowMs) {
        advance(nowMs);
        Entry head = queue.peekFirst();
        if (head == null) {
            return 0f;
        }
        long remaining = DURATION_MS - (nowMs - head.startedAt);
        if (remaining >= FADE_MS) {
            return 1f;
        }
        return Math.max(0f, remaining / (float) FADE_MS);
    }

    /**
     * 画面を描くたびに呼ぶ。{@code paused} が真の間（手書きモードでない・メニューを開いていて説明の行を描かない間）と、
     * 描画の間隔が大きく空いた時（地図の画面を閉じていた間）は、表示中のお知らせの時間を進めない。
     */
    public void tick(long nowMs, boolean paused) {
        if (lastTick != UNSET) {
            long gap = nowMs - lastTick;
            Entry head = queue.peekFirst();
            if ((paused || gap > MAX_FRAME_GAP_MS) && gap > 0 && head != null && head.startedAt != UNSET) {
                head.startedAt += gap;
            }
        }
        lastTick = nowMs;
    }

    /** 待ち・表示中を含めた件数。 */
    public int size() {
        return queue.size();
    }

    /** 全部捨てる（接続を離れた時）。 */
    public void clear() {
        queue.clear();
        lastTick = UNSET;
    }
}
