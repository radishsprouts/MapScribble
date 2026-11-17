package io.github.radishsprouts.mapscribble.store;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * 1つの接続の中で、同じディメンションIDのハッシュが変わったかを見る（v0.4 設計 2.3）。
 * 主スレッドから使う。Minecraft に依存しない。記録するだけで、メモの読み書きはしない。
 */
public final class SplitDetector {
    /** 判定の結果。split が true の時、previous は同じディメンションIDでその接続の中で最初に見たハッシュ。 */
    public record Result(boolean split, long previous, long current) {
    }

    private WeakReference<Object> connection = new WeakReference<>(null);
    /** その接続の中で、ディメンションIDごとに最初に見た（0 でない）ハッシュ。 */
    private final Map<String, Long> first = new HashMap<>();

    /**
     * ClientLevel が変わった時に1回呼ぶ。
     * 同じ接続・同じディメンションIDで、最初に見たハッシュと違う値を見る間は、毎回 split = true を返す
     * （呼び出し側が記録済みかを見る）。
     *
     * @param connection 接続の実体。null なら何もせず split=false
     * @param dimensionId 例 "minecraft:overworld"
     * @param seedHash 0（取れない）なら何も記録せず split=false
     */
    public Result observe(Object connection, String dimensionId, long seedHash) {
        if (connection == null || dimensionId == null || seedHash == 0L) {
            return new Result(false, 0L, seedHash);
        }
        if (this.connection.get() != connection) {
            this.connection = new WeakReference<>(connection);
            first.clear();
        }
        Long previous = first.get(dimensionId);
        if (previous == null) {
            first.put(dimensionId, seedHash);
            return new Result(false, 0L, seedHash);
        }
        if (previous != seedHash) {
            return new Result(true, previous, seedHash);
        }
        return new Result(false, previous, seedHash);
    }
}
