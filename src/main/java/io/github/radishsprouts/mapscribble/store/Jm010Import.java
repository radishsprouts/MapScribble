package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebook;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.1.0 のメモの引き継ぎ（v0.4 設計 7.1）。Minecraft に依存しない。
 *
 * <p>0.1.0 は JourneyMap の {@code IClientAPI.getDataPath} に保存していた。置き場所は
 * {@code <gameDir>/journeymap/data/{mp,sp}/<フォルダ>/addon-data/mapscribble/}。
 * JourneyMap のフォルダ名の書式には頼らず、{@code data/mp/} と {@code data/sp/} の1段だけを列挙し、
 * 名前の先頭一致で候補を絞る（再帰しない）。
 *
 * <p>元のファイルは消さない・動かさない・名前を変えない・書き換えない。読めないファイルには触らない。
 * 取り込み先に同じ名前のファイルがあれば上書きしない。取り込んだ印 {@link #MARKER} を置いたフォルダでは、
 * 次からは列挙もしない（ファイルシステムへの問い合わせを避ける）。
 */
public final class Jm010Import {
    private static final Logger LOGGER = LoggerFactory.getLogger("mapscribble");

    /** 取り込み済みの印（空ファイル）。取り込み先のフォルダに置く。 */
    public static final String MARKER = ".jm010-imported";

    private Jm010Import() {
    }

    /** 0.1.0 の置き場所の候補。folderName は JourneyMap のフォルダ名、dir は addon-data/mapscribble。 */
    public record Candidate(String folderName, Path dir) {
    }

    /** どの候補を、どこへ取り込むか。candidates が空なら何もしない。 */
    public record Plan(List<Candidate> candidates) {
        /** 候補が2つ以上: 直下へは入れず、フォルダごとにメモ帳として取り込む（9.3 の 1）。 */
        public boolean asNotebooks() {
            return candidates.size() >= 2;
        }

        public boolean isEmpty() {
            return candidates.isEmpty();
        }
    }

    /**
     * JourneyMap の {@code getServerName()} と同じ規則: 名前の {@code \W+} を {@code ~} に置き換え、
     * 空なら ip。ip も空なら空文字（候補は無し）。
     */
    public static String journeyMapName(String serverName, String ip) {
        String name = serverName == null ? "" : serverName.replaceAll("\\W+", "~");
        if (name.isEmpty()) {
            name = ip == null ? "" : ip;
        }
        return name;
    }

    /**
     * マルチの計画。addressDir（{@code mapscribble/servers/<ip>/}）に印があれば列挙せず空を返す。
     * フォルダ名が {@link #journeyMapName} で始まる {@code data/mp/*} のうち、{@code addon-data/mapscribble/} を持つものが候補。
     */
    public static Plan planMultiplayer(Path gameDir, Path addressDir, String serverName, String ip) {
        if (Files.exists(addressDir.resolve(MARKER))) {
            return new Plan(List.of());
        }
        String prefix = journeyMapName(serverName, ip);
        if (prefix.isEmpty()) {
            return new Plan(List.of());
        }
        return new Plan(list(gameDir.resolve("journeymap").resolve("data").resolve("mp"), prefix, false));
    }

    /**
     * シングルの計画。saveDir（{@code mapscribble/saves/<フォルダ>/}）に印があれば列挙せず空を返す。
     * {@code data/sp/} の下で、セーブのフォルダ名（または {@code \W+} → {@code ~} にしたもの）と同じ名前だけが候補。
     */
    public static Plan planSingleplayer(Path gameDir, Path saveDir, String saveFolderName) {
        if (Files.exists(saveDir.resolve(MARKER)) || saveFolderName == null || saveFolderName.isEmpty()) {
            return new Plan(List.of());
        }
        List<Candidate> found = new ArrayList<>();
        String sanitized = saveFolderName.replaceAll("\\W+", "~");
        for (Candidate c : list(gameDir.resolve("journeymap").resolve("data").resolve("sp"), saveFolderName, true)) {
            found.add(c);
        }
        if (!sanitized.equals(saveFolderName)) {
            found.addAll(list(gameDir.resolve("journeymap").resolve("data").resolve("sp"), sanitized, true));
        }
        return new Plan(found);
    }

    private static List<Candidate> list(Path root, String name, boolean exact) {
        List<Candidate> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path p : stream) {
                String folder = p.getFileName().toString();
                boolean match = exact ? folder.equals(name) : folder.startsWith(name);
                Path memoDir = p.resolve("addon-data").resolve("mapscribble");
                if (match && Files.isDirectory(p) && Files.isDirectory(memoDir)) {
                    out.add(new Candidate(folder, memoDir));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("MapScribble: 0.1.0 のメモの置き場所を調べられない: {}", e.toString());
        }
        out.sort((a, b) -> a.folderName().compareTo(b.folderName()));
        return out;
    }

    /**
     * 候補のフォルダの {@code *.json}（1段だけ）を targetDir へコピーする。targetDir に同じ名前があれば触らない。
     * コピーは一時ファイル経由（途中で止まっても中途半端な名前のファイルが残らない）。読めないファイルは飛ばす。
     * 全部うまくいった時だけ印を置いて true。1つでも失敗したら印を置かず false（次の機会にやり直す）。
     */
    public static boolean copyInto(Candidate candidate, Path targetDir) {
        boolean ok = true;
        try {
            List<Path> sources = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(candidate.dir(), "*.json")) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p) && Files.isReadable(p)) {
                        sources.add(p);
                    }
                }
            }
            for (Path src : sources) {
                Path dest = targetDir.resolve(src.getFileName().toString());
                if (Files.exists(dest)) {
                    continue;
                }
                try {
                    Files.createDirectories(targetDir);
                    copyAtomic(src, dest);
                    LOGGER.info("Imported MapScribble 0.1.0 note from {} to {}", src, dest);
                } catch (IOException | RuntimeException e) {
                    ok = false;
                    LOGGER.warn("MapScribble: 0.1.0 のメモ {} を取り込めない: {}", src, e.toString());
                }
            }
            if (ok) {
                writeMarker(targetDir);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("MapScribble: 0.1.0 のメモのフォルダ {} を読めない: {}", candidate.dir(), e.toString());
            return false;
        }
        return ok;
    }

    private static void copyAtomic(Path src, Path dest) throws IOException {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".jm010-tmp");
        try {
            Files.copy(src, tmp, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, dest);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void writeMarker(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path marker = dir.resolve(MARKER);
        if (!Files.exists(marker)) {
            Files.createFile(marker);
        }
    }

    /**
     * 候補が1つの時: アドレスのフォルダ（またはセーブのフォルダ）の直下へ取り込む。
     * 書き込み用スレッドなど、主スレッドの外で呼ぶ。成功したら true。
     */
    public static boolean importIntoRoot(Plan plan, Path targetDir) {
        if (plan.isEmpty() || plan.asNotebooks()) {
            return false;
        }
        boolean imported = false;
        for (Candidate c : plan.candidates()) {
            imported |= copyInto(c, targetDir);
        }
        return imported;
    }

    /** 書き込み済みの notebooks から、JourneyMap のフォルダ名で作ったメモ帳（origin = jm010）の id。無ければ null。 */
    public static String idOf(Notebooks written, String journeyMapFolderName) {
        String name = NotebookFile.cleanName(journeyMapFolderName);
        for (Notebook nb : written.notebooks()) {
            if (NotebookFile.ORIGIN_JM010.equals(nb.origin()) && nb.name().equals(name)) {
                return nb.id();
            }
        }
        return null;
    }

    /**
     * 候補が2つ以上の時: 候補ごとにメモ帳（origin = jm010）を notebooks.json に記録し（書き込み用スレッドで書いて読み直す）、
     * そのフォルダへコピーする。1つずつ順に行う。mode・bindings は変えない。
     *
     * <p>mainThread は {@code registry} を触る（commit / abandon）ための主スレッドの Executor。
     * 全部成功したら、アドレスのフォルダにも印を置き、取り込んだメモ帳の数で完了する（通知は数が 1 以上の時）。
     * 失敗した所で止まり、印を置かず、例外つきで完了する（次に鍵が変わった時にやり直す。済んだ分はそのまま使われる）。
     */
    public static CompletableFuture<Integer> importAsNotebooks(NotebookRegistry registry, Path addressDir,
            Plan plan, DebouncedWriter writer, Executor mainThread, Instant now) {
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (Candidate c : plan.candidates()) {
            chain = chain.thenCompose(count -> importOne(registry, addressDir, c, writer, mainThread, now)
                    .thenApply(done -> count + 1));
        }
        return chain.thenCompose(count -> writer.submit(() -> {
            writeMarker(addressDir);
            return count;
        }));
    }

    private static CompletableFuture<Void> importOne(NotebookRegistry registry, Path addressDir, Candidate c,
            DebouncedWriter writer, Executor mainThread, Instant now) {
        // 提案を作る・commit する所は主スレッド
        CompletableFuture<String> recorded = new CompletableFuture<>();
        mainThread.execute(() -> {
            try {
                Notebooks proposal = registry.proposeJm010(addressDir, c.folderName(), now);
                if (proposal == null) {
                    // 同じメモ帳が記録済みならそれを使う。読めない・書き込み中ならやり直し
                    String id = idOf(registry.notebooks(addressDir), c.folderName());
                    if (id == null) {
                        recorded.completeExceptionally(new IOException("メモ帳を記録できない（読めないか、書き込み中）"));
                    } else {
                        recorded.complete(id);
                    }
                    return;
                }
                NotebookRegistry.persist(addressDir, proposal, writer).whenComplete((written, error) ->
                        mainThread.execute(() -> {
                            if (error != null) {
                                registry.abandon(addressDir);
                                recorded.completeExceptionally(error);
                                return;
                            }
                            registry.commit(addressDir, written);
                            String id = idOf(written, c.folderName());
                            if (id == null) {
                                recorded.completeExceptionally(new IOException("記録したメモ帳が見つからない"));
                            } else {
                                recorded.complete(id);
                            }
                        }));
            } catch (RuntimeException e) {
                recorded.completeExceptionally(e);
            }
        });
        return recorded.thenCompose(id -> writer.submit(() -> {
            if (!copyInto(c, addressDir.resolve(id))) {
                throw new IOException("0.1.0 のメモを取り込めない: " + c.dir());
            }
            return null;
        }));
    }
}
