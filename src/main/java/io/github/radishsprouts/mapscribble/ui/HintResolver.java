package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.List;
import java.util.Locale;

/**
 * 帯の説明の行に何を出すかを決める（画面設計書 3.1「説明の行」の優先順）。純粋な計算。
 *
 * <p>優先 4「読み込み中」は、0.1.0 のメモをメモ帳として取り込んでいる間（{@code StoreSession.isNotebookLoading()}）に出す。
 */
public final class HintResolver {
    public static final int COLOR_WHITE = 0xFFFFFF;
    public static final int COLOR_WARNING = 0xFFE600;
    public static final int COLOR_FAILURE = 0xFF8080;
    public static final int COLOR_GRAY = 0xA0A0A0;

    /** 0.4.0 で足した、説明の行のお知らせ（設計書 3.9）。 */
    public static final String NOTICE_NO_WORLD_ID = "mapscribble.msg.no_world_id";
    public static final String NOTICE_MOVE_FAILED = "mapscribble.msg.move_failed";
    public static final String NOTICE_SPLIT_STARTED = "mapscribble.msg.split_started";
    public static final String NOTICE_JM010_IMPORTED = "mapscribble.msg.jm010_imported";
    public static final String NOTICE_NOTEBOOK_SWITCHED = "mapscribble.msg.notebook_switched";
    public static final String NOTICE_MOVED = "mapscribble.msg.moved";
    public static final String NOTICE_MOVE_UNDONE = "mapscribble.msg.move_undone";

    /**
     * 説明の行に出す 1 行。
     *
     * @param args 翻訳キーの引数（%s）。無ければ空。配列は record の equals が効かないので List で持つ
     */
    public record Line(String translationKey, int rgb, List<Object> args) {
        public Line(String translationKey, int rgb) {
            this(translationKey, rgb, List.of());
        }
    }

    /**
     * @param loadFailed      メモのファイルを読めなかった（知らない形式・壊れている）
     * @param saveFailed      保存に失敗した
     * @param notReady        保存先が未確定（地図の準備中）。読めなかった場合は含めない
     * @param noticeKey       一時のお知らせの翻訳キー。無ければ null
     * @param noticeArgs      一時のお知らせの翻訳の引数。無ければ空
     * @param hidden          メモを隠している
     * @param emptyAndEnclose このディメンションにメモが 0 件で、ツールが囲み
     * @param loading         メモ帳を読み込み・取り込み中（優先 4）。地図の準備中とは別
     */
    public record Input(boolean loadFailed, boolean saveFailed, boolean notReady, boolean loading, String noticeKey,
                        List<Object> noticeArgs, boolean hidden, boolean emptyAndEnclose, Tool tool) {
        public Input(boolean loadFailed, boolean saveFailed, boolean notReady, String noticeKey, boolean hidden,
                     boolean emptyAndEnclose, Tool tool) {
            this(loadFailed, saveFailed, notReady, false, noticeKey, List.of(), hidden, emptyAndEnclose, tool);
        }
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
        if (in.loading()) {
            return new Line("mapscribble.msg.loading", COLOR_GRAY);
        }
        if (in.noticeKey() != null) {
            return new Line(in.noticeKey(), noticeColor(in.noticeKey()), in.noticeArgs());
        }
        if (in.hidden()) {
            return new Line("mapscribble.msg.hidden", COLOR_WARNING);
        }
        if (in.emptyAndEnclose()) {
            return new Line("mapscribble.msg.empty", COLOR_WHITE);
        }
        return new Line(hintKey(in.tool()), COLOR_WHITE);
    }

    /** 一時のお知らせの文字色（設計書 3.9）。ワールドを見分けられない時は黄、移すのに失敗した時は赤系、ほかは白。 */
    public static int noticeColor(String key) {
        return switch (key) {
            case NOTICE_NO_WORLD_ID -> COLOR_WARNING;
            case NOTICE_MOVE_FAILED -> COLOR_FAILURE;
            default -> COLOR_WHITE;
        };
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
