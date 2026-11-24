package io.github.radishsprouts.mapscribble.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * ツールチップの折り返し（設計書 3.6・3.7「ツールチップの折り返し」）。
 * バニラは 1 行のツールチップを折らず（ボタンの Tooltip は幅 170 で折る）、長いアドレスは画面の外へ出る。
 * ここでは幅を {@code min(286, GUI 幅 − 24)} とし、空白で折れない語（ドメイン等）は文字単位で折る。
 * 純粋な計算だけで、Minecraft に依存しない。
 */
public final class TooltipWrap {
    /** 画面の中央の広い画面でも超えない上限（保存先の画面の内容の幅と同じ）。 */
    public static final int MAX_WIDTH = 286;
    /** 画面の左右に残す余白の合計。 */
    public static final int SCREEN_MARGIN = 24;

    private TooltipWrap() {
    }

    /** 折り返しの幅。GUI 幅 320（640x480・倍率 2）では 296 でなく 286 になる。 */
    public static int maxWidth(int guiWidth) {
        return Math.max(40, Math.min(MAX_WIDTH, guiWidth - SCREEN_MARGIN));
    }

    /**
     * 文を幅 maxWidth 以内の行に折る。改行（\n）は段落の区切り。短い文は 1 行のまま返す。
     * 空白で折り、1 語で幅を超える時はその語を文字（コードポイント）単位で折る。
     */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, maxWidth, width, out);
        }
        return out;
    }

    private static void wrapParagraph(String paragraph, int maxWidth, ToIntFunction<String> width, List<String> out) {
        if (width.applyAsInt(paragraph) <= maxWidth) {
            out.add(paragraph);
            return;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (width.applyAsInt(candidate) <= maxWidth) {
                cur.setLength(0);
                cur.append(candidate);
                continue;
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (width.applyAsInt(word) <= maxWidth) {
                cur.append(word);
                continue;
            }
            for (int i = 0; i < word.length(); ) {
                int cp = word.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                if (cur.length() > 0 && width.applyAsInt(cur + ch) > maxWidth) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                cur.append(ch);
                i += Character.charCount(cp);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        if (out.isEmpty()) {
            out.add("");
        }
    }
}
