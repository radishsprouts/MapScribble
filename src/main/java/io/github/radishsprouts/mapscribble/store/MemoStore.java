package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.model.Memo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 今のディメンションのメモを持つ {@link MemoRepository} の実装。
 * 呼び出しは主スレッドから。保存は {@link DebouncedWriter} が主スレッドの外で行う。
 * メモ帳を開いている時は、主ファイル（メモ帳）と分ける前のファイル（直下）の2つを同時に開く（設計書 3章）。
 */
public final class MemoStore implements MemoRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("mapscribble");
    /** 最後の変更からこの時間だけ待ってまとめて書く。 */
    public static final long WRITE_DELAY_MS = 1000;
    /** 消したメモの元のファイルを覚えておく件数の上限（元に戻すの履歴の上限より大きく）。 */
    static final int REMOVED_ORIGIN_LIMIT = 256;
    /** 移す前の元ファイルの控えにつける拡張子。 */
    public static final String BACKUP_SUFFIX = ".before-split.bak";

    /** 1つのファイルとその中身。 */
    private static final class Slot {
        final Path file;
        /** MISSING か OK の時だけ true。読めなかったファイルは上書きしない。 */
        final boolean writable;
        List<Memo> memos;

        Slot(Path file, boolean writable, List<Memo> memos) {
            this.file = file;
            this.writable = writable;
            this.memos = new ArrayList<>(memos);
        }

        boolean contains(UUID id) {
            for (Memo m : memos) {
                if (m.id().equals(id)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final DebouncedWriter writer;
    private final List<Runnable> listeners = new ArrayList<>();
    private List<Memo> view = List.of();
    /** 書き込み先。null なら使えない。 */
    private Slot primary;
    /** 分ける前のファイル。メモ帳を開いた時だけ。読めなかった時は null（触らない）。 */
    private Slot legacy;
    /** 見えているメモの元のファイル（view を作るたびに作り直す）。 */
    private Map<UUID, Slot> origin = new HashMap<>();
    /** 消したメモの元のファイル。削除の取り消しで元の場所へ戻すため。古い順に捨てる。 */
    private final LinkedHashMap<UUID, Slot> removedOrigin = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Slot> eldest) {
            return size() > REMOVED_ORIGIN_LIMIT;
        }
    };
    private long generation;
    /** 移す操作の最中。書き込み用スレッドから結果が戻る経路でも読まれるので volatile。 */
    private volatile boolean busy;
    /** 試験専用の印。true の間は isAvailable() が false になる（setWritableForTest）。 */
    private volatile boolean unavailableForTest;
    private volatile Consumer<String> problemHandler = message -> { };
    private volatile Consumer<Boolean> saveStateHandler = failed -> { };
    /** 書き込みに失敗したままのファイル。空になった時が「次に保存が成功した」時。 */
    private final Set<Path> failedFiles = new HashSet<>();
    /** 移す操作のファイルへの書き込み。試験で失敗させるために差し替えられる。 */
    private DebouncedWriter.Sink moveSink = (f, snapshot) -> MemoFile.writeAtomic(f, MemoFile.toJson(snapshot));
    /** 移す操作の結果を主スレッドへ戻す先。既定は呼んだスレッドでそのまま実行（試験用）。 */
    private volatile Executor mainThread = Runnable::run;

    public MemoStore() {
        this(WRITE_DELAY_MS);
    }

    public MemoStore(long writeDelayMs) {
        this(new DebouncedWriter(writeDelayMs, (f, snapshot) -> MemoFile.writeAtomic(f, MemoFile.toJson(snapshot))));
    }

    /** 書き込みの仕組みを差し替える（試験用）。 */
    public MemoStore(DebouncedWriter writer) {
        this.writer = writer;
        writer.setResultHandler(this::onWriteResult);
    }

    /** 移す操作の書き込み先の仕組みを差し替える（試験用）。 */
    void setMoveSink(DebouncedWriter.Sink sink) {
        this.moveSink = sink;
    }

    /** 移す操作の結果を主スレッドへ戻す仕組み（本番は Minecraft の execute）。 */
    public void setMainThreadExecutor(Executor executor) {
        this.mainThread = executor == null ? Runnable::run : executor;
    }

    /**
     * 保存の失敗・回復の通知先。書き込み用スレッドから呼ばれるので、画面へ渡す側が主スレッドへ戻すこと。
     * 失敗した時に true、失敗が続いていた後で書き込みが成功した時に false。ずっと成功している間は呼ばれない。
     */
    public void setSaveStateHandler(Consumer<Boolean> handler) {
        this.saveStateHandler = handler == null ? failed -> { } : handler;
    }

    private void onWriteResult(Path written, Throwable error) {
        boolean notifyFailed;
        synchronized (failedFiles) {
            if (error != null) {
                failedFiles.add(written);
                notifyFailed = true;
            } else if (failedFiles.remove(written) && failedFiles.isEmpty()) {
                notifyFailed = false;
            } else {
                return;
            }
        }
        saveStateHandler.accept(notifyFailed);
    }

    /** 読めなかった時の通知先（画面の担当がつなぐ）。主スレッドから呼ばれる。 */
    public void setProblemHandler(Consumer<String> handler) {
        this.problemHandler = handler == null ? message -> { } : handler;
    }

    /**
     * 試験専用。書き込み先が書き込める状態かを差し替える（false で「使えない」状態になる）。
     * gametest が帯の説明の行の「使えない」状態を出すために使い、終わったら true へ戻す。呼ばなければ本番の動きは変わらない。
     */
    public void setWritableForTest(boolean writable) {
        this.unavailableForTest = !writable;
    }

    /** 試験専用。今つながっている「読めなかった」通知先を返す（gametest が画面側の表示を動かすために呼ぶ）。 */
    public Consumer<String> problemHandlerForTest() {
        return problemHandler;
    }

    /** 試験専用。今開いている主ファイル（開いていなければ null）。gametest が撮影の後に元の保存先へ戻すために控える。 */
    public java.nio.file.Path primaryFileForTest() {
        return primary == null ? null : primary.file;
    }

    /** 試験専用。今開いている分ける前のファイル（無ければ null）。 */
    public java.nio.file.Path legacyFileForTest() {
        return legacy == null ? null : legacy.file;
    }

    /** 試験専用。今つながっている保存の失敗・回復の通知先を返す（gametest が画面側の表示を動かすために呼ぶ）。 */
    public Consumer<Boolean> saveStateHandlerForTest() {
        return saveStateHandler;
    }

    @Override
    public boolean isAvailable() {
        return primary != null && primary.writable && !unavailableForTest;
    }

    @Override
    public List<Memo> memos() {
        return view;
    }

    @Override
    public boolean isLegacy(UUID id) {
        return legacy != null && origin.get(id) == legacy;
    }

    @Override
    public long generation() {
        return generation;
    }

    @Override
    public boolean isBusy() {
        return busy;
    }

    @Override
    public void add(Memo memo) {
        if (!isAvailable() || busy) {
            return;
        }
        // 削除の取り消しは、消したメモの元のファイルへ戻す（直下のメモがメモ帳へ移らないように）
        Slot target = primary;
        Slot remembered = removedOrigin.remove(memo.id());
        if (remembered != null && remembered.writable && (remembered == primary || remembered == legacy)) {
            target = remembered;
        }
        target.memos.add(memo);
        changed(target);
    }

    @Override
    public void update(Memo memo) {
        if (!isAvailable() || busy) {
            return;
        }
        Slot slot = origin.get(memo.id());
        if (slot == null || !slot.writable) {
            return;
        }
        for (int i = 0; i < slot.memos.size(); i++) {
            if (slot.memos.get(i).id().equals(memo.id())) {
                slot.memos.set(i, memo);
                changed(slot);
                return;
            }
        }
    }

    @Override
    public void remove(UUID id) {
        if (!isAvailable() || busy) {
            return;
        }
        Slot slot = origin.get(id);
        if (slot != null && slot.writable && slot.memos.removeIf(m -> m.id().equals(id))) {
            removedOrigin.put(id, slot);
            changed(slot);
        }
    }

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** このファイルを今のメモの保存先にして読み込む（メモ帳なし）。先に開いていたものは書き切ってから捨てる。 */
    public void open(Path newFile) {
        open(newFile, null);
    }

    /**
     * 主ファイルと、分ける前のファイル（無ければ null）を開く。先に開いていたものは書き切ってから捨てる。
     * 分ける前のファイルが読めなければ表示せず、触らない。
     */
    public void open(Path primaryFile, Path legacyFile) {
        discardCurrent();
        MemoFile.ReadResult result = MemoFile.read(primaryFile);
        primary = new Slot(primaryFile, result.canWrite(), result.memos());
        if (!result.canWrite()) {
            String message = "メモのファイルを読めなかったため、このディメンションでは手書きメモを使えません（"
                    + primaryFile.getFileName() + "）: " + describe(result);
            LOGGER.warn("MapScribble: {} / {}", message, primaryFile);
            problemHandler.accept(message);
        }
        if (legacyFile != null && !legacyFile.equals(primaryFile)) {
            MemoFile.ReadResult legacyResult = MemoFile.read(legacyFile);
            if (legacyResult.canWrite()) {
                legacy = new Slot(legacyFile, true, legacyResult.memos());
            } else {
                String message = "分ける前のメモのファイルを読めなかったため表示できません（"
                        + legacyFile.getFileName() + "）: " + describe(legacyResult) + "。ファイルには触りません";
                LOGGER.warn("MapScribble: {} / {}", message, legacyFile);
                problemHandler.accept(message);
            }
        }
        generation++;
        refreshView();
        notifyListeners();
    }

    /** 書き切って、手持ちを捨てる。保存先は未定になる。 */
    public void close() {
        discardCurrent();
        generation++;
        notifyListeners();
    }

    /** 予約分を書き切る（切断・ゲーム終了の時）。 */
    public void flush() {
        writer.flush();
    }

    /** 書き切ってから、書き込み用スレッドを止める。 */
    public void shutdown() {
        writer.close();
    }

    private void discardCurrent() {
        writer.flush();
        primary = null;
        legacy = null;
        removedOrigin.clear();
        refreshView();
    }

    private static String describe(MemoFile.ReadResult r) {
        return switch (r.status()) {
            case UNSUPPORTED -> "知らない形式です（" + r.detail() + "）";
            default -> "ファイルが壊れています（" + r.detail() + "）";
        };
    }

    private void changed(Slot slot) {
        refreshView();
        writer.request(slot.file, List.copyOf(slot.memos));
        notifyListeners();
    }

    /** 表示の一覧を作る。分ける前のメモを先、主ファイルのメモを後。同じ id が両方にあれば主ファイルだけ。 */
    private void refreshView() {
        Map<UUID, Slot> newOrigin = new HashMap<>();
        List<Memo> list = new ArrayList<>();
        if (primary != null) {
            for (Memo m : primary.memos) {
                newOrigin.put(m.id(), primary);
            }
        }
        if (legacy != null) {
            for (Memo m : legacy.memos) {
                if (!newOrigin.containsKey(m.id())) {
                    newOrigin.put(m.id(), legacy);
                    list.add(m);
                }
            }
        }
        if (primary != null) {
            list.addAll(primary.memos);
        }
        origin = newOrigin;
        view = Collections.unmodifiableList(List.copyOf(list));
    }

    private void notifyListeners() {
        for (Runnable l : List.copyOf(listeners)) {
            l.run();
        }
    }

    // ---- 移す操作（設計書 5章） ----

    @Override
    public void move(Collection<UUID> ids, MoveDirection direction, Consumer<MoveResult> onDone) {
        if (busy || primary == null || legacy == null || !primary.writable || !legacy.writable) {
            onDone.accept(MoveResult.unsupported());
            return;
        }
        final Slot from = direction == MoveDirection.LEGACY_TO_PRIMARY ? legacy : primary;
        final Slot to = direction == MoveDirection.LEGACY_TO_PRIMARY ? primary : legacy;
        List<Memo> moving = new ArrayList<>();
        for (Memo m : from.memos) {
            if (ids.contains(m.id()) && !to.contains(m.id())) {
                moving.add(m);
            }
        }
        if (moving.isEmpty()) {
            onDone.accept(MoveResult.success(0));
            return;
        }
        final List<Memo> fromBefore = List.copyOf(from.memos);
        final List<Memo> toBefore = List.copyOf(to.memos);
        final List<Memo> toAfter = new ArrayList<>(toBefore);
        toAfter.addAll(moving);
        final Set<UUID> movingIds = new HashSet<>();
        for (Memo m : moving) {
            movingIds.add(m.id());
        }
        final List<Memo> fromAfter = new ArrayList<>();
        for (Memo m : fromBefore) {
            if (!movingIds.contains(m.id())) {
                fromAfter.add(m);
            }
        }
        final long token = generation;
        final boolean makeBackup = direction == MoveDirection.LEGACY_TO_PRIMARY;
        final int count = moving.size();
        busy = true;
        notifyListeners();
        writer.submit(() -> {
            doMove(from.file, to.file, fromBefore, fromAfter, toBefore, toAfter, movingIds, makeBackup);
            return Boolean.TRUE;
        }).whenComplete((ok, error) -> mainThread.execute(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                String message = "メモを移せませんでした（" + from.file.getFileName() + "）: " + cause.getMessage();
                LOGGER.warn("MapScribble: {}", message, cause);
                busy = false;
                problemHandler.accept(message);
                notifyListeners();
                onDone.accept(MoveResult.failed(MoveResult.KEY_FAILED));
                return;
            }
            if (generation == token) {
                // メモリの差し替えを終えてから busy を外す（外れた時点で結果が揃っているように）
                to.memos = new ArrayList<>(toAfter);
                from.memos = new ArrayList<>(fromAfter);
                for (UUID id : movingIds) {
                    removedOrigin.remove(id);
                }
                refreshView();
                busy = false;
                notifyListeners();
            } else {
                // 途中で別のファイルへ切り替わった。ファイルは書き終わっていて、次に開いた時に正しく読まれる
                busy = false;
            }
            onDone.accept(MoveResult.success(count));
        }));
    }

    /**
     * 書き込み用スレッドで行う本体。移し先へ書いて読み直せたことを確かめてから元から除く。
     * 失敗したら移し先を元の内容へ戻して例外にする。
     */
    private void doMove(Path fromFile, Path toFile, List<Memo> fromBefore, List<Memo> fromAfter,
            List<Memo> toBefore, List<Memo> toAfter, Set<UUID> movingIds, boolean makeBackup) throws IOException {
        boolean fromTouched = false;
        try {
            moveSink.write(toFile, toAfter);
            verify(toFile, toAfter, movingIds, true);
            if (makeBackup) {
                Path bak = fromFile.resolveSibling(fromFile.getFileName() + BACKUP_SUFFIX);
                if (Files.exists(fromFile) && !Files.exists(bak)) {
                    Files.copy(fromFile, bak, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            fromTouched = true;
            moveSink.write(fromFile, fromAfter);
            verify(fromFile, fromAfter, movingIds, false);
        } catch (IOException | RuntimeException e) {
            rollback(toFile, toBefore);
            // 元のファイルは、置き換えが成功した後の確認で失敗した時だけ戻す（書く前に失敗したなら触っていない）
            if (fromTouched && Files.exists(fromFile) && !sameAs(fromFile, fromBefore)) {
                rollback(fromFile, fromBefore);
            }
            throw e;
        }
    }

    /** 書いた内容と同じバイト列で、読み直せて、移すメモが入っている（または除かれている）ことを確かめる。 */
    private static void verify(Path file, List<Memo> expected, Set<UUID> movingIds, boolean mustContain)
            throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.equals(MemoFile.toJson(expected))) {
            throw new IOException("書いた内容と読み直した内容が違います: " + file.getFileName());
        }
        MemoFile.ReadResult r = MemoFile.parse(text);
        if (r.status() != MemoFile.Status.OK) {
            throw new IOException("書いたファイルを読み直せません: " + file.getFileName());
        }
        Set<UUID> present = new HashSet<>();
        for (Memo m : r.memos()) {
            present.add(m.id());
        }
        for (UUID id : movingIds) {
            if (present.contains(id) != mustContain) {
                throw new IOException("移したメモの有無が想定と違います: " + file.getFileName());
            }
        }
    }

    private static boolean sameAs(Path file, List<Memo> memos) {
        try {
            return MemoFile.read(file).memos().equals(memos);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 失敗した時に、ファイルを移す前の内容へ戻す（戻せなくても元の失敗を優先する）。 */
    private void rollback(Path file, List<Memo> before) {
        try {
            // 一時ファイル経由の書き込みのまま戻す。試験の差し替え先は通さない（失敗させたい対象のため）
            MemoFile.writeAtomic(file, MemoFile.toJson(before));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("MapScribble: 移す操作の失敗後にファイルを戻せなかった: {}", file, e);
        }
    }
}
