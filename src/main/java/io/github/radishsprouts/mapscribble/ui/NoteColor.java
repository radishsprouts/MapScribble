package io.github.radishsprouts.mapscribble.ui;

import java.util.List;

/** メモの色（画面設計書 3.4 の 10 色。順序も設計書のとおり）。 */
public enum NoteColor {
    RED("red", 0xFF6E61),
    ORANGE("orange", 0xFF9500),
    YELLOW("yellow", 0xFFE600),
    LIME("lime", 0x7CFC00),
    AQUA("aqua", 0x32D2FF),
    BLUE("blue", 0x5C9DFF),
    PURPLE("purple", 0xC584FF),
    PINK("pink", 0xFF6FD0),
    WHITE("white", 0xFFFFFF),
    BLACK("black", 0x1A1A1A);

    private static final List<NoteColor> ALL = List.of(values());

    private final String key;
    private final int rgb;

    /** 設計書の順の一覧。毎フレーム呼んでも配列を作らない。 */
    public static List<NoteColor> list() {
        return ALL;
    }

    NoteColor(String key, int rgb) {
        this.key = key;
        this.rgb = rgb;
    }

    public int rgb() {
        return rgb;
    }

    /** 色の名前の翻訳キー。 */
    public String translationKey() {
        return "mapscribble.color." + key;
    }

    /** 0xRRGGBB に一致する色。一覧に無ければ null（外から読んだメモの色など）。 */
    public static NoteColor of(int rgb) {
        int masked = rgb & 0xFFFFFF;
        for (NoteColor c : values()) {
            if (c.rgb == masked) {
                return c;
            }
        }
        return null;
    }
}
