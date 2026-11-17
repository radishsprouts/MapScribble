package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.store.NotebookAccess;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 保存先の画面が、{@link NotebookAccess} の状態のほかに必要とする値と操作。
 * {@code NotebookAccess}（設計書 v0.4 の 4.5）にはメモ帳ごとの件数・分ける前のメモの件数・移す操作の履歴が無いので、
 * 画面はこの窓口から受け取る。実装はつなぎ込み（StoreSession・DrawingController）が持つ。
 * 呼び出しは主スレッドから。
 */
public interface NotebookExtras {
    /** 何も繋がっていない時の実装（件数は 0、移す操作は失敗を返す）。画面が落ちないための既定。 */
    NotebookExtras NONE = new NotebookExtras() {
        @Override
        public String address() {
            return "";
        }

        @Override
        public boolean isLoading() {
            return false;
        }

        @Override
        public int noteCount(String notebookId) {
            return 0;
        }

        @Override
        public int legacyTotal() {
            return 0;
        }

        @Override
        public int legacyVisible() {
            return 0;
        }

        @Override
        public Collection<UUID> legacyVisibleIds() {
            return List.of();
        }

        @Override
        public boolean isMoving() {
            return false;
        }

        @Override
        public boolean canUndoMove() {
            return false;
        }

        @Override
        public void move(Collection<UUID> ids, Consumer<NotebookAccess.Result> onDone) {
            onDone.accept(new NotebookAccess.Result(false, "mapscribble.notebook.move_failed"));
        }

        @Override
        public void undoMove(Consumer<NotebookAccess.Result> onDone) {
            onDone.accept(new NotebookAccess.Result(false, "mapscribble.notebook.move_failed"));
        }
    };

    /** 接続先（接続に使ったアドレス）。分からなければ空文字。 */
    String address();

    /** メモ帳の一覧を読み込み中か。 */
    boolean isLoading();

    /** このメモ帳のメモの件数。分からなければ 0。 */
    int noteCount(String notebookId);

    /** 分ける前のメモの件数（この接続先の全ディメンション）。 */
    int legacyTotal();

    /** 分ける前のメモのうち、画面を開いた時に全画面マップに（一部でも）見えていた分の件数。 */
    int legacyVisible();

    /** その分の id。 */
    Collection<UUID> legacyVisibleIds();

    /** 移している最中か（書き込み用スレッドの完了待ち）。 */
    boolean isMoving();

    /** 直前に移した操作を、元に戻せるか。 */
    boolean canUndoMove();

    /** 分ける前のメモを使用中のメモ帳へ移す。ids が null なら全部。元に戻すの履歴に積む。 */
    void move(Collection<UUID> ids, Consumer<NotebookAccess.Result> onDone);

    /** 直前に移した操作を元に戻す。 */
    void undoMove(Consumer<NotebookAccess.Result> onDone);
}
