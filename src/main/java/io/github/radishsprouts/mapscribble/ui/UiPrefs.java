package io.github.radishsprouts.mapscribble.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.store.MemoFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 最後に使った値（ツール・色・太さ・塗り・文字の大きさ・ミニマップ）を、ゲームを再起動しても覚えておく
 * （画面設計書 3.2）。設定フォルダの小さな JSON 1 つ。読み書きは起動時と、値を決めた時だけで、毎フレームは触らない。
 */
public final class UiPrefs {
    public static final String FILE_NAME = "mapscribble-ui.json";

    private UiPrefs() {
    }

    /** 状態を JSON にする。 */
    public static String toJson(DrawingState state) {
        JsonObject o = new JsonObject();
        o.addProperty("tool", state.tool().name());
        o.addProperty("color", state.color());
        o.addProperty("width", state.width());
        o.addProperty("fillOpacity", state.fillOpacity());
        o.addProperty("textScale", state.textScale());
        o.addProperty("onMinimap", state.onMinimap());
        return o.toString();
    }

    /** JSON の値を状態へ入れる。読めない項目・不正な値は既定のまま。壊れた JSON 全体は何も変えない。 */
    public static void apply(String json, DrawingState state) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        try {
            if (o.has("tool")) {
                state.setTool(Tool.valueOf(o.get("tool").getAsString()));
            }
        } catch (RuntimeException ignored) {
            // 知らないツール名は無視
        }
        if (isNumber(o, "color")) {
            state.setColor(o.get("color").getAsInt());
        }
        if (isNumber(o, "width")) {
            state.setWidth(o.get("width").getAsInt());
        }
        if (isNumber(o, "fillOpacity")) {
            state.setFillOpacity(FillLevel.nearest(o.get("fillOpacity").getAsFloat()).opacity());
        }
        if (isNumber(o, "textScale")) {
            state.setTextScale(TextSize.nearest(o.get("textScale").getAsFloat()).scale());
        }
        if (o.has("onMinimap") && o.get("onMinimap").isJsonPrimitive()) {
            state.setOnMinimap(o.get("onMinimap").getAsBoolean());
        }
    }

    private static boolean isNumber(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber();
    }

    /** ファイルから読んで状態へ入れる。無い・読めない時は何もしない。 */
    public static void load(Path file, DrawingState state) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            apply(Files.readString(file, StandardCharsets.UTF_8), state);
        } catch (IOException | RuntimeException e) {
            // 設定が読めなくても既定で動く
        }
    }

    /** 状態をファイルへ書く。書けなくても手書きは続けられるので、例外は呼び出し側へ出さず false を返す。 */
    public static boolean save(Path file, DrawingState state) {
        try {
            MemoFile.writeAtomic(file, toJson(state));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
