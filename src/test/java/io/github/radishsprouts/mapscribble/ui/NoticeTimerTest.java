package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NoticeTimerTest {
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
    void 新しいお知らせは置き換え_clearで消える() {
        NoticeTimer t = new NoticeTimer();
        t.push("a", 0);
        t.push("b", 100);
        assertEquals("b", t.activeKey(4050));
        t.clear();
        assertNull(t.activeKey(4050));
    }
}
