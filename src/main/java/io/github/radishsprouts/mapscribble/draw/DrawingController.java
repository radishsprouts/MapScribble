package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.geom.Shapes;
import io.github.radishsprouts.mapscribble.geom.Simplify;
import io.github.radishsprouts.mapscribble.geom.StrokeSampler;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import io.github.radishsprouts.mapscribble.store.MoveDirection;
import io.github.radishsprouts.mapscribble.store.MoveResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * メモの追加・変更・削除と取り消し、描いている最中の一筆を扱う。
 * メモの読み書きは {@link MemoRepository} だけを通す。Minecraft の主スレッドからだけ使う。
 */
public final class DrawingController {
    /** 一筆を確定した後、既定の処理が付ける文字のメモの文字（文字入力の画面ができるまでの仮）。 */
    static final String DEFAULT_TEXT_MEMO = "メモ";

    public static final String NOTICE_UNDONE = "mapscribble.msg.undone";
    /** 移す操作を取り消した時のお知らせ（画面の「元に戻す」と同じキー）。 */
    public static final String NOTICE_MOVE_UNDONE = "mapscribble.msg.move_undone";
    public static final String NOTICE_REDONE = "mapscribble.msg.redone";
    public static final String NOTICE_DELETED = "mapscribble.msg.deleted";
    public static final String NOTICE_TOO_SMALL = "mapscribble.msg.too_small";
    public static final String NOTICE_TOO_SHORT = "mapscribble.msg.too_short";
    public static final String NOTICE_NO_NOTE = "mapscribble.msg.no_note";
    public static final String NOTICE_HIDDEN_PICK = "mapscribble.msg.hidden_pick";
    /** 分ける前のメモを移している最中で、編集を受け付けない（文言は W6 で足す）。 */
    public static final String NOTICE_BUSY = "mapscribble.msg.move_busy";

    /** 履歴 1 件。before が null なら追加、after が null なら削除、両方あれば変更。 */
    record Edit(Memo before, Memo after) implements HistoryEntry {
    }

    /** 履歴 1 件の型。メモの編集か、分ける前のメモをメモ帳へ移した操作。 */
    sealed interface HistoryEntry permits Edit, Move {
    }

    /** 分ける前のメモを移した操作。元に戻すと逆向きに移す。 */
    record Move(List<UUID> ids, MoveDirection direction) implements HistoryEntry {
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
    private final UndoHistory<HistoryEntry> history = new UndoHistory<>();
    private ActiveStroke stroke;
    /** 履歴を作った時の保存先の世代。変わっていたら履歴を捨てる（別のサーバー・メモ帳へ書かないため）。 */
    private long historyGeneration;

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
        MemoRepository repo = editableRepository();
        if (repo == null) {
            return;
        }
        repo.add(memo);
        history.push(new Edit(null, memo));
        state.setVisible(true);
    }

    /** メモを変更して履歴に積む（変更の前後を持つ）。 */
    public void update(Memo before, Memo after) {
        MemoRepository repo = editableRepository();
        if (repo == null) {
            return;
        }
        Memo stamped = withUpdated(after);
        repo.update(stamped);
        history.push(new Edit(before, stamped));
    }

    /** メモを削除して履歴に積む。 */
    public void remove(Memo memo) {
        MemoRepository repo = editableRepository();
        if (repo == null) {
            return;
        }
        repo.remove(memo.id());
        history.push(new Edit(memo, null));
        state.notice(NOTICE_DELETED);
    }

    public boolean canUndo() {
        ensureFresh(repository());
        return history.canUndo() && repository() != null;
    }

    public boolean canRedo() {
        ensureFresh(repository());
        return history.canRedo() && repository() != null;
    }

    /**
     * 変更してよい窓口。保存先が決まっていなければ null。移している最中は何もせず null（お知らせを出す）。
     * 保存先が変わっていたら、先に履歴と描いている最中の一筆を捨てる。
     */
    private MemoRepository editableRepository() {
        MemoRepository repo = repository();
        if (repo == null) {
            return null;
        }
        ensureFresh(repo);
        if (repo.isBusy()) {
            state.notice(NOTICE_BUSY);
            return null;
        }
        return repo;
    }

    /** 保存先が開き直されていたら（サーバー・メモ帳が変わった）、履歴と一筆を捨てる。 */
    private void ensureFresh(MemoRepository repo) {
        if (repo == null) {
            return;
        }
        long generation = repo.generation();
        if (generation != historyGeneration) {
            history.clear();
            stroke = null;
            historyGeneration = generation;
        }
    }

    /** 直前の追加・変更・削除・移す操作を取り消す。取り消したら（移す操作は始めたら）true。 */
    public boolean undo() {
        MemoRepository repo = editableRepository();
        if (repo == null) {
            return false;
        }
        HistoryEntry entry = history.popUndo();
        if (entry == null) {
            return false;
        }
        if (entry instanceof Move move) {
            long generation = repo.generation();
            repo.move(move.ids(), move.direction().reverse(), result -> {
                if (!result.ok()) {
                    if (repo.generation() == generation) {
                        history.restoreUndo(entry);
                    }
                    state.notice(result.messageKey());
                }
            });
            state.notice(NOTICE_MOVE_UNDONE);
            return true;
        }
        Edit edit = (Edit) entry;
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
        MemoRepository repo = editableRepository();
        if (repo == null) {
            return false;
        }
        HistoryEntry entry = history.popRedo();
        if (entry == null) {
            return false;
        }
        if (entry instanceof Move move) {
            long generation = repo.generation();
            repo.move(move.ids(), move.direction(), result -> {
                if (!result.ok()) {
                    if (repo.generation() == generation) {
                        history.restoreRedo(entry);
                    }
                    state.notice(result.messageKey());
                }
            });
            state.notice(NOTICE_REDONE);
            return true;
        }
        Edit edit = (Edit) entry;
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

    /** 直前の操作が「分ける前のメモを移した」操作で、元に戻せるか。 */
    public boolean canUndoMove() {
        ensureFresh(repository());
        return repository() != null && history.peekUndo() instanceof Move;
    }

    /**
     * 直前に移した操作だけを元に戻す（直前が移す操作でなければ何もせず失敗を返す）。保存先の画面の「元に戻す」用。
     * 結果は主スレッドで onDone に渡す。
     */
    public void undoMove(Consumer<MoveResult> onDone) {
        MemoRepository repo = editableRepository();
        if (repo == null || !(history.peekUndo() instanceof Move move)) {
            if (onDone != null) {
                onDone.accept(MoveResult.unsupported());
            }
            return;
        }
        history.popUndo();
        long generation = repo.generation();
        repo.move(move.ids(), move.direction().reverse(), result -> {
            if (!result.ok() && repo.generation() == generation) {
                history.restoreUndo(move);
            }
            if (onDone != null) {
                onDone.accept(result);
            }
        });
    }

    /** 全画面マップが最後に描いた時の投影。保存先の画面が「見えている範囲」を数えるのに使う。 */
    private volatile Projection fullscreenProjection;

    /** 全画面マップの描画が毎フレーム呼ぶ（参照を置くだけ）。 */
    public void noteFullscreenProjection(Projection projection) {
        this.fullscreenProjection = projection;
    }

    /** 全画面マップに最後に映っていたワールドの範囲。まだ描いていなければ null。 */
    public io.github.radishsprouts.mapscribble.geom.Rect fullscreenVisibleWorld() {
        Projection p = fullscreenProjection;
        return p == null ? null : p.visibleWorld();
    }

    /** ディメンション（やワールド）が切り替わった時に、履歴と描いている最中の一筆を捨てる。 */
    public void onDimensionChanged() {
        history.clear();
        stroke = null;
    }

    /**
     * 分ける前のメモを今のメモ帳へ移し、成功したら履歴に積む（元に戻すで戻せる）。
     *
     * @param ids    移すメモ。null なら今のディメンションの分ける前のメモ全部
     * @param onDone 結果（主スレッドで呼ばれる）。null 可
     */
    public void moveLegacyToNotebook(Collection<UUID> ids, Consumer<MoveResult> onDone) {
        MemoRepository repo = editableRepository();
        if (repo == null) {
            if (onDone != null) {
                onDone.accept(MoveResult.unsupported());
            }
            return;
        }
        List<UUID> target = new ArrayList<>();
        for (Memo m : repo.memos()) {
            if (repo.isLegacy(m.id()) && (ids == null || ids.contains(m.id()))) {
                target.add(m.id());
            }
        }
        long generation = repo.generation();
        repo.move(target, MoveDirection.LEGACY_TO_PRIMARY, result -> {
            if (result.ok() && result.moved() > 0 && repo.generation() == generation) {
                history.push(new Move(List.copyOf(target), MoveDirection.LEGACY_TO_PRIMARY));
            }
            if (onDone != null) {
                onDone.accept(result);
            }
        });
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
