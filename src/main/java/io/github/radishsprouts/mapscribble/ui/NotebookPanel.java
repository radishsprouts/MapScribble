package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.store.NotebookAccess;
import io.github.radishsprouts.mapscribble.store.NotebookFile;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * メモの保存先の画面の「今どの状態か」と、状態ごとに何を押せるかを決める（設計書 3.7「状態」の表）。
 * 純粋な計算だけで、Minecraft に依存しない。
 */
public final class NotebookPanel {
    /** 名前の上限（入力欄が受け付ける文字数）。 */
    public static final int NAME_MAX = 24;
    /** ボタンの中の件数の上限表示。 */
    public static final int COUNT_CAP = 999;

    /** 画面の状態。 */
    public enum Kind {
        /** 読み込み中。完了だけ押せる。 */
        LOADING,
        /** 知らない形式の notebooks.json。完了だけ押せる。 */
        LOAD_FAILED,
        /** 分けていない（普通のサーバー）。切替だけ押せる。 */
        UNSPLIT,
        /** 分けている（自動でオンにした）。 */
        SPLIT_AUTO,
        /** 分けている（利用者がオンにした）。 */
        SPLIT_USER,
        /** 分けている接続先で、ワールドの識別値が来ない。 */
        NO_WORLD_ID
    }

    private NotebookPanel() {
    }

    public static Kind kindOf(NotebookAccess.State state, boolean loading) {
        if (loading) {
            return Kind.LOADING;
        }
        if (state == null || !state.registryReadable()) {
            return Kind.LOAD_FAILED;
        }
        NotebookAccess.Mode mode = state.mode();
        if (mode == NotebookAccess.Mode.UNDECIDED || mode == NotebookAccess.Mode.OFF) {
            return Kind.UNSPLIT;
        }
        if (state.currentNotebookId() == null && state.currentSeedHash() == 0L) {
            return Kind.NO_WORLD_ID;
        }
        return mode == NotebookAccess.Mode.AUTO ? Kind.SPLIT_AUTO : Kind.SPLIT_USER;
    }

    /** 分けている状態（メモ帳の一覧を出す状態）か。 */
    public static boolean isSplit(Kind kind) {
        return kind == Kind.SPLIT_AUTO || kind == Kind.SPLIT_USER || kind == Kind.NO_WORLD_ID;
    }

    /** 「この接続先をサーバーごとに分ける」の切替を押せるか。 */
    public static boolean switchEnabled(Kind kind) {
        return kind == Kind.UNSPLIT || kind == Kind.SPLIT_AUTO || kind == Kind.SPLIT_USER;
    }

    /** 切替の表示（オンか）。 */
    public static boolean switchOn(Kind kind) {
        return isSplit(kind);
    }

    /** 一覧の行を押して切り替えられるか。 */
    public static boolean listEnabled(Kind kind) {
        return isSplit(kind);
    }

    /** 名前を変えるを押せるか（使用中のメモ帳が無い間は押せない）。 */
    public static boolean renameEnabled(Kind kind) {
        return kind == Kind.SPLIT_AUTO || kind == Kind.SPLIT_USER;
    }

    /** 新しいメモ帳を押せるか。 */
    public static boolean newEnabled(Kind kind) {
        return isSplit(kind);
    }

    /** 移す操作の前提（分けていて、使用中のメモ帳がある）を満たすか。件数は別に見る。 */
    public static boolean moveAvailable(Kind kind) {
        return kind == Kind.SPLIT_AUTO || kind == Kind.SPLIT_USER;
    }

    /** 切替の下の説明の翻訳キー（空なら null）。 */
    public static String descKey(Kind kind) {
        return switch (kind) {
            case LOADING -> null;
            case LOAD_FAILED -> "mapscribble.notebook.load_failed";
            case UNSPLIT -> "mapscribble.notebook.split.desc.off";
            case SPLIT_AUTO -> "mapscribble.notebook.split.desc.auto";
            case SPLIT_USER -> "mapscribble.notebook.split.desc.on";
            case NO_WORLD_ID -> "mapscribble.notebook.split.desc.no_id";
        };
    }

    /** 切替の説明の文字色（RGB）。灰・黄（注意）・赤系（失敗）。 */
    public static int descColor(Kind kind) {
        return switch (kind) {
            case NO_WORLD_ID -> HintResolver.COLOR_WARNING;
            case LOAD_FAILED -> HintResolver.COLOR_FAILURE;
            default -> 0xA0A0A0;
        };
    }

    /** 分ける前のメモの行の翻訳キー（空なら null）。 */
    public static String legacyKey(Kind kind, int total) {
        return legacyKey(kind, total, 0);
    }

    /**
     * 分ける前のメモの行の翻訳キー。{@code total} は全ディメンションの合計、{@code otherDimensions} はほかのディメンションの件数。
     * 分けている接続先（自動・利用者）で、ほかのディメンションにもある時は「ここに○件・ほかのディメンションに○件」の行にする。
     * 合計が 0 なら「ありません」のまま。
     */
    public static String legacyKey(Kind kind, int total, int otherDimensions) {
        if (kind == Kind.LOADING) {
            return null;
        }
        if (total <= 0) {
            return "mapscribble.notebook.presplit.none";
        }
        if ((kind == Kind.SPLIT_AUTO || kind == Kind.SPLIT_USER) && otherDimensions > 0) {
            return "mapscribble.notebook.presplit.dims";
        }
        return switch (kind) {
            case UNSPLIT -> "mapscribble.notebook.presplit.unsplit";
            case NO_WORLD_ID -> "mapscribble.notebook.presplit.writing";
            default -> "mapscribble.notebook.presplit";
        };
    }

    /** 分けていない接続先の行は灰色、ほかは `#E0E0E0`。 */
    public static boolean legacyGray(Kind kind) {
        return kind == Kind.UNSPLIT;
    }

    /** ボタンの中の件数。1000 以上は「999+」。 */
    public static String countLabel(int n) {
        return n > COUNT_CAP ? COUNT_CAP + "+" : Integer.toString(n);
    }

    /** 幅 {@code maxWidth} に収まるよう、末尾を「…」で切る。収まるならそのまま返す。 */
    public static String ellipsize(String s, int maxWidth, ToIntFunction<String> width) {
        if (s == null || width.applyAsInt(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int end = s.length();
        while (end > 0 && width.applyAsInt(s.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return s.substring(0, end) + ellipsis;
    }

    /** アドレスの中央部を「…」で切る（長いアドレスの表示用）。 */
    public static String ellipsizeMiddle(String s, int maxWidth, ToIntFunction<String> width) {
        if (s == null || width.applyAsInt(s) <= maxWidth) {
            return s;
        }
        int keepHead = s.length() / 2;
        int keepTail = s.length() - keepHead;
        while (keepHead + keepTail > 0) {
            String candidate = s.substring(0, keepHead) + "…" + s.substring(s.length() - keepTail);
            if (width.applyAsInt(candidate) <= maxWidth) {
                return candidate;
            }
            if (keepHead >= keepTail) {
                keepHead--;
            } else {
                keepTail--;
            }
        }
        return "…";
    }

    /** 名前の検証。問題が無ければ null、あれば翻訳キー。前後の空白は除いてから見る。 */
    public static String validateName(String raw, List<NotebookAccess.Info> notebooks, String ownId) {
        String name = normalizeName(raw);
        if (name.isEmpty()) {
            return "mapscribble.notebook.error.empty";
        }
        for (NotebookAccess.Info info : notebooks) {
            if (!info.id().equals(ownId) && NotebookFile.sameName(info.name(), name)) {
                return "mapscribble.notebook.error.duplicate";
            }
        }
        return null;
    }

    /**
     * 名前が重なっている既存のメモ帳の名前（無ければ null。重なる相手は名前が空ではない）。大文字・小文字、全角・半角の違いは同じ名前とみなす。
     * ownId のメモ帳（名前を変える本人）は除く。エラーに既存の名前を出すために使う。
     */
    public static String conflictingName(String raw, List<NotebookAccess.Info> notebooks, String ownId) {
        String name = normalizeName(raw);
        for (NotebookAccess.Info info : notebooks) {
            if (!info.id().equals(ownId) && NotebookFile.sameName(info.name(), name)) {
                return info.name();
            }
        }
        return null;
    }

    /** 前後の空白を除き、上限の長さに収める。 */
    public static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.length() > NAME_MAX) {
            name = name.substring(0, NAME_MAX);
        }
        return name;
    }

    /** 「メモ帳 N」のうち、使われていない最小の N の名前。 */
    public static String defaultName(List<NotebookAccess.Info> notebooks, IntFunction<String> nameOf) {
        for (int n = 1; ; n++) {
            String candidate = nameOf.apply(n);
            boolean used = false;
            for (NotebookAccess.Info info : notebooks) {
                if (NotebookFile.sameName(info.name(), candidate)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 作った日（`yyyy-MM-dd`。言語によらない）。 */
    public static String dateLabel(Instant created, ZoneId zone) {
        return created == null ? "" : DATE.format(created.atZone(zone));
    }

    /** 使用中の行の番号（一覧の中）。無ければ -1。 */
    public static int inUseIndex(NotebookAccess.State state) {
        if (state == null || state.currentNotebookId() == null) {
            return -1;
        }
        for (int i = 0; i < state.notebooks().size(); i++) {
            if (state.notebooks().get(i).id().equals(state.currentNotebookId())) {
                return i;
            }
        }
        return -1;
    }
}
