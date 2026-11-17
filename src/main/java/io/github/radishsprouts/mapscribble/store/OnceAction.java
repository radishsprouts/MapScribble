package io.github.radishsprouts.mapscribble.store;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 何度呼ばれても、中身を1回だけ実行する（v0.4 設計 6.2）。停止の書き切りが、ローダーのイベントと
 * JVM の終了フックの両方から呼ばれても、1回だけ働くようにするために使う。Minecraft に依存しない。
 */
final class OnceAction {
    private final AtomicBoolean done = new AtomicBoolean();
    private final Runnable action;

    OnceAction(Runnable action) {
        this.action = action;
    }

    /** 初めての呼び出しなら中身を実行して true。2回目以降（別スレッドからの同時呼び出しを含む）は何もせず false。 */
    boolean run() {
        if (!done.compareAndSet(false, true)) {
            return false;
        }
        action.run();
        return true;
    }
}
