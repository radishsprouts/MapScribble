package io.github.radishsprouts.mapscribble.draw;

/** 手書きモードのツール。 */
public enum Tool {
    /** 地図をドラッグして区画を囲む。 */
    ENCLOSE,
    /** ドラッグした跡を線として残す。 */
    LINE,
    /** クリックした位置に文字を置く。 */
    TEXT,
    /** クリックしたメモを消す。 */
    ERASER,
    /** クリックしたメモの文字・色を直す。 */
    EDIT;

    /** ドラッグで描くツールか。 */
    public boolean isStrokeTool() {
        return this == ENCLOSE || this == LINE;
    }
}
