package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.geom.Shapes;
import io.github.radishsprouts.mapscribble.geom.Simplify;
import io.github.radishsprouts.mapscribble.geom.StrokeSampler;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * メモの追加・変更・削除と取り消し、描いている最中の一筆を扱う。
 * メモの読み書きは {@link MemoRepository} だけを通す。Minecraft の主スレッドからだけ使う。
 */
public final class DrawingController {
    /** 一筆を確定した後、既定の処理が付ける文字のメモの文字（文字入力の画面ができるまでの仮）。 */
    static final String DEFAULT_TEXT_MEMO = "メモ";

    public static final String NOTICE_UNDONE = "mapscribble.msg.undone";
    public static final String NOTICE_REDONE = "mapscribble.msg.redone";
    public static final String NOTICE_DELETED = "mapscribble.msg.deleted";
    public static final String NOTICE_TOO_SMALL = "mapscribble.msg.too_small";
    public static final String NOTICE_TOO_SHORT = "mapscribble.msg.too_short";
    public static final String NOTICE_NO_NOTE = "mapscribble.msg.no_note";
    public static final String NOTICE_HIDDEN_PICK = "mapscribble.msg.hidden_pick";

    /** 履歴 1 件。before が null なら追加、after が null なら削除、両方あれば変更。 */
    record Edit(Memo before, Memo after) {
    }

    /** 描いている最中の一筆。 */
    public static final class ActiveStroke {
        private final MemoKind kind;
        private final StrokeSampler sampler = new StrokeSampler();
        private MapPoint cursor;

        ActiveStroke(MemoKind kind) {
            this.kind = kind;
        }

        public MemoKind kind() {
            return kind;
        }

        /** 取り込み済みの点。 */
        public List<MapPoint> points() {
            return sampler.points();
        }

        /** 指の今の位置（近すぎて取り込まなかった点も含む。囲みの「始点へ戻る線」の端）。 */
        public MapPoint cursor() {
            return cursor;
        }
    }

    private final DrawingState state;
    private final Supplier<MemoRepository> repositorySupplier;
    private final UndoHistory<Edit> history = new UndoHistory<>();
    private ActiveStroke stroke;

    public DrawingController(DrawingState state, Supplier<MemoRepository> repositorySupplier) {
        this.state = state;
        this.repositorySupplier = repositorySupplier;
    }

    public DrawingState state() {
        return state;
    }

    /** 使えるメモの窓口。保存先が決まっていなければ null。 */
    public MemoRepository repository() {
        MemoRepository repo = repositorySupplier.get();
        return repo != null && repo.isAvailable() ? repo : null;
    }

    /** 手書きモードがオンで、メモを使える状態か。false の間は地図の操作に何も介入しない。 */
    public boolean isActive() {
        return state.isModeOn() && repository() != null;
    }

    // ---- 追加・変更・削除と履歴 ----

    /** メモを追加して履歴に積む。メモを隠していたら表示へ戻す（描いたのに見えない、を防ぐ）。 */
    public void add(Memo memo) {
        MemoRepository repo = repository();
        if (repo == null) {
            return;
        }
        repo.add(memo);
        history.push(new Edit(null, memo));
        state.setVisible(true);
    }

    /** メモを変更して履歴に積む（変更の前後を持つ）。 */
    public void update(Memo before, Memo after) {
        MemoRepository repo = repository();
        if (repo == null) {
            return;
        }
        Memo stamped = withUpdated(after);
        repo.update(stamped);
        history.push(new Edit(before, stamped));
    }

    /** メモを削除して履歴に積む。 */
    public void remove(Memo memo) {
        MemoRepository repo = repository();
        if (repo == null) {
            return;
        }
        repo.remove(memo.id());
        history.push(new Edit(memo, null));
        state.notice(NOTICE_DELETED);
    }

    public boolean canUndo() {
        return history.canUndo() && repository() != null;
    }

    public boolean canRedo() {
        return history.canRedo() && repository() != null;
    }

    /** 直前の追加・変更・削除を取り消す。取り消したら true。 */
    public boolean undo() {
        MemoRepository repo = repository();
        if (repo == null) {
            return false;
        }
        Edit edit = history.popUndo();
        if (edit == null) {
            return false;
        }
        if (edit.before() == null) {
            repo.remove(edit.after().id());
        } else if (edit.after() == null) {
            repo.add(edit.before());
        } else {
            repo.update(edit.before());
        }
        state.notice(NOTICE_UNDONE);
        return true;
    }

    /** 取り消した操作をやり直す。やり直したら true。 */
    public boolean redo() {
        MemoRepository repo = repository();
        if (repo == null) {
            return false;
        }
        Edit edit = history.popRedo();
        if (edit == null) {
            return false;
        }
        if (edit.before() == null) {
            repo.add(edit.after());
        } else if (edit.after() == null) {
            repo.remove(edit.before().id());
        } else {
            repo.update(edit.after());
        }
        state.notice(NOTICE_REDONE);
        return true;
    }

    /** ディメンション（やワールド）が切り替わった時に、履歴と描いている最中の一筆を捨てる。 */
    public void onDimensionChanged() {
        history.clear();
        stroke = null;
    }

    int historySize() {
        return history.undoSize();
    }

    private static Memo withUpdated(Memo m) {
        return new Memo(m.id(), m.kind(), m.points(), m.color(), m.width(), m.fillOpacity(), m.text(), m.textScale(),
                m.onMinimap(), m.created(), Instant.now());
    }

    // ---- 一筆 ----

    public ActiveStroke activeStroke() {
        return stroke;
    }

    /** 一筆を始める（囲み・線のツールのとき）。 */
    public void beginStroke(double px, double py, Projection projection) {
        Tool tool = state.tool();
        if (!tool.isStrokeTool()) {
            return;
        }
        stroke = new ActiveStroke(tool == Tool.ENCLOSE ? MemoKind.REGION : MemoKind.LINE);
        extendStroke(px, py, projection);
    }

    /** 点を足す。一筆が無ければ始める。 */
    public void extendStroke(double px, double py, Projection projection) {
        if (stroke == null) {
            beginStroke(px, py, projection);
            return;
        }
        stroke.sampler.add(px, py, projection);
        stroke.cursor = new MapPoint(projection.toWorldX(px), projection.toWorldZ(py));
    }

    /** 描いている最中の一筆を捨てる。 */
    public void cancelStroke() {
        stroke = null;
    }

    /**
     * 一筆を確定する。間引き・成立判定をして、成立すれば {@link Draft} を確定後の処理へ渡す。
     * 成立しなければ捨てて、お知らせを出す。
     *
     * @return 下書きを渡したら true
     */
    public boolean finishStroke(Projection projection) {
        ActiveStroke finished = stroke;
        stroke = null;
        if (finished == null) {
            return false;
        }
        double tolerance = projection.pixelsToWorld(Simplify.TOLERANCE_PIXELS);
        List<MapPoint> points = Simplify.rdp(finished.points(), tolerance);
        if (finished.kind() == MemoKind.REGION) {
            double minArea = Shapes.MIN_REGION_AREA_PIXELS / (projection.scale() * projection.scale());
            if (!Shapes.isValidRegion(points, minArea)) {
                state.notice(NOTICE_TOO_SMALL);
                return false;
            }
        } else if (!Shapes.isValidLine(points)) {
            state.notice(NOTICE_TOO_SHORT);
            return false;
        }
        submit(newDraft(finished.kind(), points));
        return true;
    }

    /** 文字ツールでクリックした位置に置く下書きを作って渡す。 */
    public void placeText(double wx, double wz) {
        submit(newDraft(MemoKind.TEXT, List.of(new MapPoint(wx, wz))));
    }

    private Draft newDraft(MemoKind kind, List<MapPoint> points) {
        return new Draft(kind, points, state.color(), state.width(), state.fillOpacity(), state.textScale(),
                state.onMinimap());
    }

    private void submit(Draft draft) {
        if (state.draftHandler() != null) {
            state.draftHandler().accept(draft);
            return;
        }
        // 既定: 文字なしで追加する（文字のメモだけは、見えなくならないよう仮の文字を付ける）
        add(draft.toMemo(draft.kind() == MemoKind.TEXT ? DEFAULT_TEXT_MEMO : ""));
    }

    /** 消しゴムでクリックした地点のメモを消す。 */
    public void erase(Memo memo) {
        if (memo == null) {
            state.notice(NOTICE_NO_NOTE);
            return;
        }
        remove(memo);
    }

    /** 編集ツールでクリックしたメモの編集を、文字入力の画面へ頼む。 */
    public void requestEdit(Memo memo) {
        if (memo == null) {
            state.notice(NOTICE_NO_NOTE);
            return;
        }
        state.requestEdit(memo);
    }
}
