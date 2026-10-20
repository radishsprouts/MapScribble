package io.github.radishsprouts.mapscribble.draw;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 取り消し・やり直しの履歴。上限を超えたら古いものから捨てる（メモリを増やし続けないため）。
 * 履歴に積むのは「どう変えたか」だけで、適用は呼び出し側が行う。
 */
public final class UndoHistory<T> {
    /** 履歴の上限。 */
    public static final int LIMIT = 100;

    private final int limit;
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();

    public UndoHistory() {
        this(LIMIT);
    }

    public UndoHistory(int limit) {
        this.limit = limit;
    }

    /** 新しい操作を積む。やり直しの履歴は捨てる。 */
    public void push(T edit) {
        redo.clear();
        pushUndo(edit);
    }

    /** 取り消す操作を取り出す（やり直しの履歴へ移す）。無ければ null。 */
    public T popUndo() {
        T edit = undo.pollLast();
        if (edit != null) {
            redo.addLast(edit);
        }
        return edit;
    }

    /** やり直す操作を取り出す（取り消しの履歴へ戻す）。無ければ null。 */
    public T popRedo() {
        T edit = redo.pollLast();
        if (edit != null) {
            pushUndo(edit);
        }
        return edit;
    }

    private void pushUndo(T edit) {
        undo.addLast(edit);
        while (undo.size() > limit) {
            undo.removeFirst();
        }
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int undoSize() {
        return undo.size();
    }

    public int redoSize() {
        return redo.size();
    }

    /** ディメンションの切り替わり等で履歴を捨てる。 */
    public void clear() {
        undo.clear();
        redo.clear();
    }
}
