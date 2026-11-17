package io.github.radishsprouts.mapscribble.ui;

import net.minecraft.client.resources.language.I18n;

/**
 * メモ帳の名前を画面・お知らせに出す時の表示名（設計書 3.7）。
 * 名前が空のメモ帳（0.4.0 の自動作成が空で保存したもの）は「（名前なし）」と表示する。ファイルの name は書き換えない。
 * メモ帳の名前を表示する所は、すべてこれを通す。
 */
public final class NotebookNames {
    private NotebookNames() {
    }

    /** 名前が空（null か空白だけ）か。 */
    public static boolean isBlank(String name) {
        return name == null || name.isBlank();
    }

    /** 表示する名前。空なら「（名前なし）」/ "(untitled)"。 */
    public static String display(String name) {
        return isBlank(name) ? I18n.get("mapscribble.notebook.untitled") : name;
    }
}
