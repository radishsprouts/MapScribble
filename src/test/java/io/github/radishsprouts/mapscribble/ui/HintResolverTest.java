package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.radishsprouts.mapscribble.draw.Tool;
import org.junit.jupiter.api.Test;

class HintResolverTest {
    private static HintResolver.Line resolve(boolean load, boolean save, boolean notReady, String notice,
                                             boolean hidden, boolean empty) {
        return HintResolver.resolve(new HintResolver.Input(load, save, notReady, notice, hidden, empty, Tool.LINE));
    }

    @Test
    void 何も無ければツールの説明() {
        var line = resolve(false, false, false, null, false, false);
        assertEquals("mapscribble.hint.line", line.translationKey());
        assertEquals(HintResolver.COLOR_WHITE, line.rgb());
    }

    @Test
    void 読み込み失敗が最優先で赤い() {
        var line = resolve(true, true, true, "mapscribble.msg.undone", true, true);
        assertEquals("mapscribble.msg.load_failed", line.translationKey());
        assertEquals(HintResolver.COLOR_FAILURE, line.rgb());
    }

    @Test
    void 優先順位は_保存失敗_準備中_お知らせ_隠し中_空() {
        assertEquals("mapscribble.msg.save_failed",
                resolve(false, true, true, "n", true, true).translationKey());
        assertEquals("mapscribble.msg.not_ready",
                resolve(false, false, true, "n", true, true).translationKey());
        assertEquals("n", resolve(false, false, false, "n", true, true).translationKey());
        assertEquals("mapscribble.msg.hidden", resolve(false, false, false, null, true, true).translationKey());
        assertEquals("mapscribble.msg.empty", resolve(false, false, false, null, false, true).translationKey());
    }

    @Test
    void 隠し中と準備中は黄色() {
        assertEquals(HintResolver.COLOR_WARNING, resolve(false, false, true, null, false, false).rgb());
        assertEquals(HintResolver.COLOR_WARNING, resolve(false, false, false, null, true, false).rgb());
    }

    private static HintResolver.Line resolveFull(boolean notReady, boolean loading, String notice,
                                                 java.util.List<Object> args) {
        return HintResolver.resolve(new HintResolver.Input(false, false, notReady, loading, notice, args, false, false,
                Tool.LINE));
    }

    @Test
    void お知らせの色はキーごと() {
        assertEquals(HintResolver.COLOR_WARNING, resolveFull(false, false, HintResolver.NOTICE_NO_WORLD_ID,
                java.util.List.of()).rgb());
        assertEquals(HintResolver.COLOR_FAILURE, resolveFull(false, false, HintResolver.NOTICE_MOVE_FAILED,
                java.util.List.of()).rgb());
        for (String key : new String[] {HintResolver.NOTICE_SPLIT_STARTED, HintResolver.NOTICE_JM010_IMPORTED,
                HintResolver.NOTICE_NOTEBOOK_SWITCHED, HintResolver.NOTICE_MOVED, HintResolver.NOTICE_MOVE_UNDONE,
                "mapscribble.msg.move_busy", "mapscribble.msg.undone"}) {
            assertEquals(HintResolver.COLOR_WHITE, resolveFull(false, false, key, java.util.List.of()).rgb(), key);
        }
    }

    @Test
    void お知らせの引数が行に渡る() {
        var line = resolveFull(false, false, HintResolver.NOTICE_NOTEBOOK_SWITCHED, java.util.List.of("メモ帳 1"));
        assertEquals(java.util.List.of("メモ帳 1"), line.args());
        assertEquals(java.util.List.of(), resolve(false, false, false, null, false, false).args());
    }

    @Test
    void 読み込み中は準備中より後_お知らせより先で灰色() {
        assertEquals("mapscribble.msg.not_ready", resolveFull(true, true, "n", java.util.List.of()).translationKey());
        var line = resolveFull(false, true, "n", java.util.List.of());
        assertEquals("mapscribble.msg.loading", line.translationKey());
        assertEquals(HintResolver.COLOR_GRAY, line.rgb());
        assertEquals(0xA0A0A0, HintResolver.COLOR_GRAY);
    }

    @Test
    void 翻訳キーはツール名から作る() {
        assertEquals("mapscribble.hint.eraser", HintResolver.hintKey(Tool.ERASER));
        assertEquals("mapscribble.tool.enclose", HintResolver.toolKey(Tool.ENCLOSE));
    }
}
