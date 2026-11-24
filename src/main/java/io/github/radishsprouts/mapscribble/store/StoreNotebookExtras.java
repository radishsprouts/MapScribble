package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.geom.Rect;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.ui.NotebookExtras;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 保存先の画面に渡す件数と、分ける前のメモを移す操作（{@link NotebookExtras} の実体）。主スレッドから使う。
 *
 * <p>件数はファイルを読んで数えるので、画面の描画のたびには数えず、メモ・メモ帳が変わるまで覚えておく。
 * 移している最中（書き込み用スレッドの完了待ち）は読み直さない。
 */
final class StoreNotebookExtras implements NotebookExtras {
    private final MemoStore store;

    /** 件数の覚え。変わった時に true にして、次に聞かれた時に数え直す。 */
    private boolean dirty = true;
    private int legacyTotal;
    /** 分ける前のメモのうち、いま開いているディメンションのファイル以外（ほかのディメンション）の件数。 */
    private int legacyOther;
    private final Map<String, Integer> notebookCounts = new HashMap<>();

    /**
     * 画面を開いた時に見えていた分（開いた後に地図を動かしても変わらない）。
     * 移した後・元に戻した後の「見えている分」は、このうち今も分ける前のものだけ（{@link #legacyVisibleIds()}）。
     */
    private List<UUID> openedVisibleIds = List.of();

    /** 直前の移す操作で実際に移った件数。 */
    private int lastMoved;

    StoreNotebookExtras(MemoStore store) {
        this.store = store;
        store.addListener(this::invalidate);
    }

    void invalidate() {
        dirty = true;
    }

    /** 画面を開く時に呼ぶ。今の全画面マップの範囲で、分ける前のメモのうち見えているものを控える。 */
    void snapshotVisible() {
        DrawingController controller = ScribbleSession.controller();
        Rect view = controller == null ? null : controller.fullscreenVisibleWorld();
        List<UUID> ids = new ArrayList<>();
        if (view != null) {
            for (Memo memo : store.memos()) {
                if (store.isLegacy(memo.id()) && !memo.points().isEmpty()
                        && Rect.of(memo.points()).intersects(view)) {
                    ids.add(memo.id());
                }
            }
        }
        setOpenedVisible(ids);
    }

    /** 開いた時に見えていた分を控える（snapshotVisible と試験から）。 */
    void setOpenedVisible(Collection<UUID> ids) {
        openedVisibleIds = List.copyOf(ids);
        dirty = true;
    }

    @Override
    public String address() {
        String ip = StoreSession.currentAddress();
        return ip == null ? "" : ip;
    }

    @Override
    public boolean isLoading() {
        return StoreSession.isNotebookLoading();
    }

    @Override
    public void addListener(Runnable listener) {
        StoreSession.addNotebookListener(listener);
    }

    @Override
    public void removeListener(Runnable listener) {
        StoreSession.removeNotebookListener(listener);
    }

    @Override
    public int noteCount(String notebookId) {
        refresh();
        return notebookId == null ? 0 : notebookCounts.getOrDefault(notebookId, 0);
    }

    @Override
    public int legacyTotal() {
        refresh();
        return legacyTotal;
    }

    @Override
    public int legacyHere() {
        return countLegacyHere(store);
    }

    /**
     * 今のディメンションの分ける前のメモの件数。移す操作が対象にするメモ（DrawingController#moveLegacyToNotebook）と
     * 同じ条件（開いているメモのうち isLegacy のもの）で数える。ボタンの件数と実際に移る件数を一致させるため。
     */
    static int countLegacyHere(MemoRepository repo) {
        int n = 0;
        for (Memo memo : repo.memos()) {
            if (repo.isLegacy(memo.id())) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int legacyOtherDimensions() {
        refresh();
        return legacyOther;
    }

    @Override
    public int legacyVisible() {
        return legacyVisibleIds().size();
    }

    /** 開いた時に見えていた ID のうち、今も分ける前のもの（移した後は減り、元に戻すと戻る）。 */
    @Override
    public Collection<UUID> legacyVisibleIds() {
        List<UUID> now = new ArrayList<>(openedVisibleIds.size());
        for (UUID id : openedVisibleIds) {
            if (store.isLegacy(id)) {
                now.add(id);
            }
        }
        return now;
    }

    @Override
    public int lastMoved() {
        return lastMoved;
    }

    @Override
    public boolean isMoving() {
        return store.isBusy();
    }

    @Override
    public boolean canUndoMove() {
        DrawingController controller = ScribbleSession.controller();
        return controller != null && controller.canUndoMove();
    }

    @Override
    public void move(Collection<UUID> ids, Consumer<NotebookAccess.Result> onDone) {
        DrawingController controller = ScribbleSession.controller();
        if (controller == null) {
            fail(onDone);
            return;
        }
        controller.moveLegacyToNotebook(ids, r -> {
            dirty = true;
            lastMoved = r.moved();
            onDone.accept(new NotebookAccess.Result(r.ok(), r.messageKey()));
        });
    }

    @Override
    public void undoMove(Consumer<NotebookAccess.Result> onDone) {
        DrawingController controller = ScribbleSession.controller();
        if (controller == null) {
            fail(onDone);
            return;
        }
        controller.undoMove(r -> {
            dirty = true;
            onDone.accept(new NotebookAccess.Result(r.ok(), r.messageKey()));
        });
    }

    private static void fail(Consumer<NotebookAccess.Result> onDone) {
        onDone.accept(new NotebookAccess.Result(false, MoveResult.KEY_UNAVAILABLE));
    }

    // ---- 件数 ----

    private void refresh() {
        if (!dirty || store.isBusy()) {
            return;
        }
        refreshFrom(StoreSession.notebookAddressDir(), StoreSession.currentLegacyFile());
    }

    /** 件数を数え直したい状態か（メモ・メモ帳が変わった時から、次に数えるまで真）。単体試験から確かめる。 */
    boolean isDirty() {
        return dirty;
    }

    /**
     * アドレスのフォルダ {@code dir} と、いま開いている分ける前のファイル {@code openLegacy} から件数を数え直す。
     * {@link #refresh} が使う実体で、保存先の状態（{@link StoreSession}）に頼らず試験できるよう分けてある。
     */
    void refreshFrom(Path dir, Path openLegacy) {
        dirty = false;
        notebookCounts.clear();
        legacyTotal = 0;
        legacyOther = 0;
        String openLegacyName = openLegacy == null ? null : openLegacy.getFileName().toString();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        // 予約中の書き込みをファイルへ出してから数える
        store.flush();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    if (NotebookFile.ID_PATTERN.matcher(name).matches()) {
                        notebookCounts.put(name, countFolder(entry));
                    }
                } else if (name.endsWith(".json") && !name.equals(NotebookFile.FILE_NAME)) {
                    int n = countFile(entry);
                    legacyTotal += n;
                    if (!name.equals(openLegacyName)) {
                        legacyOther += n;
                    }
                }
            }
        } catch (IOException e) {
            // 読めなかった分は 0 件として扱う（画面の表示だけに使う値）
        }
    }

    private static int countFolder(Path folder) {
        int total = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder, "*.json")) {
            for (Path entry : entries) {
                total += countFile(entry);
            }
        } catch (IOException e) {
            // 読めなかった分は数えない
        }
        return total;
    }

    private static int countFile(Path file) {
        MemoFile.ReadResult result = MemoFile.read(file);
        return result.memos() == null ? 0 : result.memos().size();
    }
}
