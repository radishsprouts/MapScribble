package io.github.radishsprouts.mapscribble.store;

/**
 * 移す操作の結果。
 *
 * @param ok         移せた（移すものが無かった場合も true）
 * @param messageKey 失敗の理由の翻訳キー。成功なら null
 * @param moved      実際に移したメモの数
 */
public record MoveResult(boolean ok, String messageKey, int moved) {
    public static final String KEY_UNAVAILABLE = "mapscribble.move.unavailable";
    public static final String KEY_FAILED = "mapscribble.move.failed";

    public static MoveResult success(int moved) {
        return new MoveResult(true, null, moved);
    }

    public static MoveResult failed(String messageKey) {
        return new MoveResult(false, messageKey, 0);
    }

    /** この窓口は移す操作に対応していない。 */
    public static MoveResult unsupported() {
        return failed(KEY_UNAVAILABLE);
    }
}
