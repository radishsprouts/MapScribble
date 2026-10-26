package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.util.function.Supplier;

/**
 * 手書きモードの共有の窓口。MOD の初期化（{@code MapScribble.initCommon}）と JourneyMap 連携（jm/）が
 * {@link #install} で受け取り、画面（ui/・map/）が {@link #state()} と {@link #controller()} を使う。
 */
public final class ScribbleSession {
    private static DrawingController controller;

    private ScribbleSession() {
    }

    /**
     * メモの窓口の取り方を渡して、共有のコントローラーを受け取る。
     * 呼び出しは MOD の初期化と JourneyMap のプラグインの初期化の 2 か所にあるので、
     * <strong>2 回目以降は最初に作ったものをそのまま返す</strong>。
     * 作り直すと、先に受け取った側（描画・ミニマップ）と別の実体になり、
     * 描きかけの一筆と取り消しの履歴が画面ごとに分かれてしまう。
     */
    public static synchronized DrawingController install(Supplier<MemoRepository> repository) {
        if (controller == null) {
            controller = new DrawingController(DrawingState.shared(), repository);
        }
        return controller;
    }

    public static DrawingState state() {
        return DrawingState.shared();
    }

    /** まだ MOD の初期化が済んでいなければ null。 */
    public static synchronized DrawingController controller() {
        return controller;
    }

    /** 指定したメモを取り消しの履歴に積んで追加する（文字入力の画面が確定した時用の近道）。 */
    public static void add(Memo memo) {
        DrawingController c = controller();
        if (c != null) {
            c.add(memo);
        }
    }
}
