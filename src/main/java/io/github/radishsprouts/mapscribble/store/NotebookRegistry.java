package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.store.NotebookFile.Mode;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebook;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * アドレスごとの notebooks.json をメモリに持ち、書き込み先のメモ帳を決める（v0.4 設計 2.5・2.6・4.2〜4.4）。
 * Minecraft に依存しない。主スレッドから使う。
 *
 * <p>状態を変える操作は「提案（proposal）を作る → 書き込み用スレッドで書いて読み直す（{@link #persist}）
 * → 成功したら主スレッドで {@link #commit}、失敗したら {@link #abandon}」の順で行う。
 * 書けたと確かめるまでメモリ上の状態を変えない（記録が残らないまま新しいメモ帳へ書かないため）。
 */
public final class NotebookRegistry {
    /** mode = auto のアドレスで、紐付けるハッシュの数の上限。これを超える新しいハッシュで自動の作成を止める。 */
    public static final int AUTO_LIMIT = 8;

    private static final int ID_BODY_MAX = 40;
    private static final DateTimeFormatter MANUAL_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    /** 利用者が手で選んだメモ帳。notebookId が null なら直下を選んだ。その接続・そのハッシュの間だけ有効。 */
    public record ManualSelection(long seedHash, String notebookId) {
    }

    /**
     * resolve の結果。proposal が null でなければ、呼び出し側は persist して commit（失敗なら abandon）する。
     * それまで notebookId は null（直下）。
     */
    public record Resolution(String notebookId, Notebooks proposal) {
    }

    private static final class Entry {
        Status status;
        Notebooks data;
        String detail;
        /** 書き込み中の提案がある。重ねて提案しない。 */
        boolean pending;
        /** このセッションで書き込みに失敗した。自動の提案を繰り返さない。 */
        boolean failed;
    }

    private final Map<Path, Entry> entries = new HashMap<>();

    private Entry entry(Path addressDir) {
        return entries.computeIfAbsent(addressDir.toAbsolutePath().normalize(), dir -> {
            NotebookFile.ReadResult r = NotebookFile.read(dir.resolve(NotebookFile.FILE_NAME));
            Entry e = new Entry();
            e.status = r.status();
            e.data = r.notebooks();
            e.detail = r.detail();
            return e;
        });
    }

    private Entry existing(Path addressDir) {
        return entries.get(addressDir.toAbsolutePath().normalize());
    }

    /** そのアドレスの notebooks.json の読み込み結果。最初の1回だけファイルを読む。 */
    public Status status(Path addressDir) {
        return entry(addressDir).status;
    }

    public String detail(Path addressDir) {
        return entry(addressDir).detail;
    }

    /** 今の内容。読めない時は空。 */
    public Notebooks notebooks(Path addressDir) {
        return entry(addressDir).data;
    }

    public static Path fileOf(Path addressDir) {
        return addressDir.resolve(NotebookFile.FILE_NAME);
    }

    /** 書き込み先のメモ帳の id。null は直下。proposal は無視する（呼び出し側が不要な時用）。 */
    public String resolve(Path addressDir, long seedHash, ManualSelection manual) {
        return resolveFull(addressDir, seedHash, manual, null).notebookId();
    }

    /** 設計 4.4 の順で決める。 */
    public Resolution resolveFull(Path addressDir, long seedHash, ManualSelection manual, IntFunction<String> nameOf) {
        Entry e = entry(addressDir);
        // 1. 手で選んだもの（読めない時でも、id の形が正しければ使う）
        if (manual != null && manual.seedHash() == seedHash) {
            String id = manual.notebookId();
            if (id == null) {
                return new Resolution(null, null);
            }
            if (NotebookFile.ID_PATTERN.matcher(id).matches()
                    && (!e.status.canWrite() || e.data.find(id) != null)) {
                return new Resolution(id, null);
            }
        }
        // 2. 記録が読めない・無い・未決・分けない
        if (!e.status.canWrite() || e.data.mode() == null || e.data.mode() == Mode.OFF) {
            return new Resolution(null, null);
        }
        // 3. ハッシュが取れない
        if (seedHash == 0L) {
            return new Resolution(null, null);
        }
        // 4. 紐付け済み
        String bound = e.data.bindings().get(seedHash);
        if (bound != null && e.data.find(bound) != null) {
            return new Resolution(bound, null);
        }
        // 5. 自動の作成を止めている
        if (e.data.autoStopped() || e.pending || e.failed) {
            return new Resolution(null, null);
        }
        // 6. 新しいハッシュ。書いてから使う（それまでは直下）。mode = auto だけ上限を数える（2.6）
        if (e.data.mode() == Mode.AUTO && e.data.bindings().size() >= AUTO_LIMIT) {
            e.pending = true;
            return new Resolution(null, e.data.withAutoStopped(true));
        }
        String id = seedId(seedHash);
        Notebooks next = e.data;
        if (next.find(id) == null) {
            String name = nextAutoName(next, nameOf);
            next = next.withNotebook(new Notebook(id, name, Instant.now(), NotebookFile.ORIGIN_SEED));
        }
        e.pending = true;
        return new Resolution(null, next.withBinding(seedHash, id));
    }

    /**
     * 自動で作るメモ帳の名前。nameOf(n) が、この接続先の既存のメモ帳の名前と重ならない最小の n の名前を返す
     * （重なりの比べ方は {@link NotebookFile#sameName}）。nameOf が null なら空の名前（画面が「名前なし」と表示する）。
     */
    static String nextAutoName(Notebooks current, IntFunction<String> nameOf) {
        if (nameOf == null) {
            return "";
        }
        for (int n = 1; ; n++) {
            String candidate = NotebookFile.cleanName(nameOf.apply(n));
            boolean used = false;
            for (Notebook nb : current.notebooks()) {
                if (NotebookFile.sameName(nb.name(), candidate)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
        }
    }

    /**
     * 「同じ接続の中でハッシュが変わった」時の、分ける記録の提案（設計 2.5 の 5〜6）。
     * 提案できない時（読めない・mode が既に決まっている・書き込み中・失敗済み）は null。
     * 読めない時の通知は、呼び出し側が {@link #status} を見て行う。
     */
    public Notebooks proposeSplit(Path addressDir, long previous, long current, Instant now,
            IntFunction<String> nameOf) {
        Entry e = entry(addressDir);
        if (!e.status.canWrite() || e.data.mode() != null || e.pending || e.failed) {
            return null;
        }
        Notebooks n = e.data.withMode(Mode.AUTO).withSplitAt(now);
        for (long hash : new long[] {previous, current}) {
            String id = seedId(hash);
            if (n.find(id) == null) {
                n = n.withNotebook(new Notebook(id, nextAutoName(n, nameOf), now, NotebookFile.ORIGIN_SEED));
            }
            n = n.withBinding(hash, id);
        }
        e.pending = true;
        return n;
    }

    /** 手でメモ帳を作る提案。作っただけでは切り替えない。読めない・書き込み中なら null。 */
    public Notebooks proposeManual(Path addressDir, String name, Instant now) {
        Entry e = entry(addressDir);
        if (!e.status.canWrite() || e.pending) {
            return null;
        }
        String id = manualId(now, taken(addressDir, e.data));
        e.pending = true;
        return e.data.withNotebook(new Notebook(id, NotebookFile.cleanName(name), now, NotebookFile.ORIGIN_MANUAL));
    }

    /**
     * 0.1.0 のメモを取り込むメモ帳（origin = "jm010"）を足す提案。mode・bindings は変えない（未決のまま）。
     * 同じ id（同じ JourneyMap のフォルダ名）が既にあれば、そのメモ帳をそのまま使うので提案しない（null）。
     * 読めない・書き込み中も null。作られる id は {@link #jm010Id} と同じ規則。
     */
    public Notebooks proposeJm010(Path addressDir, String journeyMapFolderName, Instant now) {
        Entry e = entry(addressDir);
        if (!e.status.canWrite() || e.pending) {
            return null;
        }
        for (Notebook nb : e.data.notebooks()) {
            if (nb.origin().equals(NotebookFile.ORIGIN_JM010)
                    && nb.name().equals(NotebookFile.cleanName(journeyMapFolderName))) {
                return null;
            }
        }
        // フォルダが既にあっても id を使い回さない（別のものの上に書かない）
        String id = jm010Id(journeyMapFolderName, taken(addressDir, e.data));
        e.pending = true;
        return e.data.withNotebook(new Notebook(id, NotebookFile.cleanName(journeyMapFolderName), now,
                NotebookFile.ORIGIN_JM010));
    }

    /** 書き込み用スレッドで proposal を書き、読み直して同じ内容か確かめる。失敗は例外つきの完了で返る。 */
    public static CompletableFuture<Notebooks> persist(Path addressDir, Notebooks proposal, DebouncedWriter writer) {
        Path file = fileOf(addressDir);
        return writer.submit(() -> {
            NotebookFile.write(file, proposal);
            NotebookFile.ReadResult back = NotebookFile.read(file);
            if (back.status() != Status.OK || !NotebookFile.toJson(back.notebooks())
                    .equals(NotebookFile.toJson(proposal))) {
                throw new java.io.IOException("notebooks.json を読み直したが内容が合わない: " + back.status());
            }
            return back.notebooks();
        });
    }

    /** persist が成功した後、主スレッドで呼ぶ。メモリ上の状態を差し替える。 */
    public void commit(Path addressDir, Notebooks written) {
        Entry e = entry(addressDir);
        e.status = Status.OK;
        e.data = written;
        e.detail = "";
        e.pending = false;
    }

    /** persist が失敗した後、主スレッドで呼ぶ。メモリ上は変えず、自動の提案をこのセッションでは止める。 */
    public void abandon(Path addressDir) {
        Entry e = entry(addressDir);
        e.pending = false;
        e.failed = true;
    }

    /** 提案を作ったが書かずに捨てる時（待ち状態だけ戻す。失敗扱いにはしない）。 */
    public void cancel(Path addressDir) {
        Entry e = existing(addressDir);
        if (e != null) {
            e.pending = false;
        }
    }

    // ---- id の作り方（設計 4.3） ----

    public static String seedId(long seedHash) {
        return "nb-" + NotebookFile.hex(seedHash);
    }

    public static String manualId(Instant now, Predicate<String> taken) {
        return unique("m" + MANUAL_STAMP.format(now), taken);
    }

    /** 0.1.0 の取り込み。JourneyMap のフォルダ名を小文字にし、[^0-9a-z] を - に置き換え、40文字に切る。 */
    public static String jm010Id(String journeyMapFolderName, Predicate<String> taken) {
        String s = journeyMapFolderName.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z]", "-");
        return unique("j" + s, taken);
    }

    private static String unique(String body, Predicate<String> taken) {
        String base = cut(body, ID_BODY_MAX);
        String id = "nb-" + base;
        for (int n = 2; taken.test(id); n++) {
            String suffix = "-" + n;
            id = "nb-" + cut(body, ID_BODY_MAX - suffix.length()) + suffix;
        }
        return id;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Predicate<String> taken(Path addressDir, Notebooks data) {
        return id -> data.find(id) != null || Files.exists(addressDir.resolve(id));
    }
}
