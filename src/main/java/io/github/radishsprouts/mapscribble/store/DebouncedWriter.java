package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.model.Memo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 書き出し予約をまとめて、1本の書き込み用スレッドで書く。
 * 最後の依頼から delayMs 後に書く。連続した依頼は最後のスナップショットだけが1回書かれる。
 */
public final class DebouncedWriter implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("mapscribble");

    /** 実際の書き込み。書き込み用スレッドから呼ばれる。 */
    @FunctionalInterface
    public interface Sink {
        void write(Path file, List<Memo> snapshot) throws IOException;
    }

    private final long delayMs;
    private final Sink sink;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MapScribble-Writer");
        t.setDaemon(true);
        return t;
    });
    private final Map<Path, List<Memo>> pending = new LinkedHashMap<>();
    private ScheduledFuture<?> scheduled;

    public DebouncedWriter(long delayMs, Sink sink) {
        this.delayMs = delayMs;
        this.sink = sink;
    }

    /** 主スレッドから呼ぶ。snapshot は以後変更されない一覧であること。 */
    public synchronized void request(Path file, List<Memo> snapshot) {
        pending.put(file, snapshot);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        try {
            scheduled = executor.schedule(this::drain, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            drain();
        }
    }

    /** 予約分を書き切るまで待つ。切り替わり・切断・終了の時に呼ぶ。 */
    public void flush() {
        synchronized (this) {
            if (scheduled != null) {
                scheduled.cancel(false);
                scheduled = null;
            }
        }
        // 書き込み用スレッドで予約分を書かせ、その完了を待つ（書き込み中のものがあればその後ろになる）
        try {
            executor.submit(this::drain).get(10, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            drain();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.warn("MapScribble: 書き込みの完了待ちに失敗した", e);
        }
    }

    private void drain() {
        List<Map.Entry<Path, List<Memo>>> work;
        synchronized (this) {
            work = new ArrayList<>(pending.entrySet());
            pending.clear();
        }
        for (Map.Entry<Path, List<Memo>> e : work) {
            try {
                sink.write(e.getKey(), e.getValue());
            } catch (IOException | RuntimeException ex) {
                LOGGER.error("MapScribble: メモの書き込みに失敗した: {}", e.getKey(), ex);
            }
        }
    }

    @Override
    public void close() {
        flush();
        executor.shutdown();
    }
}
