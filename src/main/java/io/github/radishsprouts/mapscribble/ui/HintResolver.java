package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.Locale;

/**
 * 帯の説明の行に何を出すかを決める（画面設計書 3.1「説明の行」の優先順）。純粋な計算。
 *
 * <p>設計書の優先 4「読み込み中」は、メモの読み込みが同期（切り替わった時に主スレッドで読み切る）で、
 * 読み込み中の状態が存在しないため、ここには入れていない（文言だけ lang にある）。
 */
public final class HintResolver {
    public static final int COLOR_WHITE = 0xFFFFFF;
    public static final int COLOR_WARNING = 0xFFE600;
    public static final int COLOR_FAILURE = 0xFF8080;

    /** 説明の行に出す 1 行。 */
    public record Line(String translationKey, int rgb) {
    }

    /**
     * @param loadFailed      メモのファイルを読めなかった（知らない形式・壊れている）
     * @param saveFailed      保存に失敗した
     * @param notReady        保存先が未確定（地図の準備中）。読めなかった場合は含めない
     * @param noticeKey       一時のお知らせの翻訳キー。無ければ null
     * @param hidden          メモを隠している
     * @param emptyAndEnclose このディメンションにメモが 0 件で、ツールが囲み
     */
    public record Input(boolean loadFailed, boolean saveFailed, boolean notReady, String noticeKey, boolean hidden,
                        boolean emptyAndEnclose, Tool tool) {
    }

    private HintResolver() {
    }

    public static Line resolve(Input in) {
        if (in.loadFailed()) {
            return new Line("mapscribble.msg.load_failed", COLOR_FAILURE);
        }
        if (in.saveFailed()) {
            return new Line("mapscribble.msg.save_failed", COLOR_FAILURE);
        }
        if (in.notReady()) {
            return new Line("mapscribble.msg.not_ready", COLOR_WARNING);
        }
        if (in.noticeKey() != null) {
            return new Line(in.noticeKey(), COLOR_WHITE);
        }
        if (in.hidden()) {
            return new Line("mapscribble.msg.hidden", COLOR_WARNING);
        }
        if (in.emptyAndEnclose()) {
            return new Line("mapscribble.msg.empty", COLOR_WHITE);
        }
        return new Line(hintKey(in.tool()), COLOR_WHITE);
    }

    /** ツールの説明（通常時）の翻訳キー。 */
    public static String hintKey(Tool tool) {
        return "mapscribble.hint." + tool.name().toLowerCase(Locale.ROOT);
    }

    /** ツールの名前の翻訳キー。 */
    public static String toolKey(Tool tool) {
        return "mapscribble.tool." + tool.name().toLowerCase(Locale.ROOT);
    }
}
