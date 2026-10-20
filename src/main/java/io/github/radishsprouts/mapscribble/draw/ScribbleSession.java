package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.util.function.Supplier;

/**
 * 手書きモードの共有の窓口。JourneyMap 連携（jm/）が {@link #install} で作り、画面（ui/）が
 * {@link #state()} と {@link #controller()} を使う。
 */
public final class ScribbleSession {
    private static DrawingController controller;

    private ScribbleSession() {
    }

    /** メモの窓口の取り方を渡して、共有のコントローラーを作る（JourneyMap のプラグインの初期化で 1 回）。 */
    public static synchronized DrawingController install(Supplier<MemoRepository> repository) {
        controller = new DrawingController(DrawingState.shared(), repository);
        return controller;
    }

    public static DrawingState state() {
        return DrawingState.shared();
    }

    /** まだ JourneyMap のプラグインが初期化されていなければ null。 */
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
