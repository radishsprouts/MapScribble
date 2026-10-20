package io.github.radishsprouts.mapscribble.ui;

/** 地図上の文字の大きさ（画面設計書 3.2「選択ボタンの中身」。倍率 1.0 / 1.5 / 2.0）。 */
public enum TextSize {
    SMALL("small", 1.0f),
    MEDIUM("medium", 1.5f),
    LARGE("large", 2.0f);

    private final String key;
    private final float scale;

    TextSize(String key, float scale) {
        this.key = key;
        this.scale = scale;
    }

    public float scale() {
        return scale;
    }

    public String translationKey() {
        return "mapscribble.size." + key;
    }

    /** 倍率に一番近い段階。 */
    public static TextSize nearest(float scale) {
        TextSize best = MEDIUM;
        float bestDiff = Float.MAX_VALUE;
        for (TextSize size : values()) {
            float diff = Math.abs(size.scale - scale);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = size;
            }
        }
        return best;
    }
}
