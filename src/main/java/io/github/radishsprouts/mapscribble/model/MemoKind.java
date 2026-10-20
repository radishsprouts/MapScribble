package io.github.radishsprouts.mapscribble.model;

/** メモの種類。JSON には名前のまま書く。 */
public enum MemoKind {
    /** 手書きで囲んだ区画。閉じた多角形で、塗りと文字を持つ。 */
    REGION,
    /** 手書きの線。閉じない折れ線。 */
    LINE,
    /** 文字だけのメモ。点を1つだけ持つ。 */
    TEXT
}
