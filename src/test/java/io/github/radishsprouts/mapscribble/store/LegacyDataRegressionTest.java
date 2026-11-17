package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.Jm010Import.Plan;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * 過去の版（0.1.0・0.2.0・0.3.0 …）が実際に書いたメモのファイルを、今のコードで開く回帰試験
 * （設計書 7.3、プラン 01章「過去のデータを消さない」7）。
 *
 * <p>資料は {@code src/test/resources/legacy/<版>/{mp,sp}/...}。版のフォルダを足すだけで試験が増える
 * （README.txt に「パス」と「sha256」の組を書き足す）。資料は一度コミットしたら変えない。
 * 試験は資料を一時フォルダへコピーしてから使い、リソースそのものには書かない。
 *
 * <p>確かめること: 全部読める・件数と中身が同じ・元のファイルがバイト単位で変わらず、増えたファイルも無い。
 */
class LegacyDataRegressionTest {
    private static final Instant T = Instant.parse("2026-11-17T12:34:56Z");
    private static final String OVERWORLD = "minecraft__overworld.json";

    /** 資料の中身（README の記述）から取った、種類・色・文字の期待値。独自に JSON を読む期待値とは別の、書き写した値。 */
    private static final Map<String, List<String>> EXPECTED = new HashMap<>();

    static {
        String mp1 = "v0.1.0/mp/journeymap/data/mp/MCP~Debug/addon-data/mapscribble/";
        String sp1 = "v0.1.0/sp/journeymap/data/sp/New World/addon-data/mapscribble/";
        EXPECTED.put(mp1 + "minecraft__overworld.json", List.of(
                "REGION #FF6E61 拠点エリア Base 🏠", "REGION #5C9DFF ", "LINE #7CFC00 線 Green line 🌲",
                "TEXT #FF9500 こんにちは Hello 😀"));
        EXPECTED.put(mp1 + "minecraft__the_nether.json", List.of(
                "TEXT #C584FF ネザー Nether text", "LINE #FF6FD0 Nether line", "REGION #FF9500 ネザー区画 Nether zone"));
        EXPECTED.put(sp1 + "minecraft__overworld.json", List.of(
                "REGION #FF9500 SP区画 SP zone 🏠", "LINE #FF9500 SP線 SP line", "TEXT #FF9500 SP文字 SP text"));
        EXPECTED.put(sp1 + "minecraft__the_nether.json", List.of(
                "REGION #FF9500 SPネザー区画 SP nether zone", "LINE #FF9500 SPネザー線 SP nether line",
                "TEXT #FF9500 SPネザー文字 SP nether text"));
        String mp2 = "v0.2.0/mp/mapscribble/servers/localhost_25565/";
        String sp2 = "v0.2.0/sp/mapscribble/saves/New World/";
        EXPECTED.put(mp2 + "minecraft__overworld.json", List.of(
                "REGION #FF6E61 v020 MP区画 zone", "LINE #FF6E61 v020 MP線 line", "TEXT #FF6E61 v020 MP文字 text 😀"));
        EXPECTED.put(mp2 + "minecraft__the_nether.json", List.of(
                "REGION #FF6E61 v020 MPネザー区画", "LINE #FF6E61 v020 MPネザー線", "TEXT #FF6E61 v020 MPネザー文字 😀"));
        EXPECTED.put(sp2 + "minecraft__overworld.json", List.of(
                "REGION #FF6E61 v020 SP区画 zone", "LINE #FF6E61 v020 SP線 line", "TEXT #FF6E61 v020 SP文字 text"));
        EXPECTED.put(sp2 + "minecraft__the_nether.json", List.of(
                "REGION #FF6E61 v020 SPネザー区画", "LINE #FF6E61 v020 SPネザー線", "TEXT #FF6E61 v020 SPネザー文字 😀"));
        String mp3 = "v0.3.0/mp/mapscribble/servers/localhost_25565/";
        String sp3 = "v0.3.0/sp/mapscribble/saves/New World/";
        EXPECTED.put(mp3 + "minecraft__overworld.json", List.of(
                "TEXT #FF9500 v030 MP文字 text 😀", "LINE #FF9500 v030 MP線 line", "REGION #FF9500 v030 MP区画 zone"));
        EXPECTED.put(mp3 + "minecraft__the_nether.json", List.of(
                "REGION #FF9500 v030 MPネザー区画", "LINE #FF9500 v030 MPネザー線", "TEXT #FF9500 v030 MPネザー文字"));
        EXPECTED.put(sp3 + "minecraft__overworld.json", List.of(
                "REGION #FF9500 v030 SP区画 zone", "LINE #FF9500 v030 SP線 line", "TEXT #FF9500 v030 SP文字 text"));
        EXPECTED.put(sp3 + "minecraft__the_nether.json", List.of(
                "TEXT #FF9500 v030 SPネザー文字 😀", "LINE #FF9500 v030 SPネザー線", "REGION #FF9500 v030 SPネザー区画"));
    }

    // ------------------------------------------------------------------ 資料の探索

    /** 1つの資料ファイル。rel は legacy/ からの相対パス（/ 区切り）。 */
    private record Fixture(String rel, Path file) {
        String version() {
            return rel.substring(0, rel.indexOf('/'));
        }

        /** 版の下の最初の段（mp / sp）。 */
        String side() {
            String[] p = rel.split("/");
            return p[1];
        }

        /** 0.1.0 の置き場所（journeymap/data/...）か。 */
        boolean isJm010() {
            return rel.contains("/journeymap/data/");
        }
    }

    private static Path root;

    private static synchronized Path root() {
        if (root == null) {
            try {
                var url = LegacyDataRegressionTest.class.getResource("/legacy");
                assertNotNull(url, "資料のフォルダ src/test/resources/legacy が無い");
                root = Path.of(url.toURI());
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        }
        return root;
    }

    private static String relOf(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }

    private static List<Fixture> fixtures() throws IOException {
        List<Fixture> list = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root())) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(p -> list.add(new Fixture(relOf(root(), p), p)));
        }
        return list;
    }

    private static List<String> versions() throws IOException {
        try (Stream<Path> s = Files.list(root())) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).filter(n -> n.startsWith("v"))
                    .sorted().collect(Collectors.toList());
        }
    }

    /** README.txt の「パス」＋次行の「sha256 ...」の組を読む。 */
    private static Map<String, String> readmeHashes() throws IOException {
        Map<String, String> map = new HashMap<>();
        List<String> lines = Files.readAllLines(root().resolve("README.txt"), StandardCharsets.UTF_8);
        for (int i = 0; i + 1 < lines.size(); i++) {
            String line = lines.get(i);
            String next = lines.get(i + 1).trim();
            if (line.startsWith("v") && line.endsWith(".json") && next.startsWith("sha256 ")) {
                map.put(line.trim(), next.substring("sha256 ".length()).trim());
            }
        }
        return map;
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------ 共通の道具

    /** 資料の1つの版・mp/sp の下を、一時フォルダへそのまま（相対パスを保って）コピーする。 */
    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
        }
    }

    /** dir の下の全ファイルの 相対パス → バイト列。増減・変化を見るため。 */
    private static Map<String, byte[]> snapshot(Path dir) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        if (!Files.exists(dir)) {
            return map;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                map.put(relOf(dir, p), Files.readAllBytes(p));
            }
        }
        return map;
    }

    private static void assertSameTree(Map<String, byte[]> before, Map<String, byte[]> after, String what) {
        assertEquals(before.keySet(), after.keySet(), what + ": ファイルが増減した");
        for (var e : before.entrySet()) {
            assertArrayEquals(e.getValue(), after.get(e.getKey()), what + ": " + e.getKey() + " がバイト単位で変わった");
        }
    }

    /** MemoFile を使わずに、資料の JSON から期待するメモを組み立てる。 */
    private static List<Memo> expectedMemos(Path file) throws IOException {
        JsonObject obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, obj.get("format").getAsInt(), "資料は format 1");
        List<Memo> out = new ArrayList<>();
        for (JsonElement e : obj.getAsJsonArray("memos")) {
            JsonObject o = e.getAsJsonObject();
            List<MapPoint> points = new ArrayList<>();
            for (JsonElement p : o.getAsJsonArray("points")) {
                JsonArray xz = p.getAsJsonArray();
                points.add(new MapPoint(xz.get(0).getAsDouble(), xz.get(1).getAsDouble()));
            }
            out.add(new Memo(UUID.fromString(o.get("id").getAsString()), MemoKind.valueOf(o.get("kind").getAsString()),
                    points, Integer.parseInt(o.get("color").getAsString().substring(1), 16),
                    o.get("width").getAsInt(), o.get("fillOpacity").getAsFloat(), o.get("text").getAsString(),
                    o.get("textScale").getAsFloat(), o.get("onMinimap").getAsBoolean(),
                    Instant.parse(o.get("created").getAsString()), Instant.parse(o.get("updated").getAsString())));
        }
        return out;
    }

    private static String describe(Memo m) {
        return m.kind() + " #" + String.format("%06X", m.color()) + " " + m.text();
    }

    private static MoveResult moveSync(MemoStore store, java.util.Collection<UUID> ids, MoveDirection direction) {
        CompletableFuture<MoveResult> future = new CompletableFuture<>();
        store.move(ids, direction, future::complete);
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static DebouncedWriter noopWriter() {
        return new DebouncedWriter(60_000, (file, snapshot) -> { });
    }

    private static List<Fixture> directFixtures() throws IOException {
        return fixtures().stream().filter(f -> !f.isJm010()).collect(Collectors.toList());
    }

    private static List<Fixture> jm010Fixtures() throws IOException {
        return fixtures().stream().filter(Fixture::isJm010).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ 資料そのものの確かめ

    @Test
    void 資料の版は0_1_0と0_2_0と0_3_0がそろっている() throws IOException {
        List<String> versions = versions();
        assertTrue(versions.containsAll(List.of("v0.1.0", "v0.2.0", "v0.3.0")), "版: " + versions);
    }

    @Test
    void 資料は全部READMEのSHA256と同じで_READMEに載っていないjsonも無い() throws IOException {
        Map<String, String> hashes = readmeHashes();
        List<Fixture> all = fixtures();
        assertFalse(all.isEmpty());
        assertEquals(all.stream().map(Fixture::rel).sorted().collect(Collectors.toList()),
                hashes.keySet().stream().sorted().collect(Collectors.toList()),
                "資料のファイルと README の一覧が一致しない");
        for (Fixture f : all) {
            assertEquals(hashes.get(f.rel()), sha256(f.file()),
                    f.rel() + " が README の SHA-256 と違う（改行変換・手編集の疑い）");
        }
    }

    @Test
    void 資料の件数と種類と色と文字がREADMEの記述と同じ() throws IOException {
        for (Fixture f : fixtures()) {
            List<String> expected = EXPECTED.get(f.rel());
            if (expected == null) {
                continue; // 後から足した版は、独自に JSON を読む試験（下）で守る
            }
            assertEquals(expected, expectedMemos(f.file()).stream().map(LegacyDataRegressionTest::describe)
                    .collect(Collectors.toList()), f.rel());
        }
        assertTrue(EXPECTED.keySet().stream().allMatch(k -> Files.exists(root().resolve(k))), "期待値の表に載せたパスが無い");
        // 0.1.0 のマルチのオーバーワールドは4件で、2件目は文字が空・onMinimap が false の REGION
        Path first = root().resolve(
                "v0.1.0/mp/journeymap/data/mp/MCP~Debug/addon-data/mapscribble/minecraft__overworld.json");
        List<Memo> four = expectedMemos(first);
        assertEquals(4, four.size());
        assertEquals(MemoKind.REGION, four.get(1).kind());
        assertEquals("", four.get(1).text());
        assertFalse(four.get(1).onMinimap());
    }

    // ------------------------------------------------------------------ 読める・同じ・変わらない（全ファイル）

    @TestFactory
    Stream<DynamicTest> 全ファイルを今の読み込みで読める_件数も中身も同じ_元は変わらない(@TempDir Path tmp) throws IOException {
        Path copy = tmp.resolve("copy");
        copyTree(root(), copy);
        Map<String, byte[]> before = snapshot(copy);
        List<DynamicTest> tests = new ArrayList<>();
        for (Fixture f : fixtures()) {
            tests.add(dynamicTest(f.rel(), () -> {
                Path file = copy.resolve(f.rel());
                MemoFile.ReadResult r = MemoFile.read(file);
                assertEquals(MemoFile.Status.OK, r.status(), r.detail());
                assertTrue(r.canWrite());
                assertEquals(expectedMemos(f.file()), r.memos(), "中身（種類・座標・色・文字・onMinimap・日時）が違う");
                // 書き戻した時の形も、読み直して同じ
                assertEquals(r.memos(), MemoFile.parse(MemoFile.toJson(r.memos())).memos());
            }));
        }
        tests.add(dynamicTest("読んだ後も全ファイルがバイト単位で同じ", () -> assertSameTree(before, snapshot(copy), "読み込み後")));
        return tests.stream();
    }

    // ------------------------------------------------------------------ 0.2.0 以降（直下）

    @TestFactory
    Stream<DynamicTest> 直下のファイルをメモ帳なしで開いても_メモ帳ありで開いても全部見える(@TempDir Path tmp) throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Fixture f : directFixtures()) {
            tests.add(dynamicTest(f.rel(), () -> {
                Path dir = tmp.resolve(f.rel().replace('/', '_'));
                Path file = dir.resolve(f.file().getFileName().toString());
                Files.createDirectories(dir);
                Files.copy(f.file(), file);
                Map<String, byte[]> before = snapshot(dir);
                List<Memo> expected = expectedMemos(f.file());

                // メモ帳を開いていない状態
                MemoStore plain = new MemoStore(5000);
                try {
                    plain.open(file);
                    assertTrue(plain.isAvailable());
                    assertEquals(expected, plain.memos());
                    plain.close();
                } finally {
                    plain.shutdown();
                }
                assertSameTree(before, snapshot(dir), "メモ帳なしで開いて閉じた後");

                // メモ帳を開いた状態（直下は「分ける前のメモ」として全部見える。メモ帳のファイルは作らない）
                Path nbFile = dir.resolve("nb-1").resolve(file.getFileName().toString());
                MemoStore withNb = new MemoStore(5000);
                try {
                    withNb.open(nbFile, file);
                    assertEquals(expected, withNb.memos());
                    for (Memo m : expected) {
                        assertTrue(withNb.isLegacy(m.id()), "分ける前のメモとして扱われていない: " + describe(m));
                    }
                    withNb.close();
                } finally {
                    withNb.shutdown();
                }
                assertSameTree(before, snapshot(dir), "メモ帳ありで開いて閉じた後");
                assertFalse(Files.exists(dir.resolve("nb-1")), "メモ帳のフォルダを作ってはいけない");
            }));
        }
        return tests.stream();
    }

    /** NotebookRegistry で別々のメモ帳を選んでも、直下のメモはどのメモ帳からも全部見える（全サーバー共通）。 */
    @TestFactory
    Stream<DynamicTest> 直下のメモは_レジストリで選んだどのメモ帳からも全部見える(@TempDir Path tmp) throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (Fixture f : directFixtures()) {
            tests.add(dynamicTest(f.rel(), () -> {
                Path addr = tmp.resolve(f.rel().replace('/', '_'));
                Path file = addr.resolve(f.file().getFileName().toString());
                Files.createDirectories(addr);
                Files.copy(f.file(), file);
                Map<String, byte[]> before = snapshot(addr);
                List<Memo> expected = expectedMemos(f.file());

                NotebookRegistry reg = new NotebookRegistry();
                // 記録が無い間は直下（どのハッシュでも）
                assertNull(reg.resolve(addr, 0x1111L, null));
                assertNull(reg.resolve(addr, 0x2222L, null));
                assertSameTree(before, snapshot(addr), "resolve しただけ");

                // メモ帳を2つ作り、それぞれを選ぶ
                List<String> ids = new ArrayList<>();
                try (DebouncedWriter w = noopWriter()) {
                    for (String name : List.of("A", "B")) {
                        Notebooks proposal = reg.proposeManual(addr, name, T.plusSeconds(ids.size()));
                        assertNotNull(proposal);
                        Notebooks written = NotebookRegistry.persist(addr, proposal, w).get(5, TimeUnit.SECONDS);
                        reg.commit(addr, written);
                        ids.add(written.notebooks().get(written.notebooks().size() - 1).id());
                    }
                }
                assertEquals(2, ids.size());
                Map<String, byte[]> withRecord = snapshot(addr);
                assertTrue(withRecord.containsKey(NotebookFile.FILE_NAME));
                assertArrayEquals(Files.readAllBytes(f.file()), withRecord.get(file.getFileName().toString()),
                        "notebooks.json を書いても直下のメモのファイルは変わらない");

                for (String id : ids) {
                    String chosen = reg.resolve(addr, 0xABCDL, new NotebookRegistry.ManualSelection(0xABCDL, id));
                    assertEquals(id, chosen);
                    MemoStore store = new MemoStore(5000);
                    try {
                        store.open(addr.resolve(chosen).resolve(file.getFileName().toString()), file);
                        assertEquals(expected, store.memos(), "メモ帳 " + id + " から直下のメモが全部見えない");
                        store.close();
                    } finally {
                        store.shutdown();
                    }
                }
                assertSameTree(withRecord, snapshot(addr), "メモ帳を選んで開いて閉じた後");
            }));
        }
        return tests.stream();
    }

    // ------------------------------------------------------------------ 書き込み後の安全装置

    @TestFactory
    Stream<DynamicTest> 書き込みと移す操作の後も_移す前の控えが残り元の内容が復元できる(@TempDir Path tmp) throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Fixture f : directFixtures()) {
            tests.add(dynamicTest(f.rel(), () -> {
                Path dir = tmp.resolve(f.rel().replace('/', '_'));
                String name = f.file().getFileName().toString();
                Path legacy = dir.resolve(name);
                Path nbFile = dir.resolve("nb-1").resolve(name);
                Path bak = dir.resolve(name + MemoStore.BACKUP_SUFFIX);
                Files.createDirectories(dir);
                Files.copy(f.file(), legacy);
                byte[] original = Files.readAllBytes(f.file());
                List<Memo> expected = expectedMemos(f.file());
                List<UUID> ids = expected.stream().map(Memo::id).collect(Collectors.toList());

                MemoStore store = new MemoStore(5000);
                try {
                    store.open(nbFile, legacy);

                    // 新しいメモの追加はメモ帳へ。直下は1バイトも変わらない
                    Memo added = MemoFileTest.memo("新規 added 😀", new MapPoint(1, 1), new MapPoint(2, 2));
                    store.add(added);
                    store.flush();
                    assertArrayEquals(original, Files.readAllBytes(legacy), "メモを追加しても直下は変わらない");
                    assertEquals(List.of(added), MemoFile.read(nbFile).memos());
                    assertFalse(Files.exists(bak), "移す前は控えを作らない");

                    // 全部をメモ帳へ移す。控えは元の内容とバイト単位で同じ
                    MoveResult moved = moveSync(store, ids, MoveDirection.LEGACY_TO_PRIMARY);
                    assertTrue(moved.ok(), String.valueOf(moved.messageKey()));
                    assertEquals(expected.size(), moved.moved());
                    assertArrayEquals(original, Files.readAllBytes(bak), "控えが元のファイルと同じでない");
                    assertEquals(List.of(), MemoFile.read(legacy).memos(), "移した分は直下から除かれる");
                    List<Memo> inNotebook = new ArrayList<>();
                    inNotebook.add(added);
                    inNotebook.addAll(expected);
                    assertEquals(inNotebook, MemoFile.read(nbFile).memos());

                    // 元に戻す。2つのファイルのメモが元に戻り、控えは残ったまま上書きされない
                    MoveResult back = moveSync(store, ids, MoveDirection.PRIMARY_TO_LEGACY);
                    assertTrue(back.ok());
                    assertEquals(expected, MemoFile.read(legacy).memos());
                    assertEquals(List.of(added), MemoFile.read(nbFile).memos());
                    assertArrayEquals(original, Files.readAllBytes(bak), "元に戻した後も控えは残る");

                    // もう一度移しても、控えは上書きされない（1回だけ）
                    assertTrue(moveSync(store, ids, MoveDirection.LEGACY_TO_PRIMARY).ok());
                    assertArrayEquals(original, Files.readAllBytes(bak), "2回目に控えが上書きされた");
                    store.flush();
                } finally {
                    store.shutdown();
                }
                // 古い版（今と同じ読み込み）でも、直下のファイルは読める
                assertEquals(MemoFile.Status.OK, MemoFile.read(legacy).status());
                assertEquals(MemoFile.Status.OK, MemoFile.read(bak).status());
                assertEquals(expected, MemoFile.read(bak).memos());
            }));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> 分ける前のメモを1件編集すると直下だけが書き換わり他のメモは同じ(@TempDir Path tmp) throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Fixture f : directFixtures()) {
            tests.add(dynamicTest(f.rel(), () -> {
                Path dir = tmp.resolve(f.rel().replace('/', '_'));
                String name = f.file().getFileName().toString();
                Path legacy = dir.resolve(name);
                Path nbFile = dir.resolve("nb-1").resolve(name);
                Files.createDirectories(dir);
                Files.copy(f.file(), legacy);
                List<Memo> expected = expectedMemos(f.file());
                Memo first = expected.get(0);
                Memo edited = new Memo(first.id(), first.kind(), first.points(), first.color(), first.width(),
                        first.fillOpacity(), first.text() + " 編集", first.textScale(), first.onMinimap(),
                        first.created(), T);
                MemoStore store = new MemoStore(5000);
                try {
                    store.open(nbFile, legacy);
                    store.update(edited);
                    store.flush();
                } finally {
                    store.shutdown();
                }
                List<Memo> onDisk = MemoFile.read(legacy).memos();
                assertEquals(expected.size(), onDisk.size());
                assertEquals(edited, onDisk.get(0));
                assertEquals(expected.subList(1, expected.size()), onDisk.subList(1, onDisk.size()));
                assertFalse(Files.exists(nbFile), "メモ帳のファイルは作られない");
                assertFalse(Files.exists(dir.resolve(name + MemoStore.BACKUP_SUFFIX)), "移していないので控えは作らない");
            }));
        }
        return tests.stream();
    }

    // ------------------------------------------------------------------ 0.1.0（JourneyMap の保存先）

    /** 0.1.0 の資料の mp / sp の1つ分。gameDir にコピーした後の、JourneyMap のフォルダ名を返す。 */
    private static String jmFolder(Fixture f) {
        String[] p = f.rel().split("/");
        return p[4]; // v0.1.0 / mp / journeymap / data / mp / <folder> ...
    }

    private static Map<String, Fixture> jmFixturesByFolder(String side) throws IOException {
        Map<String, Fixture> map = new HashMap<>();
        for (Fixture f : jm010Fixtures()) {
            if (f.side().equals(side)) {
                map.put(jmFolder(f) + "/" + f.file().getFileName(), f);
            }
        }
        return map;
    }

    private static Path gameDirOf(Path tmp, String version, String side) throws IOException {
        Path game = tmp.resolve("game-" + version + "-" + side);
        copyTree(root().resolve(version).resolve(side), game);
        return game;
    }

    private static void assertImportedAndOriginalsKept(Path game, Path targetDir, List<Fixture> sourceFiles,
            Map<String, byte[]> originalsBefore) throws IOException {
        for (Fixture f : sourceFiles) {
            Path imported = targetDir.resolve(f.file().getFileName().toString());
            assertTrue(Files.exists(imported), imported + " が取り込まれていない");
            assertArrayEquals(Files.readAllBytes(f.file()), Files.readAllBytes(imported),
                    "取り込んだファイルが元とバイト単位で違う");
            assertEquals(expectedMemos(f.file()), MemoFile.read(imported).memos());
        }
        assertSameTree(originalsBefore, snapshot(game.resolve("journeymap")), "取り込み後の 0.1.0 のファイル");
    }

    @Test
    void v0_1_0のマルチ_候補が1つなら直下へ元を残して全件取り込まれ全部見える(@TempDir Path tmp) throws Exception {
        String version = "v0.1.0";
        Path game = gameDirOf(tmp, version, "mp");
        Map<String, byte[]> originals = snapshot(game.resolve("journeymap"));
        List<Fixture> sources = new ArrayList<>(jmFixturesByFolder("mp").values());
        assertFalse(sources.isEmpty());
        Path addr = game.resolve("mapscribble/servers/localhost_25565");

        Plan plan = Jm010Import.planMultiplayer(game, addr, "MCP Debug", "localhost");
        assertEquals(1, plan.candidates().size());
        assertFalse(plan.asNotebooks());
        assertTrue(Jm010Import.importIntoRoot(plan, addr));
        assertImportedAndOriginalsKept(game, addr, sources, originals);
        assertTrue(Files.exists(addr.resolve(Jm010Import.MARKER)));
        assertTrue(Jm010Import.planMultiplayer(game, addr, "MCP Debug", "localhost").isEmpty(), "2回目は列挙しない");

        // 取り込んだ後に今の MemoStore で開くと、全件見える
        for (Fixture f : sources) {
            MemoStore store = new MemoStore(5000);
            try {
                store.open(addr.resolve("nb-x").resolve(f.file().getFileName().toString()),
                        addr.resolve(f.file().getFileName().toString()));
                assertEquals(expectedMemos(f.file()), store.memos());
                store.close();
            } finally {
                store.shutdown();
            }
        }
        assertSameTree(originals, snapshot(game.resolve("journeymap")), "開いて閉じた後の 0.1.0 のファイル");
    }

    @Test
    void v0_1_0のシングル_セーブ名のフォルダから全件取り込まれ元は変わらない(@TempDir Path tmp) throws Exception {
        Path game = gameDirOf(tmp, "v0.1.0", "sp");
        Map<String, byte[]> originals = snapshot(game.resolve("journeymap"));
        List<Fixture> sources = new ArrayList<>(jmFixturesByFolder("sp").values());
        Path save = game.resolve("mapscribble/saves/New World");

        Plan plan = Jm010Import.planSingleplayer(game, save, "New World");
        assertEquals(1, plan.candidates().size());
        assertTrue(Jm010Import.importIntoRoot(plan, save));
        assertImportedAndOriginalsKept(game, save, sources, originals);
        assertTrue(Jm010Import.planSingleplayer(game, save, "New World").isEmpty());
        for (Fixture f : sources) {
            MemoStore store = new MemoStore(5000);
            try {
                store.open(save.resolve(f.file().getFileName().toString()));
                assertEquals(expectedMemos(f.file()), store.memos());
            } finally {
                store.shutdown();
            }
        }
    }

    @Test
    void v0_1_0のマルチ_直下に同じ名前のファイルがあれば上書きせず_元も変わらない(@TempDir Path tmp) throws Exception {
        Path game = gameDirOf(tmp, "v0.1.0", "mp");
        Map<String, byte[]> originals = snapshot(game.resolve("journeymap"));
        Path addr = game.resolve("mapscribble/servers/localhost_25565");
        Files.createDirectories(addr);
        byte[] mine = "{\"format\":1,\"memos\":[]}".getBytes(StandardCharsets.UTF_8);
        Files.write(addr.resolve(OVERWORLD), mine);

        Plan plan = Jm010Import.planMultiplayer(game, addr, "MCP Debug", "localhost");
        Jm010Import.importIntoRoot(plan, addr);
        assertArrayEquals(mine, Files.readAllBytes(addr.resolve(OVERWORLD)), "既にあるファイルを上書きした");
        assertTrue(Files.exists(addr.resolve("minecraft__the_nether.json")), "無い方は取り込まれる");
        assertSameTree(originals, snapshot(game.resolve("journeymap")), "0.1.0 のファイル");
    }

    @Test
    void v0_1_0のマルチ_候補が2つならメモ帳として全件取り込まれ元は変わらず直下は空のまま(@TempDir Path tmp) throws Exception {
        Path game = gameDirOf(tmp, "v0.1.0", "mp");
        // JourneyMap がプロキシの中のサーバーを分けていた形: 同じ名前で始まるフォルダをもう1つ増やす
        Path data = game.resolve("journeymap/data/mp");
        copyTree(data.resolve("MCP~Debug"), data.resolve("MCP~Debug_2"));
        Map<String, byte[]> originals = snapshot(game.resolve("journeymap"));
        List<Fixture> sources = new ArrayList<>(jmFixturesByFolder("mp").values());
        Path addr = game.resolve("mapscribble/servers/localhost_25565");

        Plan plan = Jm010Import.planMultiplayer(game, addr, "MCP Debug", "localhost");
        assertTrue(plan.asNotebooks());
        assertEquals(2, plan.candidates().size());
        NotebookRegistry reg = new NotebookRegistry();
        try (DebouncedWriter w = noopWriter()) {
            int n = Jm010Import.importAsNotebooks(reg, addr, plan, w, Runnable::run, T).get(5, TimeUnit.SECONDS);
            assertEquals(2, n);
        }
        Notebooks nbs = reg.notebooks(addr);
        assertEquals(2, nbs.notebooks().size());
        assertNull(nbs.mode(), "mode は未決のまま");
        assertFalse(Files.exists(addr.resolve(OVERWORLD)), "直下へは入れない");
        for (String folder : List.of("MCP~Debug", "MCP~Debug_2")) {
            String id = Jm010Import.idOf(nbs, folder);
            assertNotNull(id, folder);
            for (Fixture f : sources) {
                Path imported = addr.resolve(id).resolve(f.file().getFileName().toString());
                assertArrayEquals(Files.readAllBytes(f.file()), Files.readAllBytes(imported));
                MemoStore store = new MemoStore(5000);
                try {
                    store.open(imported, addr.resolve(f.file().getFileName().toString()));
                    assertEquals(expectedMemos(f.file()), store.memos(), "メモ帳 " + folder + " で見えない");
                    store.close();
                } finally {
                    store.shutdown();
                }
            }
        }
        assertSameTree(originals, snapshot(game.resolve("journeymap")), "取り込み後の 0.1.0 のファイル");
        assertTrue(Jm010Import.planMultiplayer(game, addr, "MCP Debug", "localhost").isEmpty(), "2回目は何もしない");
    }

    // ------------------------------------------------------------------ 壊れた・知らない形式（試験の中で作る。資料ではない）

    @Test
    void 壊れたJSONと知らない形式のファイルは_読まず_上書きせず_知らせが出る(@TempDir Path tmp) throws IOException {
        byte[] broken = "{\"format\":1,\"memos\":[{\"id\":".getBytes(StandardCharsets.UTF_8);
        byte[] newer = "{\"format\":2,\"memos\":[]}".getBytes(StandardCharsets.UTF_8);
        Fixture good = directFixtures().get(0);
        for (byte[] bad : List.of(broken, newer)) {
            Path dir = Files.createTempDirectory(tmp, "bad");
            Path legacy = dir.resolve(OVERWORLD);
            Path nbFile = dir.resolve("nb-1").resolve(OVERWORLD);
            Files.write(legacy, bad);
            // メモ帳側には読めるファイルを置いて、主ファイルだけは使えることも確かめる
            Files.createDirectories(nbFile.getParent());
            Files.copy(good.file(), nbFile);
            List<String> problems = new ArrayList<>();
            MemoStore store = new MemoStore(5000);
            try {
                store.setProblemHandler(problems::add);
                store.open(nbFile, legacy);
                assertEquals(expectedMemos(good.file()), store.memos(), "分ける前が壊れていても主ファイルだけは使える");
                assertFalse(problems.isEmpty(), "知らせが出ない");
                store.add(MemoFileTest.memo("x", new MapPoint(0, 0)));
                store.flush();
            } finally {
                store.shutdown();
            }
            assertArrayEquals(bad, Files.readAllBytes(legacy), "読めないファイルに触った");

            // 主ファイルが壊れている場合は、書き込めない（上書きしない）
            Path dir2 = Files.createTempDirectory(tmp, "bad2");
            Path primary = dir2.resolve(OVERWORLD);
            Files.write(primary, bad);
            MemoStore store2 = new MemoStore(5000);
            try {
                store2.setProblemHandler(problems::add);
                store2.open(primary);
                assertFalse(store2.isAvailable());
                store2.add(MemoFileTest.memo("y", new MapPoint(0, 0)));
                store2.flush();
            } finally {
                store2.shutdown();
            }
            assertArrayEquals(bad, Files.readAllBytes(primary), "読めないファイルを上書きした");
        }
    }

    // ------------------------------------------------------------------ 古い版が読めること

    @Test
    void 新しい形式のファイルとメモ帳のフォルダがあっても_直下のファイルは今と同じ読み込みで読める(@TempDir Path tmp) throws Exception {
        Fixture f = directFixtures().get(0);
        Path addr = tmp.resolve("addr");
        Files.createDirectories(addr);
        Path file = addr.resolve(f.file().getFileName().toString());
        Files.copy(f.file(), file);
        NotebookRegistry reg = new NotebookRegistry();
        try (DebouncedWriter w = noopWriter()) {
            Notebooks p = reg.proposeManual(addr, "メモ帳", T);
            reg.commit(addr, NotebookRegistry.persist(addr, p, w).get(5, TimeUnit.SECONDS));
        }
        MemoFile.ReadResult r = MemoFile.read(file);
        assertEquals(MemoFile.Status.OK, r.status());
        assertEquals(expectedMemos(f.file()), r.memos());
        assertArrayEquals(Files.readAllBytes(f.file()), Files.readAllBytes(file));
    }
}
