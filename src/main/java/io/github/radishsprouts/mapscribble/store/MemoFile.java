package io.github.radishsprouts.mapscribble.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** メモの JSON ファイルの読み書き（04章）。状態を持たない。 */
public final class MemoFile {
    /** 今書いている形式の版。 */
    public static final int FORMAT = 1;

    private MemoFile() {
    }

    /** 読み込みの結果。MISSING・OK 以外のファイルは、上書きしてはいけない。 */
    public enum Status {
        /** ファイルが無い。空として始めてよい。 */
        MISSING,
        OK,
        /** 知らない format。 */
        UNSUPPORTED,
        /** 壊れた JSON、または読み取りの失敗。 */
        CORRUPT
    }

    public record ReadResult(Status status, List<Memo> memos, String detail) {
        public boolean canWrite() {
            return status == Status.MISSING || status == Status.OK;
        }
    }

    /** ディメンションのファイル名。例: minecraft:overworld → minecraft__overworld.json */
    public static String fileName(String dimension) {
        String ns = "minecraft";
        String path = dimension;
        int colon = dimension.indexOf(':');
        if (colon >= 0) {
            ns = dimension.substring(0, colon);
            path = dimension.substring(colon + 1);
        }
        return sanitize(ns) + "__" + sanitize(path) + ".json";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static Path resolve(Path dataPath, String dimension) {
        return dataPath.resolve(fileName(dimension));
    }

    public static ReadResult read(Path file) {
        if (!Files.exists(file)) {
            return new ReadResult(Status.MISSING, List.of(), "");
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ReadResult(Status.CORRUPT, List.of(), "読み取りに失敗: " + e.getMessage());
        }
        return parse(text);
    }

    public static ReadResult parse(String text) {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return new ReadResult(Status.CORRUPT, List.of(), "最上位がオブジェクトではない");
            }
            JsonObject obj = root.getAsJsonObject();
            JsonElement format = obj.get("format");
            if (format == null || !format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()) {
                return new ReadResult(Status.UNSUPPORTED, List.of(), "format が無い");
            }
            if (format.getAsInt() != FORMAT) {
                return new ReadResult(Status.UNSUPPORTED, List.of(), "format " + format.getAsInt());
            }
            List<Memo> memos = new ArrayList<>();
            for (JsonElement e : obj.getAsJsonArray("memos")) {
                memos.add(memoFromJson(e.getAsJsonObject()));
            }
            return new ReadResult(Status.OK, memos, "");
        } catch (RuntimeException e) {
            return new ReadResult(Status.CORRUPT, List.of(), String.valueOf(e.getMessage()));
        }
    }

    private static Memo memoFromJson(JsonObject o) {
        List<MapPoint> points = new ArrayList<>();
        for (JsonElement p : o.getAsJsonArray("points")) {
            JsonArray xz = p.getAsJsonArray();
            points.add(new MapPoint(xz.get(0).getAsDouble(), xz.get(1).getAsDouble()));
        }
        String color = o.get("color").getAsString();
        if (!color.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("color が不正: " + color);
        }
        return new Memo(
                UUID.fromString(o.get("id").getAsString()),
                MemoKind.valueOf(o.get("kind").getAsString()),
                points,
                Integer.parseInt(color.substring(1), 16),
                o.get("width").getAsInt(),
                o.get("fillOpacity").getAsFloat(),
                o.has("text") ? o.get("text").getAsString() : "",
                o.get("textScale").getAsFloat(),
                o.get("onMinimap").getAsBoolean(),
                Instant.parse(o.get("created").getAsString()),
                Instant.parse(o.get("updated").getAsString()));
    }

    public static String toJson(List<Memo> memos) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonArray arr = new JsonArray();
        for (Memo m : memos) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id().toString());
            o.addProperty("kind", m.kind().name());
            JsonArray pts = new JsonArray();
            for (MapPoint p : m.points()) {
                JsonArray xz = new JsonArray();
                xz.add(round2(p.x()));
                xz.add(round2(p.z()));
                pts.add(xz);
            }
            o.add("points", pts);
            o.addProperty("color", String.format("#%06X", m.color() & 0xFFFFFF));
            o.addProperty("width", m.width());
            o.addProperty("fillOpacity", m.fillOpacity());
            o.addProperty("text", m.text());
            o.addProperty("textScale", m.textScale());
            o.addProperty("onMinimap", m.onMinimap());
            o.addProperty("created", m.created().toString());
            o.addProperty("updated", m.updated().toString());
            arr.add(o);
        }
        root.add("memos", arr);
        return root.toString();
    }

    /** 小数第2位で丸める（ファイルを大きくしないため）。 */
    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 一時ファイルに書いてから置き換える。書き込み中に落ちても元のファイルが残る。 */
    public static void writeAtomic(Path file, String json) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
