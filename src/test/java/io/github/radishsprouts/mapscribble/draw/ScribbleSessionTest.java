package io.github.radishsprouts.mapscribble.draw;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ScribbleSessionTest {
    @Test
    void 二度目のinstallでもコントローラーは作り直さない() {
        Supplier<MemoRepository> first = () -> null;
        Supplier<MemoRepository> second = () -> null;

        DrawingController a = ScribbleSession.install(first);
        DrawingController b = ScribbleSession.install(second);

        assertNotNull(a);
        assertSame(a, b, "作り直すと、描きかけの一筆と取り消しの履歴が画面ごとに分かれる");
        assertSame(a, ScribbleSession.controller());
        assertSame(ScribbleSession.state(), a.state());
    }
}
