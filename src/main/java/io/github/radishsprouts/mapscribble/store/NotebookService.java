package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.compat.WorldIdentityCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebook;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import io.github.radishsprouts.mapscribble.store.NotebookRegistry.ManualSelection;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 保存先の画面から呼ぶ API の実装（v0.4 設計 4.5）。取り出し口は {@link StoreSession#notebooks()}。
 * 呼び出しは主スレッドから行い、結果も主スレッドで返す。
 *
 * <p>削除の API は作らない（メモ帳のフォルダ・直下のファイル・.bak を消す操作は、この版では提供しない）。
 * 触るのは利用者自身の mapscribble フォルダの下だけで、メモ帳の id は {@link NotebookFile#ID_PATTERN} で
 * 検査してから使う（サーバーが外へ書かせることはできない）。
 *
 * <p>失敗の理由（messageKey）は、保存先の画面が名前の入力欄の下に出せる
 * {@code mapscribble.notebook.error.*} だけを返す（画面はそれ以外を save_failed として扱う）。
 */
public final class NotebookService implements NotebookAccess {
    /** 名前が空。 */
    public static final String KEY_EMPTY = "mapscribble.notebook.error.empty";
    /** 同じ名前のメモ帳がある。 */
    public static final String KEY_DUPLICATE = "mapscribble.notebook.error.duplicate";
    /** それ以外の失敗（使えない・読めない・他の書き込み中・書けなかった・見つからない・ハッシュが取れない）。 */
    public static final String KEY_FAILED = "mapscribble.notebook.error.save_failed";

    NotebookService() {
    }

    private static Result ok() {
        return new Result(true, null);
    }

    private static Result fail(String key) {
        return new Result(false, key);
    }

    @Override
    public boolean isAvailable() {
        return StoreSession.notebookAddressDir() != null;
    }

    /** 出せない時は、読めない・メモ帳なしの状態を返す。 */
    @Override
    public State state() {
        Path dir = StoreSession.notebookAddressDir();
        long hash = StoreSession.currentSeedHash();
        String current = StoreSession.currentNotebookId();
        if (dir == null) {
            return new State(Mode.UNDECIDED, false, false, current, false, hash, List.of());
        }
        NotebookRegistry registry = StoreSession.registry();
        boolean readable = registry.status(dir).canWrite();
        Notebooks data = registry.notebooks(dir);
        List<Info> infos = new ArrayList<>();
        for (Notebook nb : data.notebooks()) {
            List<Long> seeds = new ArrayList<>();
            for (Map.Entry<Long, String> e : data.bindings().entrySet()) {
                if (e.getValue().equals(nb.id())) {
                    seeds.add(e.getKey());
                }
            }
            infos.add(new Info(nb.id(), nb.name(), nb.created(), nb.origin(), List.copyOf(seeds)));
        }
        ManualSelection manual = StoreSession.manualSelection();
        boolean isManual = manual != null && manual.seedHash() == hash && Objects.equals(manual.notebookId(), current);
        return new State(modeOf(data), readable, data.autoStopped(), current, isManual, hash, List.copyOf(infos));
    }

    private static Mode modeOf(Notebooks data) {
        if (data.mode() == null) {
            return Mode.UNDECIDED;
        }
        return switch (data.mode()) {
            case AUTO -> Mode.AUTO;
            case USER -> Mode.USER;
            case OFF -> Mode.OFF;
        };
    }

    @Override
    public void setSplit(boolean split, Consumer<Result> onDone) {
        Path dir = writableDir(onDone);
        if (dir == null) {
            return;
        }
        Notebooks cur = StoreSession.registry().notebooks(dir);
        Notebooks next;
        if (split) {
            // 入れ直したら、入るたびにハッシュが変わるとして止めていた自動の作成も解く
            next = cur.withMode(NotebookFile.Mode.USER).withAutoStopped(false);
            if (next.splitAt() == null) {
                next = next.withSplitAt(Instant.now());
            }
        } else {
            next = cur.withMode(NotebookFile.Mode.OFF);
        }
        write(dir, next, null, onDone);
    }

    @Override
    public void create(String name, Consumer<Result> onDone) {
        Path dir = writableDir(onDone);
        if (dir == null) {
            return;
        }
        String problem = nameProblem(dir, null, name);
        if (problem != null) {
            done(onDone, fail(problem));
            return;
        }
        Notebooks proposal = StoreSession.registry().proposeManual(dir, name, Instant.now());
        if (proposal == null) {
            done(onDone, fail(KEY_FAILED));
            return;
        }
        StoreSession.persist(dir, proposal, false, null, okay -> done(onDone, okay ? ok() : fail(KEY_FAILED)));
    }

    @Override
    public void rename(String id, String name, Consumer<Result> onDone) {
        Path dir = writableDir(onDone);
        if (dir == null) {
            return;
        }
        Notebooks cur = StoreSession.registry().notebooks(dir);
        Notebook nb = id == null ? null : cur.find(id);
        if (nb == null) {
            done(onDone, fail(KEY_FAILED));
            return;
        }
        String problem = nameProblem(dir, id, name);
        if (problem != null) {
            done(onDone, fail(problem));
            return;
        }
        write(dir, cur.withNotebook(nb.withName(name)), null, onDone);
    }

    /** 名前の検査。問題が無ければ null。selfId はその名前を持ってよい（自分自身）メモ帳。 */
    private static String nameProblem(Path dir, String selfId, String name) {
        String clean = NotebookFile.cleanName(name);
        if (clean.isBlank()) {
            return KEY_EMPTY;
        }
        for (Notebook nb : StoreSession.registry().notebooks(dir).notebooks()) {
            if (!nb.id().equals(selfId) && nb.name().equals(clean)) {
                return KEY_DUPLICATE;
            }
        }
        return null;
    }

    /**
     * remember = true の時、分けていないアドレスでは紐付けが効くように mode を USER にする。
     * id が null の時は紐付けるものが無いので remember = false と同じ。
     */
    @Override
    public void select(String id, boolean remember, Consumer<Result> onDone) {
        Path dir = writableDir(onDone);
        if (dir == null) {
            return;
        }
        Notebooks cur = StoreSession.registry().notebooks(dir);
        if (id != null && (!NotebookFile.ID_PATTERN.matcher(id).matches() || cur.find(id) == null)) {
            done(onDone, fail(KEY_FAILED));
            return;
        }
        long hash = StoreSession.currentSeedHash();
        if (!remember || id == null) {
            StoreSession.setManualSelection(new ManualSelection(hash, id));
            StoreSession.rekey();
            StoreSession.notifyNotebookListeners();
            done(onDone, ok());
            return;
        }
        if (hash == WorldIdentityCompat.UNKNOWN) {
            done(onDone, fail(KEY_FAILED));
            return;
        }
        Notebooks next = cur.withBinding(hash, id);
        if (next.mode() == null || next.mode() == NotebookFile.Mode.OFF) {
            next = next.withMode(NotebookFile.Mode.USER);
        }
        if (next.splitAt() == null) {
            next = next.withSplitAt(Instant.now());
        }
        // 紐付けが効くので、手で選んだ印は外す（次の鍵は紐付けから決まる）
        write(dir, next, written -> StoreSession.setManualSelection(null), onDone);
    }

    @Override
    public void moveLegacyToCurrent(Collection<UUID> ids, Consumer<Result> onDone) {
        DrawingController controller = ScribbleSession.controller();
        if (controller == null) {
            done(onDone, fail(KEY_FAILED));
            return;
        }
        controller.moveLegacyToNotebook(ids, r -> done(onDone, new Result(r.ok(), r.messageKey())));
    }

    @Override
    public void addListener(Runnable listener) {
        StoreSession.addNotebookListener(listener);
    }

    // ---- 内部 ----

    /** 書いてよいアドレスのフォルダ。出せない・読めない・他の書き込み中なら、onDone へ失敗を返して null。 */
    private static Path writableDir(Consumer<Result> onDone) {
        Path dir = StoreSession.notebookAddressDir();
        if (dir == null || !StoreSession.registry().status(dir).canWrite() || StoreSession.isNotebookBusy(dir)) {
            done(onDone, fail(KEY_FAILED));
            return null;
        }
        return dir;
    }

    private static void write(Path dir, Notebooks next, Consumer<Notebooks> onOk, Consumer<Result> onDone) {
        StoreSession.persist(dir, next, false, onOk, okay -> done(onDone, okay ? ok() : fail(KEY_FAILED)));
    }

    private static void done(Consumer<Result> onDone, Result result) {
        if (onDone != null) {
            onDone.accept(result);
        }
    }
}
