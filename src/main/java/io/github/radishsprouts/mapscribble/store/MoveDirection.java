package io.github.radishsprouts.mapscribble.store;

/** 「移す」操作の向き（設計書 5章）。 */
public enum MoveDirection {
    /** 分ける前のファイル（直下）から、今のメモ帳（主ファイル）へ。 */
    LEGACY_TO_PRIMARY,
    /** メモ帳（主ファイル）から、分ける前のファイル（直下）へ戻す。元に戻すで使う。 */
    PRIMARY_TO_LEGACY;

    public MoveDirection reverse() {
        return this == LEGACY_TO_PRIMARY ? PRIMARY_TO_LEGACY : LEGACY_TO_PRIMARY;
    }
}
