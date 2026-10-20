package io.github.radishsprouts.mapscribble.draw;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HitTesterTest {
    /** 1 文字 6px、行の高さ 9px。 */
    private static final HitTester.TextMeasure MEASURE = new HitTester.TextMeasure() {
        @Override
        public int width(String line) {
            return line.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };

    private static Memo memo(MemoKind kind, String text, MapPoint... points) {
        Instant now = Instant.EPOCH;
        return new Memo(UUID.randomUUID(), kind, List.of(points), 0xFF6E61, 2, 0.3f, text, 1.5f, true, now, now);
    }

    private static Memo square() {
        return memo(MemoKind.REGION, "", new MapPoint(0, 0), new MapPoint(20, 0), new MapPoint(20, 20),
                new MapPoint(0, 20));
    }

    @Test
    void 囲みの内側と輪郭の近くは当たり_離れた所は当たらない() {
        Memo region = square();
        List<Memo> memos = List.of(region);
        assertSame(region, HitTester.find(memos, 10, 10, 1.0, 2.0, MEASURE));
        // 輪郭のすぐ外（許容 6 ブロック以内）
        assertSame(region, HitTester.find(memos, 23, 10, 1.0, 2.0, MEASURE));
        assertNull(HitTester.find(memos, 30, 10, 1.0, 2.0, MEASURE));
    }

    @Test
    void 許容距離は画面のピクセルで決まり_拡大すると小さくなる() {
        Memo region = square();
        List<Memo> memos = List.of(region);
        // 1 ピクセルが 0.1 ブロック（拡大）なら許容は 0.6 ブロック。2 ブロック外は当たらない
        assertNull(HitTester.find(memos, 22, 10, 0.1, 2.0, MEASURE));
        assertSame(region, HitTester.find(memos, 20.5, 10, 0.1, 2.0, MEASURE));
    }

    @Test
    void 線は線の近くだけ当たる() {
        Memo line = memo(MemoKind.LINE, "", new MapPoint(0, 0), new MapPoint(100, 0));
        List<Memo> memos = List.of(line);
        assertSame(line, HitTester.find(memos, 50, 3, 1.0, 2.0, MEASURE));
        assertNull(HitTester.find(memos, 50, 8, 1.0, 2.0, MEASURE));
        // 線の端の延長線上は当たらない
        assertNull(HitTester.find(memos, 120, 0, 1.0, 2.0, MEASURE));
    }

    @Test
    void 文字は表示の矩形に当たる() {
        Memo text = memo(MemoKind.TEXT, "hello", new MapPoint(50, 50));
        List<Memo> memos = List.of(text);
        // 幅 30px × 倍率 1.5 の半分 = 22.5 + 余白 2、GUI 1px = 0.5 ブロック（worldPerPx 0.25 × guiScale 2）
        assertSame(text, HitTester.find(memos, 50, 50, 0.25, 2.0, MEASURE));
        assertSame(text, HitTester.find(memos, 50 + 11, 50, 0.25, 2.0, MEASURE));
        assertNull(HitTester.find(memos, 50 + 13, 50, 0.25, 2.0, MEASURE));
    }

    @Test
    void 複数行の文字は一番長い行の幅と行数で範囲が決まる() {
        Memo text = memo(MemoKind.TEXT, "a\nabcdef\nab", new MapPoint(0, 0));
        List<Memo> memos = List.of(text);
        // 最長 6 文字 = 36px、3 行 = 27px。倍率 1.5。GUI 1px = 1 ブロック
        assertSame(text, HitTester.find(memos, 0, 19, 1.0, 1.0, MEASURE));
        assertNull(HitTester.find(memos, 0, 25, 1.0, 1.0, MEASURE));
    }

    @Test
    void 重なっていれば最後に描いたものを選ぶ() {
        Memo below = square();
        Memo above = memo(MemoKind.REGION, "", new MapPoint(5, 5), new MapPoint(15, 5), new MapPoint(15, 15),
                new MapPoint(5, 15));
        assertSame(above, HitTester.find(List.of(below, above), 10, 10, 1.0, 2.0, MEASURE));
        // (0.5, 0.5) から上のメモの輪郭（5, 5）までは約 6.4 で、許容の 6 ブロックを超える。下のメモの内側だけに当たる
        assertSame(below, HitTester.find(List.of(below, above), 0.5, 0.5, 1.0, 2.0, MEASURE));
    }

    @Test
    void メモが無ければ何にも当たらない() {
        assertNull(HitTester.find(List.of(), 0, 0, 1.0, 2.0, MEASURE));
    }

    @Test
    void 点を持たない文字のメモは当たらない() {
        Memo broken = memo(MemoKind.TEXT, "x");
        assertNull(HitTester.find(List.of(broken), 0, 0, 1.0, 2.0, MEASURE));
    }
}
