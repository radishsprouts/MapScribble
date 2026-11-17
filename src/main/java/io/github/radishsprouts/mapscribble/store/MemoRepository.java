package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.model.Memo;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 今のサーバー・ディメンションのメモの窓口。保存と切り替わりの追従は実装側が持つ（04章）。
 * JourneyMap 連携・画面の側はこの型だけを使い、保存の仕組みを知らない。
 * 呼び出しは Minecraft の主スレッドから行う。
 */
public interface MemoRepository {
    /** メモを使える状態か（保存先が決まっているか）。false の間は手書きモードを使えなくする。 */
    boolean isAvailable();

    /** 今のディメンションのメモ。描いた順（後ろほど新しく、上に描く）。変更できない一覧を返す。 */
    List<Memo> memos();

    void add(Memo memo);

    /** 同じ id のメモを置き換える。無ければ何もしない。 */
    void update(Memo memo);

    void remove(UUID id);

    /** メモの一覧が変わった時（追加・変更・削除・切り替わりで読み直した時）に、主スレッドで呼ばれる。 */
    void addListener(Runnable listener);

    /** そのメモが「分ける前のメモ」（直下のファイルのもの）か。メモ帳を開いていない時は常に false。見分けの表示に使う。 */
    default boolean isLegacy(UUID id) {
        return false;
    }

    /** 開き直し・閉じるたびに増える。元に戻すの履歴を捨てる合図。 */
    default long generation() {
        return 0;
    }

    /** 移す操作の最中か。最中は add/update/remove を受け付けない。 */
    default boolean isBusy() {
        return false;
    }

    /** 分ける前のメモを主ファイル（メモ帳）へ、または逆へ移す（設計書 5章）。結果は主スレッドで onDone に渡す。 */
    default void move(Collection<UUID> ids, MoveDirection direction, Consumer<MoveResult> onDone) {
        onDone.accept(MoveResult.unsupported());
    }
}
