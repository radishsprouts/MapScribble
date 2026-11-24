package io.github.radishsprouts.mapscribble.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.ui.BandMenu;
import io.github.radishsprouts.mapscribble.ui.NotebookLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Font;

/**
 * 固定の幅に入れている文言が、10 言語すべてで上限に収まるかを、Minecraft の {@link Font} で測って確かめる
 * （設計書 3.6 の補足の幅・3.7 のボタンと行の幅。幅の決まりの根拠は {@link BandMenu#noteRoom}・
 * {@link NotebookLayout#buttonTextRoom} と、保存先の画面の中身の幅 {@link NotebookLayout#CONTENT_WIDTH}）。
 *
 * <p>言語を切り替えずに、{@code assets/mapscribble/lang/*.json} をそのまま読んで測る（文字の幅は言語によらず同じフォントで決まる）。
 * 上限を超えた文言は「言語・キー・幅・上限」の一覧にして、1 件でもあれば AssertionError で落とす。
 * 経過は標準出力へ「[MS-LANG] 」を付けて出す。測るのは次の4種で、名前（メモ帳の名前）の入る場所は固定の部分だけを測る
 * （名前は「…」で切り、ホバーで全文を出す）。
 * <ul>
 *   <li>メニューの補足の固定の文言（シングルプレイ・分けていない時）が、{@code 150 − 10 − 自分の言語のラベルの幅 − 8} 以内</li>
 *   <li>保存先の画面のボタン（移す2つは件数に「999+」を入れたもの・件数なしのもの・元に戻す、名前を変える、決定、新しいメモ帳）が、
 *       ボタンの幅 − 8 以内</li>
 *   <li>保存先の画面の1行の文言（切替の説明・分ける前のメモの行・移した知らせ・失敗の文・読み込み中や空の表示）が、中身の幅 286 以内</li>
 *   <li>「メモ帳」のラベルと右の灰色のヒントが、間を 8 空けて 286 に収まる</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
final class LangWidthClientGameTest {
    private static final String[] LANGS = {"ja_jp", "en_us", "de_de", "es_es", "fr_fr", "ko_kr", "pt_br", "ru_ru",
            "zh_cn", "zh_tw"};
    /** 「メモ帳」のラベルと右のヒントの間に最低限空ける幅（メニューのラベルと補足の間と同じ 8）。 */
    private static final int LABEL_HINT_GAP = 8;
    private static final String CAP = "999+";
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    private LangWidthClientGameTest() {
    }

    private static void log(String message) {
        System.out.println("[MS-LANG] " + message);
    }

    /** 全言語を測り、上限を超えた文言があれば落ちる。 */
    static void run(ClientGameTestContext ctx) {
        List<String> over = ctx.computeOnClient(client -> measureAll(client.font));
        if (!over.isEmpty()) {
            for (String line : over) {
                log("上限超え: " + line);
            }
            throw new AssertionError("[MS-LANG] 固定の幅を超えた文言が " + over.size() + " 件ある:\n  "
                    + String.join("\n  ", over));
        }
        log("10 言語すべてで、メニューの補足・保存先の画面のボタンと行が上限に収まった");
    }

    /** 全言語を測る。上限を超えた文言（と、読めなかったキー）の一覧を返す。 */
    static List<String> measureAll(Font font) {
        List<String> over = new ArrayList<>();
        for (String lang : LANGS) {
            Map<String, String> texts;
            try {
                texts = load(lang);
            } catch (IOException | RuntimeException e) {
                over.add(lang + ": 言語ファイルを読めない: " + e);
                continue;
            }
            measureLang(font, lang, texts, over);
        }
        return over;
    }

    private static void measureLang(Font font, String lang, Map<String, String> t, List<String> over) {
        Measure m = new Measure(font, lang, t, over);

        // メニューの補足の固定の文言（メモ帳の名前は「…」で切ってよいので測らない）
        String menuLabel = m.text("mapscribble.menu.notebooks");
        if (menuLabel != null) {
            int room = BandMenu.noteRoom(font.width(menuLabel));
            m.fit("mapscribble.menu.notebooks.single", room, "メニューの補足（150−10−ラベル" + font.width(menuLabel) + "−8）");
            m.fit("mapscribble.menu.notebooks.shared", room, "メニューの補足（150−10−ラベル" + font.width(menuLabel) + "−8）");
        }

        // 保存先の画面のボタン（幅 − 8）
        NotebookLayout layout = new NotebookLayout(240);
        int moveRoom = NotebookLayout.buttonTextRoom(layout.moveWidth());
        m.fit("mapscribble.notebook.move_all", moveRoom, "移すボタン", CAP);
        m.fit("mapscribble.notebook.move_all.plain", moveRoom, "移すボタン");
        m.fit("mapscribble.notebook.move_visible", moveRoom, "移すボタン", CAP);
        m.fit("mapscribble.notebook.move_visible.plain", moveRoom, "移すボタン");
        m.fit("mapscribble.notebook.undo", moveRoom, "元に戻すボタン");
        int renameRoom = NotebookLayout.buttonTextRoom(layout.renameWidth());
        m.fit("mapscribble.notebook.rename", renameRoom, "名前を変えるボタン");
        m.fit("mapscribble.notebook.rename.confirm", renameRoom, "決定のボタン");
        m.fit("mapscribble.notebook.new", NotebookLayout.buttonTextRoom(layout.newWidth()), "新しいメモ帳のボタン");

        // 保存先の画面の1行の文言（中身の幅 286。名前の入る所は名前を空にして測る）
        int line = NotebookLayout.CONTENT_WIDTH;
        String[] oneLine = {
                "mapscribble.notebook.split.desc.auto", "mapscribble.notebook.split.desc.on",
                "mapscribble.notebook.split.desc.off", "mapscribble.notebook.split.desc.no_id",
                "mapscribble.notebook.load_failed", "mapscribble.notebook.load_failed.list",
                "mapscribble.notebook.loading", "mapscribble.notebook.empty", "mapscribble.notebook.empty.hint",
                "mapscribble.notebook.presplit.none", "mapscribble.notebook.moving",
                "mapscribble.notebook.move_failed"};
        for (String key : oneLine) {
            m.fit(key, line, "保存先の画面の1行");
        }
        String[] withCount = {"mapscribble.notebook.presplit", "mapscribble.notebook.presplit.unsplit",
                "mapscribble.notebook.presplit.writing"};
        for (String key : withCount) {
            m.fit(key, line, "保存先の画面の1行", CAP);
        }
        m.fit("mapscribble.notebook.presplit.dims", line, "保存先の画面の1行", CAP, CAP);
        m.fit("mapscribble.notebook.moved", line, "保存先の画面の1行（名前を除く）", CAP, "");
        for (String key : t.keySet()) {
            if (key.startsWith("mapscribble.notebook.error.")) {
                m.fit(key, line, "名前を変える失敗の文（名前を除く）", "");
            }
        }

        // 「メモ帳」のラベルと右のヒントが重ならない
        String list = m.text("mapscribble.notebook.list");
        if (list != null) {
            int room = line - font.width(list) - LABEL_HINT_GAP;
            m.fit("mapscribble.notebook.list.hint", room, "ラベルの右のヒント（286−ラベル" + font.width(list) + "−8）");
            m.fit("mapscribble.notebook.list.hint.off", room, "ラベルの右のヒント（286−ラベル" + font.width(list) + "−8）");
        }

        // 一覧の行の名前の列。件数が長い言語で、名前が1文字も出なくならない（ここは測った値を出すだけで、上限は決めない）
        String countText = m.text("mapscribble.notebook.count");
        if (countText != null) {
            int dateWidth = font.width("2026-11-01");
            int countWidth = font.width(format(countText, CAP));
            int nameMax = NotebookLayout.nameMaxWidth(dateWidth, countWidth);
            log(lang + ": 名前の列の最大幅 " + nameMax + "（件数 \"" + format(countText, CAP) + "\" 幅 " + countWidth
                    + "、名前を変える入力欄の幅 " + NotebookLayout.renameEditWidth(dateWidth, countWidth) + "）");
            if (nameMax <= 0) {
                over.add(lang + " mapscribble.notebook.count: 件数が長く、一覧の名前の列が 0 になる（幅 " + countWidth + "）");
            }
        }
    }

    /** 1 言語分の測りの道具。キーが無ければ失敗として一覧に足す。 */
    private static final class Measure {
        private final Font font;
        private final String lang;
        private final Map<String, String> texts;
        private final List<String> over;

        Measure(Font font, String lang, Map<String, String> texts, List<String> over) {
            this.font = font;
            this.lang = lang;
            this.texts = texts;
            this.over = over;
        }

        String text(String key) {
            String value = texts.get(key);
            if (value == null) {
                over.add(lang + " " + key + ": 言語ファイルにキーが無い");
            }
            return value;
        }

        /** キーの文言（{@code %s} に args を入れたもの）の幅が limit を超えたら一覧に足す。 */
        void fit(String key, int limit, String where, String... args) {
            String value = text(key);
            if (value == null) {
                return;
            }
            String shown = format(value, args);
            int width = font.width(shown);
            if (width > limit) {
                over.add(lang + " " + key + ": 幅 " + width + " > 上限 " + limit + "（" + where + "）「" + shown + "」");
            }
        }
    }

    /** {@code %s}・{@code %1$s}・{@code %%} を差し込む（足りない引数は空にする）。 */
    static String format(String template, String... args) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        int next = 0;
        while (matcher.find()) {
            String replacement;
            if (matcher.group().equals("%%")) {
                replacement = "%";
            } else {
                int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
                replacement = index >= 0 && index < args.length ? args[index] : "";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Map<String, String> load(String lang) throws IOException {
        String path = "/assets/mapscribble/lang/" + lang + ".json";
        try (InputStream in = MapScribble.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("リソースが無い: " + path);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    map.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return map;
        }
    }
}
