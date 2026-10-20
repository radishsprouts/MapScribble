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

    @Test
    void 翻訳キーはツール名から作る() {
        assertEquals("mapscribble.hint.eraser", HintResolver.hintKey(Tool.ERASER));
        assertEquals("mapscribble.tool.enclose", HintResolver.toolKey(Tool.ENCLOSE));
    }
}
