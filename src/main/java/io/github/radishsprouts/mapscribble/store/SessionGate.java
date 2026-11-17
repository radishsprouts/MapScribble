package io.github.radishsprouts.mapscribble.store;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * 毎 tick の呼び出しを安く弾く判定（v0.4 設計 2.2）。Minecraft に依存しない。主スレッドから使う。
 * 前回の ClientLevel・ディメンション・接続先を覚え、参照の比較だけで「何も変わっていない」を返す。
 * ハッシュを読み直すのは {@link Change#LEVEL}（ClientLevel の実体が変わった時）だけでよい。
 */
final class SessionGate {
    enum Change {
        /** 何も変わっていない。毎 tick はここで返る。 */
        NONE,
        /** ClientLevel は同じだが、ディメンションか接続先が変わった（または開いていない）。鍵の作り直しだけ要る。 */
        KEY,
        /** ClientLevel の実体が変わった。ハッシュを読み直し、切り替わりの判定も行う。 */
        LEVEL
    }

    /** リークを避けるため弱参照。 */
    private WeakReference<Object> lastLevel = new WeakReference<>(null);
    private Object lastDimension;
    private Object lastConnection;

    /**
     * @param opened 今メモを開いているか（鍵がある）。開いていなければ、同じ入力でも作り直す
     */
    Change check(Object level, Object dimension, Object connection, boolean opened) {
        boolean levelChanged = level != lastLevel.get();
        if (!levelChanged && Objects.equals(dimension, lastDimension) && connection == lastConnection && opened) {
            return Change.NONE;
        }
        lastDimension = dimension;
        lastConnection = connection;
        if (levelChanged) {
            lastLevel = new WeakReference<>(level);
            return Change.LEVEL;
        }
        return Change.KEY;
    }

    /** 切断。前回の入力を全部忘れる。 */
    void reset() {
        lastLevel = new WeakReference<>(null);
        lastDimension = null;
        lastConnection = null;
    }
}
