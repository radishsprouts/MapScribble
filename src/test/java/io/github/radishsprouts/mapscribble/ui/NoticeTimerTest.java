package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeTimerTest {
    private static final String OP = "mapscribble.msg.undone";
    private static final String OP2 = "mapscribble.msg.redone";
    private static final String A = HintResolver.NOTICE_SPLIT_STARTED;
    private static final String B = HintResolver.NOTICE_NOTEBOOK_SWITCHED;
    private static final String C = HintResolver.NOTICE_MOVE_UNDONE;
    private static final String D = HintResolver.NOTICE_JM010_IMPORTED;

    @Test
    void 四秒間出て_その後は消える() {
        NoticeTimer t = new NoticeTimer();
        t.push("k", 1000);
        assertEquals("k", t.activeKey(1000));
        assertEquals("k", t.activeKey(4999));
        assertNull(t.activeKey(5000));
        assertNull(t.activeKey(5001));
    }

    @Test
    void 最後の半秒で透明になる() {
        NoticeTimer t = new NoticeTimer();
        t.push("k", 0);
        assertEquals(1f, t.alpha(0));
        assertEquals(1f, t.alpha(3500));
        assertEquals(0.5f, t.alpha(3750), 0.001f);
        assertEquals(0f, t.alpha(4000), 0.001f);
        assertEquals(0f, t.alpha(9000));
    }

    @Test
    void 操作の結果は待たせず置き換える_clearで消える() {
        NoticeTimer t = new NoticeTimer();
        t.push(OP, 0);
        t.push(OP2, 100);
        assertEquals(OP2, t.activeKey(100));
        assertEquals(OP2, t.activeKey(4050));
        assertNull(t.activeKey(4100));
        t.push(OP, 5000);
        t.clear();
        assertNull(t.activeKey(5000));
    }

    @Test
    void 操作の結果を続けて出しても_最後から四秒で消える() {
        NoticeTimer t = new NoticeTimer();
        for (int i = 0; i < 5; i++) {
            t.push(OP, i * 500L);
        }
        assertEquals(OP, t.activeKey(2000 + 3999));
        assertNull(t.activeKey(2000 + 4000));
    }

    @Test
    void 起きたことは順に出る() {
        NoticeTimer t = new NoticeTimer();
        t.push(A, 0);
        t.push(B, 0, "メモ帳 1");
        assertEquals(A, t.activeKey(0));
        assertEquals(A, t.activeKey(3999));
        assertEquals(B, t.activeKey(4000));
        assertEquals(List.of("メモ帳 1"), t.activeArgs());
        assertNull(t.activeKey(8000));
    }

    @Test
    void 待ちは最大2件で_3件目が来たら一番古い待ちを捨てる() {
        NoticeTimer t = new NoticeTimer();
        t.push(A, 0);
        t.push(B, 0, "x");
        t.push(C, 0);
        t.push(D, 0);
        assertEquals(A, t.activeKey(0));
        assertEquals(3, t.size());
        assertEquals(C, t.activeKey(4000));
        assertEquals(D, t.activeKey(8000));
        assertNull(t.activeKey(12000));
    }

    @Test
    void 同じキーと同じ引数の起きたことは足さない() {
        NoticeTimer t = new NoticeTimer();
        t.push(B, 0, "a");
        t.push(B, 0, "a");
        assertEquals(1, t.size());
        t.push(B, 0, "b");
        assertEquals(2, t.size());
    }

    @Test
    void 帯が出ていない間は四秒を数えない() {
        NoticeTimer t = new NoticeTimer();
        // 帯が出ていない間に起きた（まだ誰も問い合わせていない）
        t.push(A, 1000);
        // ずっと後に、帯を出した時に初めて出て、そこから四秒
        assertEquals(A, t.activeKey(100000));
        assertEquals(A, t.activeKey(103999));
        assertNull(t.activeKey(104000));
    }

    @Test
    void 描画が止まっていた間は数えない() {
        NoticeTimer t = new NoticeTimer();
        t.tick(0, false);
        t.push(A, 0);
        assertEquals(A, t.activeKey(0));
        t.tick(100, false);
        // 地図を閉じていて 10 秒描画が無かった
        t.tick(10100, false);
        assertEquals(A, t.activeKey(10100));
        assertEquals(A, t.activeKey(10100 + 3899));
        assertNull(t.activeKey(10100 + 3900));
    }

    @Test
    void 一時停止の間は数えない() {
        NoticeTimer t = new NoticeTimer();
        t.tick(0, false);
        t.push(OP, 0);
        for (long now = 100; now <= 3000; now += 100) {
            t.tick(now, true);
        }
        assertEquals(OP, t.activeKey(3000));
        assertEquals(OP, t.activeKey(6999));
        assertNull(t.activeKey(7000));
    }

    @Test
    void 起きたことが二秒未満で操作の結果に置き換えられたら_待ちの先頭へ戻す() {
        NoticeTimer t = new NoticeTimer();
        t.push(A, 0);
        assertEquals(A, t.activeKey(0));
        t.push(OP, 1500);
        assertEquals(OP, t.activeKey(1500));
        // 操作の結果の後に、改めて四秒出る
        assertEquals(A, t.activeKey(1500 + 4000));
        assertEquals(A, t.activeKey(1500 + 7999));
        assertNull(t.activeKey(1500 + 8000));
    }

    @Test
    void 起きたことが二秒以上出ていたら_置き換えられても戻さない() {
        NoticeTimer t = new NoticeTimer();
        t.push(A, 0);
        assertEquals(A, t.activeKey(0));
        t.push(OP, 2000);
        assertEquals(OP, t.activeKey(2000));
        assertNull(t.activeKey(6000));
    }

    @Test
    void 操作の結果で置き換えても_待っている起きたことは残る() {
        NoticeTimer t = new NoticeTimer();
        t.push(A, 0);
        t.push(B, 0, "x");
        assertEquals(A, t.activeKey(0));
        t.push(OP, 3000);
        assertEquals(OP, t.activeKey(3000));
        assertEquals(B, t.activeKey(7000));
    }

    @Test
    void 接続を離れたらclearで全部捨てる() {
        NoticeTimer t = new NoticeTimer();
        t.push(A, 0);
        t.push(B, 0, "旧サーバーのメモ帳");
        t.clear();
        assertNull(t.activeKey(0));
        assertEquals(0, t.size());
        t.push(D, 10);
        assertEquals(D, t.activeKey(10));
    }

    @Test
    void 起きたことのキーの分類() {
        assertEquals(true, NoticeTimer.isEvent(HintResolver.NOTICE_MOVE_FAILED));
        assertEquals(true, NoticeTimer.isEvent(HintResolver.NOTICE_MOVED));
        assertEquals(false, NoticeTimer.isEvent("mapscribble.msg.move_busy"));
        assertEquals(false, NoticeTimer.isEvent(OP));
    }

    /** サーバーを移って元に戻すの履歴を捨てた後に、「Ctrl+Z で戻せます」の知らせを残さない。 */
    @Test
    void 開き直すと_Ctrl_Zを案内する知らせだけを捨てる() {
        NoticeTimer t = new NoticeTimer();
        t.push(HintResolver.NOTICE_MOVED, 0, 4);
        t.push(A, 0);
        assertEquals(HintResolver.NOTICE_MOVED, t.activeKey(0));
        t.discardUndoHints();
        assertEquals(A, t.activeKey(0));
        assertEquals(1, t.size());

        NoticeTimer d = new NoticeTimer();
        d.push("mapscribble.msg.deleted", 0);
        d.discardUndoHints();
        assertNull(d.activeKey(0));

        NoticeTimer u = new NoticeTimer();
        u.push(OP, 0);
        u.discardUndoHints();
        assertEquals(OP, u.activeKey(0));
    }
}
