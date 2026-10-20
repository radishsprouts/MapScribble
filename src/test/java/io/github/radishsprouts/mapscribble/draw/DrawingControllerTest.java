package io.github.radishsprouts.mapscribble.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DrawingControllerTest {
    /** メモリ上だけの窓口。 */
    private static final class FakeRepository implements MemoRepository {
        boolean available = true;
        final List<Memo> list = new ArrayList<>();
        final List<Runnable> listeners = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public List<Memo> memos() {
            return List.copyOf(list);
        }

        @Override
        public void add(Memo memo) {
            list.add(memo);
        }

        @Override
        public void update(Memo memo) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(memo.id())) {
                    list.set(i, memo);
                }
            }
        }

        @Override
        public void remove(UUID id) {
            list.removeIf(m -> m.id().equals(id));
        }

        @Override
        public void addListener(Runnable listener) {
            listeners.add(listener);
        }
    }

    private static final Projection ONE = new Projection(0, 0, 1.0, 1000, 1000);

    private FakeRepository repo;
    private DrawingState state;
    private DrawingController controller;
    private final List<String> notices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repo = new FakeRepository();
        state = new DrawingState();
        state.setNoticeHandler(notices::add);
        state.setModeOn(true);
        controller = new DrawingController(state, () -> repo);
    }

    private static Memo memo(String text) {
        Instant now = Instant.now();
        return new Memo(UUID.randomUUID(), MemoKind.REGION,
                List.of(new MapPoint(0, 0), new MapPoint(10, 0), new MapPoint(10, 10)), 0xFF6E61, 2, 0.3f, text, 1.5f,
                true, now, now);
    }

    @Test
    void 追加_取り消し_やり直し() {
        Memo m = memo("a");
        controller.add(m);
        assertEquals(1, repo.list.size());
        assertTrue(controller.undo());
        assertEquals(0, repo.list.size());
        assertTrue(controller.redo());
        assertEquals(List.of(m), repo.list);
        assertEquals(List.of(DrawingController.NOTICE_UNDONE, DrawingController.NOTICE_REDONE), notices);
    }

    @Test
    void 変更は前の状態へ戻る() {
        Memo before = memo("前");
        controller.add(before);
        Memo after = new Memo(before.id(), before.kind(), before.points(), 0x7CFC00, 3, 0.5f, "後", 2.0f, false,
                before.created(), before.updated());
        controller.update(before, after);
        assertEquals("後", repo.list.get(0).text());
        controller.undo();
        assertEquals("前", repo.list.get(0).text());
        assertEquals(0xFF6E61, repo.list.get(0).color());
        controller.redo();
        assertEquals("後", repo.list.get(0).text());
    }

    @Test
    void 削除は取り消しで復活する() {
        Memo m = memo("消す");
        controller.add(m);
        controller.remove(m);
        assertEquals(0, repo.list.size());
        assertTrue(notices.contains(DrawingController.NOTICE_DELETED));
        controller.undo();
        assertEquals(List.of(m), repo.list);
        controller.redo();
        assertEquals(0, repo.list.size());
    }

    @Test
    void 履歴は100件までで_それより前へは戻れない() {
        for (int i = 0; i < 130; i++) {
            controller.add(memo("m" + i));
        }
        int undone = 0;
        while (controller.undo()) {
            undone++;
        }
        assertEquals(100, undone);
        assertEquals(30, repo.list.size());
    }

    @Test
    void ディメンションが切り替わると履歴を捨てる() {
        controller.add(memo("a"));
        controller.onDimensionChanged();
        assertFalse(controller.canUndo());
        assertFalse(controller.undo());
    }

    @Test
    void 窓口が使えない間は何もしない() {
        repo.available = false;
        assertFalse(controller.isActive());
        controller.add(memo("a"));
        assertEquals(0, repo.list.size());
        assertFalse(controller.undo());
        state.setModeOn(false);
        repo.available = true;
        assertFalse(controller.isActive());
    }

    @Test
    void 隠している時に追加すると表示に戻す() {
        state.setVisible(false);
        controller.add(memo("a"));
        assertTrue(state.isVisible());
    }

    // ---- 一筆 ----

    private void stroke(double... xy) {
        controller.beginStroke(xy[0], xy[1], ONE);
        for (int i = 2; i < xy.length; i += 2) {
            controller.extendStroke(xy[i], xy[i + 1], ONE);
        }
    }

    @Test
    void 囲みを描いて離すと文字なしのメモが追加される() {
        state.setTool(Tool.ENCLOSE);
        stroke(100, 100, 200, 100, 200, 200, 100, 200);
        assertNotNull(controller.activeStroke());
        assertTrue(controller.finishStroke(ONE));
        assertNull(controller.activeStroke());
        assertEquals(1, repo.list.size());
        Memo m = repo.list.get(0);
        assertEquals(MemoKind.REGION, m.kind());
        assertEquals("", m.text());
        assertEquals(state.color(), m.color());
    }

    @Test
    void 小さすぎる囲みは捨ててお知らせを出す() {
        state.setTool(Tool.ENCLOSE);
        stroke(100, 100);
        assertFalse(controller.finishStroke(ONE));
        assertEquals(0, repo.list.size());
        assertEquals(List.of(DrawingController.NOTICE_TOO_SMALL), notices);
    }

    @Test
    void 短すぎる線は捨ててお知らせを出す() {
        state.setTool(Tool.LINE);
        stroke(100, 100);
        assertFalse(controller.finishStroke(ONE));
        assertEquals(0, repo.list.size());
        assertEquals(List.of(DrawingController.NOTICE_TOO_SHORT), notices);
    }

    @Test
    void 線は点が2つ以上あれば追加される() {
        state.setTool(Tool.LINE);
        stroke(100, 100, 150, 120, 200, 100);
        assertTrue(controller.finishStroke(ONE));
        assertEquals(MemoKind.LINE, repo.list.get(0).kind());
    }

    @Test
    void 確定後の処理を差し替えられる() {
        List<Draft> drafts = new ArrayList<>();
        state.setDraftHandler(drafts::add);
        state.setTool(Tool.ENCLOSE);
        stroke(100, 100, 200, 100, 200, 200);
        controller.finishStroke(ONE);
        assertEquals(1, drafts.size());
        assertEquals(0, repo.list.size(), "差し替えた処理が追加するまでメモは増えない");
        // 文字入力の画面が確定した体で追加
        controller.add(drafts.get(0).toMemo("文字"));
        assertEquals("文字", repo.list.get(0).text());
    }

    @Test
    void 文字ツールは置いた位置に1点のメモを作る() {
        state.setTool(Tool.TEXT);
        controller.placeText(12.5, -3.25);
        Memo m = repo.list.get(0);
        assertEquals(MemoKind.TEXT, m.kind());
        assertEquals(new MapPoint(12.5, -3.25), m.points().get(0));
        assertFalse(m.text().isEmpty(), "文字のメモは見えなくならないよう仮の文字を持つ");
    }

    @Test
    void 消しゴムと編集は当たらなければお知らせを出す() {
        controller.erase(null);
        controller.requestEdit(null);
        assertEquals(List.of(DrawingController.NOTICE_NO_NOTE, DrawingController.NOTICE_NO_NOTE), notices);
        List<Memo> requested = new ArrayList<>();
        state.setEditHandler(requested::add);
        Memo m = memo("x");
        controller.requestEdit(m);
        assertEquals(List.of(m), requested);
    }

    @Test
    void 帯の上かどうかは外から渡せる() {
        state.setBandHitTest((x, y) -> x < 100 && y < 50);
        assertTrue(state.isOverBand(10, 10));
        assertFalse(state.isOverBand(150, 10));
    }
}
