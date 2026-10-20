package io.github.radishsprouts.mapscribble.ui;

/** 囲みの塗りの濃さ（画面設計書 3.2「選択ボタンの中身」）。 */
public enum FillLevel {
    NONE("none", 0.00f),
    LIGHT("light", 0.15f),
    NORMAL("normal", 0.30f),
    STRONG("strong", 0.50f);

    private final String key;
    private final float opacity;

    FillLevel(String key, float opacity) {
        this.key = key;
        this.opacity = opacity;
    }

    public float opacity() {
        return opacity;
    }

    public String translationKey() {
        return "mapscribble.fill." + key;
    }

    /** 不透明度に一番近い段階（外から読んだメモの値が段階に無くても選べるように）。 */
    public static FillLevel nearest(float opacity) {
        FillLevel best = NORMAL;
        float bestDiff = Float.MAX_VALUE;
        for (FillLevel level : values()) {
            float diff = Math.abs(level.opacity - opacity);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = level;
            }
        }
        return best;
    }
}
