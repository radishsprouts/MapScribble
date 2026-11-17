package io.github.radishsprouts.mapscribble.store;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 画面から呼ぶ、メモ帳（保存先）の操作の窓口（設計書 v0.4-server-split 4.5）。
 * 保存先の画面はこの型だけを使い、ファイルを知らない。呼び出しは主スレッドから。結果も主スレッドで返す。
 * 削除の操作は作らない（メモ帳のフォルダ・直下のファイルは消さない）。
 */
public interface NotebookAccess {
    /** 保存先の画面を出せるか。マルチで、アドレスのフォルダが決まっている時だけ true（シングルは false）。 */
    boolean isAvailable();

    /** 今の状態（読むだけ。変更できない値）。 */
    State state();

    record State(
            Mode mode,                 // AUTO / USER / OFF / UNDECIDED（ファイル無し）
            boolean registryReadable,  // notebooks.json を読めたか（false なら変更の操作は全部使えない）
            boolean autoStopped,
            String currentNotebookId,  // 今の書き込み先。null なら直下
            boolean currentIsManual,   // 手で選んだものか
            long currentSeedHash,      // 0 なら取れていない
            List<Info> notebooks       // 作った順
    ) {
    }

    record Info(String id, String name, Instant created, String origin, List<Long> boundSeeds) {
    }

    enum Mode { UNDECIDED, AUTO, USER, OFF }

    /** 「このアドレスはサーバーごとに分ける」を入れる（USER）／切る（OFF）。切ってもメモ帳のフォルダは消さない。 */
    void setSplit(boolean split, Consumer<Result> onDone);

    /** 手でメモ帳を作る。名前は表示用（64文字まで）。作っただけでは切り替えない。 */
    void create(String name, Consumer<Result> onDone);

    /** 名前を変える（フォルダ名は変わらない）。 */
    void rename(String id, String name, Consumer<Result> onDone);

    /**
     * 書き込み先を切り替える。id が null なら直下。
     * remember = true: 今のハッシュをこのメモ帳に紐付け直す（次に入った時もこれになる。資源リセット後に前のメモ帳を使い続ける時）
     * remember = false: 今の接続・今のハッシュの間だけ（同じシードの2つのサーバーを分けたい時）
     */
    void select(String id, boolean remember, Consumer<Result> onDone);

    /** 分ける前のメモを、今のメモ帳へ移す（5章）。ids が null なら今のディメンションの全部。元に戻すで戻せる。 */
    void moveLegacyToCurrent(Collection<UUID> ids, Consumer<Result> onDone);

    /** 状態が変わった時（切り替わり・記録の更新）に主スレッドで呼ばれる。 */
    void addListener(Runnable listener);

    /** 操作の結果。ok でなければ message に理由（翻訳キー）。 */
    record Result(boolean ok, String messageKey) {
    }
}
