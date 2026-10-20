package io.github.radishsprouts.mapscribble.ui;

/**
 * 一時のお知らせ（帯の説明の行に 4 秒出る）。最後の 0.5 秒で不透明度を 1 → 0 にする（画面設計書 4 の「動き」）。
 * 時刻は呼び出し側が渡す（試験できるようにするため）。
 */
public final class NoticeTimer {
    public static final long DURATION_MS = 4000;
    public static final long FADE_MS = 500;

    private String key;
    private long startedAt;

    /** 新しいお知らせで置き換える。 */
    public void push(String translationKey, long nowMs) {
        this.key = translationKey;
        this.startedAt = nowMs;
    }

    /** 出ているお知らせの翻訳キー。無ければ（期限切れも含む）null。 */
    public String activeKey(long nowMs) {
        if (key == null) {
            return null;
        }
        if (nowMs - startedAt >= DURATION_MS) {
            key = null;
            return null;
        }
        return key;
    }

    /** 今の不透明度（0.0〜1.0）。 */
    public float alpha(long nowMs) {
        long remaining = DURATION_MS - (nowMs - startedAt);
        if (remaining >= FADE_MS) {
            return 1f;
        }
        return Math.max(0f, remaining / (float) FADE_MS);
    }

    public void clear() {
        key = null;
    }
}
