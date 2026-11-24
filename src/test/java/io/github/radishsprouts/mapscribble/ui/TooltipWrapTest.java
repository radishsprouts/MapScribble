package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/** ツールチップの折り返し（設計書 3.6・3.7）。GUI 幅 320（640x480・倍率 2）で、どの行も幅以内に収まること。 */
class TooltipWrapTest {
    /** 半角 6・全角 12 の幅（バニラの文字幅に近い値）。 */
    private static final ToIntFunction<String> WIDTH = s -> {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += cp < 0x2E80 ? 6 : 12;
            i += Character.charCount(cp);
        }
        return w;
    };

    @Test
    void 幅は286と画面幅マイナス24の小さい方() {
        assertEquals(286, TooltipWrap.maxWidth(320));
        assertEquals(286, TooltipWrap.maxWidth(1000));
        assertEquals(176, TooltipWrap.maxWidth(200));
    }

    @Test
    void 長いアドレスはどの行も幅以内に折り_文字は失われない() {
        int max = TooltipWrap.maxWidth(320);
        String address = "very-long-subdomain-name.another-long-label.example-server-network.example.com:25565";
        List<String> lines = TooltipWrap.wrap(address, max, WIDTH);
        assertTrue(lines.size() > 1);
        for (String line : lines) {
            assertTrue(WIDTH.applyAsInt(line) <= max, line);
        }
        assertEquals(address, String.join("", lines));
    }

    @Test
    void 空白のある文は語の切れ目で折り_長い語だけ文字単位で折る() {
        int max = TooltipWrap.maxWidth(320);
        String text = "Server address list " + "x".repeat(120) + " end of the sentence";
        List<String> lines = TooltipWrap.wrap(text, max, WIDTH);
        for (String line : lines) {
            assertTrue(WIDTH.applyAsInt(line) <= max, line);
        }
        assertEquals("Server address list", lines.get(0));
    }

    @Test
    void 全角の長い名前も文字単位で幅以内に折る() {
        int max = TooltipWrap.maxWidth(320);
        String name = "とても長い名前のメモ帳".repeat(8);
        List<String> lines = TooltipWrap.wrap(name, max, WIDTH);
        assertTrue(lines.size() > 1);
        for (String line : lines) {
            assertTrue(WIDTH.applyAsInt(line) <= max, line);
        }
        assertEquals(name, String.join("", lines));
    }

    @Test
    void 短い文は1行のまま() {
        int max = TooltipWrap.maxWidth(320);
        for (String s : List.of("サーバーごとに分けている時だけ使えます", "Only when split by server", "接続先：")) {
            assertEquals(List.of(s), TooltipWrap.wrap(s, max, WIDTH));
        }
    }

    @Test
    void 改行は段落の区切り() {
        assertEquals(List.of("a", "b"), TooltipWrap.wrap("a\nb", 286, WIDTH));
    }
}
