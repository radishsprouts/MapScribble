package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.model.Memo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 今のディメンションのメモを持つ {@link MemoRepository} の実装。
 * 呼び出しは主スレッドから。保存は {@link DebouncedWriter} が主スレッドの外で行う。
 */
public final class MemoStore implements MemoRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("mapscribble");
    /** 最後の変更からこの時間だけ待ってまとめて書く。 */
    public static final long WRITE_DELAY_MS = 1000;

    private final DebouncedWriter writer;
    private final List<Runnable> listeners = new ArrayList<>();
    private List<Memo> memos = new ArrayList<>();
    private List<Memo> view = List.of();
    /** 保存先。null なら使えない。 */
    private Path file;
    /** 読めなかったファイルは上書きしない。 */
    private boolean writable;
    private volatile Consumer<String> problemHandler = message -> { };

    public MemoStore() {
        this(WRITE_DELAY_MS);
    }

    public MemoStore(long writeDelayMs) {
        this(new DebouncedWriter(writeDelayMs, (f, snapshot) -> MemoFile.writeAtomic(f, MemoFile.toJson(snapshot))));
    }

    /** 書き込みの仕組みを差し替える（試験用）。 */
    public MemoStore(DebouncedWriter writer) {
        this.writer = writer;
    }

    /** 読めなかった時の通知先（画面の担当がつなぐ）。主スレッドから呼ばれる。 */
    public void setProblemHandler(Consumer<String> handler) {
        this.problemHandler = handler == null ? message -> { } : handler;
    }

    @Override
    public boolean isAvailable() {
        return file != null && writable;
    }

    @Override
    public List<Memo> memos() {
        return view;
    }

    @Override
    public void add(Memo memo) {
        if (!isAvailable()) {
            return;
        }
        memos.add(memo);
        changed();
    }

    @Override
    public void update(Memo memo) {
        if (!isAvailable()) {
            return;
        }
        for (int i = 0; i < memos.size(); i++) {
            if (memos.get(i).id().equals(memo.id())) {
                memos.set(i, memo);
                changed();
                return;
            }
        }
    }

    @Override
    public void remove(UUID id) {
        if (isAvailable() && memos.removeIf(m -> m.id().equals(id))) {
            changed();
        }
    }

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** このファイルを今のメモの保存先にして読み込む。先に開いていたものは書き切ってから捨てる。 */
    public void open(Path newFile) {
        discardCurrent();
        MemoFile.ReadResult result = MemoFile.read(newFile);
        file = newFile;
        writable = result.canWrite();
        memos = new ArrayList<>(result.memos());
        refreshView();
        if (!result.canWrite()) {
            String message = "メモのファイルを読めなかったため、このディメンションでは手書きメモを使えません（"
                    + newFile.getFileName() + "）: " + describe(result);
            LOGGER.warn("MapScribble: {} / {}", message, newFile);
            problemHandler.accept(message);
        }
        notifyListeners();
    }

    /** 書き切って、手持ちを捨てる。保存先は未定になる。 */
    public void close() {
        discardCurrent();
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
        file = null;
        writable = false;
        memos = new ArrayList<>();
        refreshView();
    }

    private static String describe(MemoFile.ReadResult r) {
        return switch (r.status()) {
            case UNSUPPORTED -> "知らない形式です（" + r.detail() + "）";
            default -> "ファイルが壊れています（" + r.detail() + "）";
        };
    }

    private void changed() {
        refreshView();
        writer.request(file, view);
        notifyListeners();
    }

    private void refreshView() {
        view = Collections.unmodifiableList(List.copyOf(memos));
    }

    private void notifyListeners() {
        for (Runnable l : List.copyOf(listeners)) {
            l.run();
        }
    }
}
