package io.github.radishsprouts.mapscribble.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * notebooks.json の読み書き（v0.4 設計 4.1・4.2）。状態を持たない。Minecraft に依存しない。
 * 読めない・知らない形式のファイルは上書きしない（{@link Status#canWrite()}、{@link #write}）。
 */
public final class NotebookFile {
    private static final Logger LOGGER = LoggerFactory.getLogger("mapscribble");

    /** 今書いている形式の版。 */
    public static final int FORMAT = 1;
    public static final String FILE_NAME = "notebooks.json";
    /** フォルダ名そのものになる id の形。`..`・`/`・`\`・ドライブ名を通さない（フォルダの外へ書かせない）。 */
    public static final Pattern ID_PATTERN = Pattern.compile("^nb-[0-9a-z][0-9a-z-]{0,39}$");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{16}$");
    public static final int NAME_MAX = 64;

    /** origin の値。 */
    public static final String ORIGIN_SEED = "seed";
    public static final String ORIGIN_MANUAL = "manual";
    public static final String ORIGIN_JM010 = "jm010";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> KNOWN_KEYS = Set.of("format", "mode", "splitAt", "autoStopped", "notebooks",
            "bindings");
    private static final Set<String> KNOWN_NOTEBOOK_KEYS = Set.of("id", "name", "created", "origin");

    private NotebookFile() {
    }

    public enum Status {
        /** ファイルが無い。未判定として始めてよい。 */
        MISSING,
        OK,
        /** 知らない format・mode。読まず、上書きもしない。 */
        UNSUPPORTED,
        /** 壊れた JSON、または読み取りの失敗。上書きしない。 */
        CORRUPT;

        public boolean canWrite() {
            return this == MISSING || this == OK;
        }
    }

    public enum Mode {
        /** 同じ接続の中でハッシュの変化を見て記録した。 */
        AUTO("auto"),
        /** 利用者が分けると入れた。 */
        USER("user"),
        /** 利用者が分けないと選んだ。 */
        OFF("off");

        private final String json;

        Mode(String json) {
            this.json = json;
        }

        public String json() {
            return json;
        }

        static Mode fromJson(String s) {
            for (Mode m : values()) {
                if (m.json.equals(s)) {
                    return m;
                }
            }
            return null;
        }
    }

    /** メモ帳1つ。extra は知らないキー（書き戻しで残す）。 */
    public record Notebook(String id, String name, Instant created, String origin, JsonObject extra) {
        public Notebook(String id, String name, Instant created, String origin) {
            this(id, name, created, origin, new JsonObject());
        }

        public Notebook withName(String newName) {
            return new Notebook(id, cleanName(newName), created, origin, extra);
        }
    }

    /**
     * notebooks.json の中身。変更できない値で、変えた版は with… で作る。
     * mode が null は未決（ファイルに mode が無い）、splitAt が null は未記録。
     * bindings はハッシュ → メモ帳の id、extra は知らないトップレベルのキー。
     */
    public record Notebooks(Mode mode, Instant splitAt, boolean autoStopped, List<Notebook> notebooks,
            Map<Long, String> bindings, JsonObject extra) {
        public Notebooks {
            notebooks = List.copyOf(notebooks);
            bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        }

        public static Notebooks empty() {
            return new Notebooks(null, null, false, List.of(), Map.of(), new JsonObject());
        }

        public Notebook find(String id) {
            for (Notebook n : notebooks) {
                if (n.id().equals(id)) {
                    return n;
                }
            }
            return null;
        }

        public Notebooks withMode(Mode m) {
            return new Notebooks(m, splitAt, autoStopped, notebooks, bindings, extra);
        }

        public Notebooks withSplitAt(Instant t) {
            return new Notebooks(mode, t, autoStopped, notebooks, bindings, extra);
        }

        public Notebooks withAutoStopped(boolean v) {
            return new Notebooks(mode, splitAt, v, notebooks, bindings, extra);
        }

        /** 同じ id が既にあれば置き換え、無ければ末尾へ足す。 */
        public Notebooks withNotebook(Notebook nb) {
            List<Notebook> list = new ArrayList<>(notebooks);
            boolean replaced = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(nb.id())) {
                    list.set(i, nb);
                    replaced = true;
                }
            }
            if (!replaced) {
                list.add(nb);
            }
            return new Notebooks(mode, splitAt, autoStopped, list, bindings, extra);
        }

        public Notebooks withBinding(long seedHash, String notebookId) {
            Map<Long, String> map = new LinkedHashMap<>(bindings);
            map.put(seedHash, notebookId);
            return new Notebooks(mode, splitAt, autoStopped, notebooks, map, extra);
        }
    }

    public record ReadResult(Status status, Notebooks notebooks, String detail) {
    }

    /** 16桁の小文字16進（負の値も2の補数）。 */
    public static String hex(long seedHash) {
        return String.format("%016x", seedHash);
    }

    public static ReadResult read(Path file) {
        if (!Files.exists(file)) {
            return new ReadResult(Status.MISSING, Notebooks.empty(), "");
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ReadResult(Status.CORRUPT, Notebooks.empty(), "読み取りに失敗: " + e.getMessage());
        }
        return parse(text);
    }

    public static ReadResult parse(String text) {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return new ReadResult(Status.CORRUPT, Notebooks.empty(), "最上位がオブジェクトではない");
            }
            JsonObject obj = root.getAsJsonObject();
            JsonElement format = obj.get("format");
            if (format == null || !format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()) {
                return new ReadResult(Status.UNSUPPORTED, Notebooks.empty(), "format が無い");
            }
            if (format.getAsInt() != FORMAT) {
                return new ReadResult(Status.UNSUPPORTED, Notebooks.empty(), "format " + format.getAsInt());
            }
            Mode mode = null;
            JsonElement modeEl = obj.get("mode");
            if (modeEl != null && !modeEl.isJsonNull()) {
                mode = modeEl.isJsonPrimitive() ? Mode.fromJson(modeEl.getAsString()) : null;
                if (mode == null) {
                    return new ReadResult(Status.UNSUPPORTED, Notebooks.empty(), "知らない mode: " + modeEl);
                }
            }
            Instant splitAt = null;
            JsonElement splitEl = obj.get("splitAt");
            if (splitEl != null && !splitEl.isJsonNull()) {
                try {
                    splitAt = Instant.parse(splitEl.getAsString());
                } catch (RuntimeException e) {
                    LOGGER.warn("MapScribble: notebooks.json の splitAt を読めないので捨てる: {}", splitEl);
                }
            }
            boolean autoStopped = obj.has("autoStopped") && obj.get("autoStopped").isJsonPrimitive()
                    && obj.get("autoStopped").getAsBoolean();

            List<Notebook> notebooks = new ArrayList<>();
            Set<String> ids = new LinkedHashSet<>();
            JsonElement arr = obj.get("notebooks");
            if (arr != null && arr.isJsonArray()) {
                for (JsonElement e : arr.getAsJsonArray()) {
                    Notebook nb = notebookFromJson(e);
                    if (nb == null) {
                        continue;
                    }
                    if (!ids.add(nb.id())) {
                        LOGGER.warn("MapScribble: notebooks.json の id が重複しているので捨てる: {}", nb.id());
                        continue;
                    }
                    notebooks.add(nb);
                }
            }
            Map<Long, String> bindings = new LinkedHashMap<>();
            JsonElement bind = obj.get("bindings");
            if (bind != null && bind.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : bind.getAsJsonObject().entrySet()) {
                    String value = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : null;
                    if (!HASH_PATTERN.matcher(e.getKey()).matches() || value == null || !ids.contains(value)) {
                        LOGGER.warn("MapScribble: notebooks.json の bindings の項目を捨てる: {}", e.getKey());
                        continue;
                    }
                    bindings.put(Long.parseUnsignedLong(e.getKey(), 16), value);
                }
            }
            JsonObject extra = new JsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (!KNOWN_KEYS.contains(e.getKey())) {
                    extra.add(e.getKey(), e.getValue());
                }
            }
            return new ReadResult(Status.OK, new Notebooks(mode, splitAt, autoStopped, notebooks, bindings, extra),
                    "");
        } catch (RuntimeException e) {
            return new ReadResult(Status.CORRUPT, Notebooks.empty(), String.valueOf(e.getMessage()));
        }
    }

    /** 検査に外れたら null（その項目だけ捨てる）。 */
    private static Notebook notebookFromJson(JsonElement e) {
        try {
            JsonObject o = e.getAsJsonObject();
            String id = o.get("id").getAsString();
            if (!ID_PATTERN.matcher(id).matches()) {
                LOGGER.warn("MapScribble: notebooks.json の不正な id を捨てる: {}", id);
                return null;
            }
            String name = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : "";
            Instant created = Instant.parse(o.get("created").getAsString());
            String origin = o.has("origin") && o.get("origin").isJsonPrimitive() ? o.get("origin").getAsString()
                    : ORIGIN_MANUAL;
            JsonObject extra = new JsonObject();
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                if (!KNOWN_NOTEBOOK_KEYS.contains(en.getKey())) {
                    extra.add(en.getKey(), en.getValue());
                }
            }
            return new Notebook(id, cleanName(name), created, origin, extra);
        } catch (RuntimeException ex) {
            LOGGER.warn("MapScribble: notebooks.json のメモ帳の項目を読めないので捨てる: {}", ex.toString());
            return null;
        }
    }

    /**
     * 名前の重複の比べ方（設計書 4.5）。前後の空白を除いて NFKC で正規化し、小文字にしたものが等しければ同じ名前。
     * 大文字・小文字、全角・半角の違いは同じとみなす。保存は入力どおりで、ここは比べるためだけに使う。
     */
    public static boolean sameName(String a, String b) {
        return foldName(a).equals(foldName(b));
    }

    private static String foldName(String s) {
        String t = s == null ? "" : s.strip();
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT);
    }

    /** 制御文字を除き、64 文字（コードポイント）までに切る。 */
    public static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String s = name.replaceAll("\\p{Cc}", "");
        if (s.codePointCount(0, s.length()) > NAME_MAX) {
            s = s.substring(0, s.offsetByCodePoints(0, NAME_MAX));
        }
        return s;
    }

    public static String toJson(Notebooks n) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        if (n.mode() != null) {
            root.addProperty("mode", n.mode().json());
        }
        if (n.splitAt() != null) {
            root.addProperty("splitAt", n.splitAt().toString());
        }
        root.addProperty("autoStopped", n.autoStopped());
        JsonArray arr = new JsonArray();
        for (Notebook nb : n.notebooks()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", nb.id());
            o.addProperty("name", nb.name());
            o.addProperty("created", nb.created().toString());
            o.addProperty("origin", nb.origin());
            for (Map.Entry<String, JsonElement> e : nb.extra().entrySet()) {
                o.add(e.getKey(), e.getValue());
            }
            arr.add(o);
        }
        root.add("notebooks", arr);
        JsonObject bind = new JsonObject();
        for (Map.Entry<Long, String> e : n.bindings().entrySet()) {
            bind.addProperty(hex(e.getKey()), e.getValue());
        }
        root.add("bindings", bind);
        for (Map.Entry<String, JsonElement> e : n.extra().entrySet()) {
            root.add(e.getKey(), e.getValue());
        }
        return GSON.toJson(root) + "\n";
    }

    /**
     * 一時ファイル経由で書く（MemoFile.writeAtomic）。読めない・知らない形式の既存ファイルがあれば書かずに
     * IOException を投げる（上書きしない）。書き込み用スレッドから呼ぶ。
     */
    public static void write(Path file, Notebooks n) throws IOException {
        ReadResult existing = read(file);
        if (!existing.status().canWrite()) {
            throw new IOException("読めない・知らない形式の notebooks.json は上書きしない: " + existing.status());
        }
        MemoFile.writeAtomic(file, toJson(n));
    }
}
