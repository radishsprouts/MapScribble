package io.github.radishsprouts.mapscribble.test;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.geom.Shapes;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import io.github.radishsprouts.mapscribble.store.MemoStore;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.BandLayout;
import io.github.radishsprouts.mapscribble.ui.FillLevel;
import io.github.radishsprouts.mapscribble.ui.NoteColor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * 見た目の確認用に、GUI 倍率 × 画面の大きさ × 言語の組み合わせでスクリーンショットを撮る（判定はしない。
 * 画像の良し悪しは Dev-QA と Dev-UI-Designer が見る）。画像は run/screenshots/ へ
 * 「&lt;通し番号&gt;_&lt;言語&gt;_gui&lt;倍率&gt;_&lt;幅&gt;x&lt;高さ&gt;_&lt;連番&gt;_&lt;内容&gt;.png」の名前で保存される
 * （先頭の通し番号は Fabric の gametest が付ける）。
 * 経過は標準出力へ「[MS-SHOT] 」を付けて出す。撮影の途中で何かが失敗しても、その組をログに残して次へ進む
 * （撮れなかった画像を報告できるようにするため。落とすのは「撮影を始められない」時だけ）。
 *
 * <p>撮るもの: 01 全画面マップ（手書きオフ）、02 帯（ツールごとの説明の行）、03 文字入力の画面（日本語を数行）、
 * 04 編集の画面、05 メモの上のカーソルの表示。GUI 倍率 2・1920x1080 では加えて、残りのツールの帯、
 * 色・塗り・太さ・文字の大きさの見比べ（06）を撮る。最後に拡大した地図の斜めの線と円（07）を撮る。
 * 640x480 は GUI 倍率 1・2 を撮る。GUI 倍率 2 の 1920x1080 と 640x480（日本語）では加えて、説明の行の各状態（08）、
 * 初回の既定（09）、文字のメモの空欄と入力済み（10）、線のメモ（11）、ボタンのツールチップ（12）を撮る。
 * 撮る前にマウスを無害な位置へ動かす（ホバー・ツールチップの撮影を除く）。
 * GUI 倍率が画面の大きさで頭打ちになる（854x480 は最大 2 など）組は、実際の倍率が違うので撮らない（ログに出す）。
 *
 * <p>他の試験が残したメモが写り込まないよう、撮影の間は既存のメモをいったん外し、終わるときに戻す。
 * この試験は確定後の処理（文字入力の画面）を使うので、Drawing 試験（確定後の処理を外す）より前に置く。
 * 変えた GUI 倍率・言語・画面の大きさ・手書きの状態・足したメモは、終わるときに元へ戻す。
 */
@SuppressWarnings("UnstableApiUsage")
public class ScreenshotClientGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 600;
    private static final int[][] SIZES = {{640, 480}, {854, 480}, {1280, 720}, {1920, 1080}};
    private static final int[] SCALES = {1, 2, 3, 4};

    private static int shotCount;
    private static int skipCount;
    private static int failCount;

    /** 撮影の前に控えておく元の状態。 */
    private record Saved(boolean modeOn, Tool tool, int color, int width, boolean visible, float fillOpacity,
                         float textScale, boolean onMinimap, int guiScale, String language, int windowWidth,
                         int windowHeight) {
    }

    private static void log(String message) {
        System.out.println("[MS-SHOT] " + message);
    }

    private static Object openFullscreen() {
        try {
            Class<?> ui = Class.forName("journeymap.client.ui.UIManager");
            Object instance = ui.getField("INSTANCE").get(null);
            Method open = ui.getMethod("getOrOpenFullscreenMap");
            return open.invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String screenName(Minecraft client) {
        //? if >=26.2 {
        /*var screen = client.gui.screen();
        *///?} else {
        var screen = client.screen;
        //?}
        return screen == null ? "null" : screen.getClass().getName();
    }

    private static double center(Object fullscreen, String method) {
        try {
            return (Double) fullscreen.getClass().getMethod(method, boolean.class).invoke(fullscreen, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 1 ブロックの画面上の大きさ（物理ピクセル。UIState.blockSize）。 */
    private static double blockSize(Object fullscreen) {
        try {
            Object uiState = fullscreen.getClass().getMethod("getUiState").invoke(fullscreen);
            Field field = uiState.getClass().getField("blockSize");
            return ((Number) field.get(uiState)).doubleValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Projection projection(ClientGameTestContext context, Object fullscreen) {
        return context.computeOnClient(client -> new Projection(
                center(fullscreen, "getCenterBlockX"), center(fullscreen, "getCenterBlockZ"), blockSize(fullscreen),
                client.getWindow().getWidth(), client.getWindow().getHeight()));
    }

    private static String screenOf(ClientGameTestContext context) {
        return context.computeOnClient(ScreenshotClientGameTest::screenName);
    }

    /** 全画面マップが閉じていたら開き直す（文字入力の画面など、他の画面が出ている時は何もしない）。 */
    private static void ensureFullscreen(ClientGameTestContext context, Object[] holder) {
        String name = screenOf(context);
        if (name.endsWith(".Fullscreen") || !name.equals("null")) {
            return;
        }
        context.getInput().pressKey(InputConstants.KEY_J);
        context.waitTicks(20);
        context.runOnClient(client -> holder[0] = openFullscreen());
        context.waitTicks(5);
    }

    /** 全画面マップを閉じて開き直す（画面の大きさ・倍率・言語を変えた後に、画面を作り直すため）。 */
    private static void reopenFullscreen(ClientGameTestContext context, Object[] holder) {
        context.setScreen(() -> null);
        context.waitTicks(5);
        context.runOnClient(client -> holder[0] = openFullscreen());
        context.waitTicks(25);
        ensureFullscreen(context, holder);
        parkCursor(context);
    }

    /**
     * マウスを無害な位置へ動かす。JourneyMap の下の列（「マップを削除」など）にも、手書きの帯にも、右列のボタンにも
     * 載らない位置（全画面マップは右寄りの上、文字入力の画面はパネルの外の左端）。撮る前にここへ置かないと、
     * 前の操作で残ったマウスの位置の下にある部品のツールチップが写り込む。
     */
    private static void parkCursor(ClientGameTestContext context) {
        int w = context.computeOnClient(c -> c.getWindow().getWidth());
        int h = context.computeOnClient(c -> c.getWindow().getHeight());
        if (screenOf(context).endsWith(".NoteEditScreen")) {
            context.getInput().setCursorPos(w * 0.01, h * 0.5);
        } else {
            context.getInput().setCursorPos(w * 0.8, h * 0.22);
        }
        context.waitTicks(3);
    }

    private static void snap(ClientGameTestContext context, Object[] holder, String prefix, String suffix) {
        snap(context, holder, prefix, suffix, false);
    }

    /** keepCursor: ホバー・ツールチップの撮影で、今のマウスの位置を保つ（それ以外は撮る前に無害な位置へ動かす）。 */
    private static void snap(ClientGameTestContext context, Object[] holder, String prefix, String suffix,
                             boolean keepCursor) {
        if (!keepCursor) {
            parkCursor(context);
        }
        String name = prefix + "_" + suffix;
        Path saved = context.takeScreenshot(name);
        shotCount++;
        log("撮影 " + saved.getFileName() + "（画面: " + screenOf(context) + "）");
        ensureFullscreen(context, holder);
    }

    private static void setMode(ClientGameTestContext context, DrawingState state, boolean on, Tool tool) {
        context.runOnClient(client -> {
            state.setModeOn(on);
            if (tool != null) {
                state.setTool(tool);
            }
        });
        context.waitTicks(8);
    }

    // ---- メモの置き方（リポジトリへ直接足す。位置はウィンドウ中心からの物理ピクセル） ----

    private static List<MapPoint> points(Projection p, double[][] offsets) {
        List<MapPoint> list = new ArrayList<>();
        for (double[] o : offsets) {
            list.add(new MapPoint(p.toWorldX(p.width() / 2.0 + o[0]), p.toWorldZ(p.height() / 2.0 + o[1])));
        }
        return list;
    }

    private static double[][] circle(double cx, double cz, double radius) {
        double[][] pts = new double[64][];
        for (int i = 0; i < pts.length; i++) {
            double a = 2 * Math.PI * i / pts.length;
            pts[i] = new double[] {cx + radius * Math.cos(a), cz + radius * Math.sin(a)};
        }
        return pts;
    }

    private static double[][] rect(double cx, double cz, double halfW, double halfH) {
        return new double[][] {{cx - halfW, cz - halfH}, {cx + halfW, cz - halfH}, {cx + halfW, cz + halfH},
                {cx - halfW, cz + halfH}};
    }

    private static Memo memo(MemoKind kind, List<MapPoint> pts, int color, int width, float fill, String text,
                             float scale) {
        Instant now = Instant.now();
        return new Memo(UUID.randomUUID(), kind, pts, color, width, fill, text, scale, true, now, now);
    }

    private static void addMemos(ClientGameTestContext context, List<Memo> memos, List<UUID> ids) {
        context.runOnClient(client -> {
            MemoRepository repo = StoreSession.repository();
            for (Memo m : memos) {
                repo.add(m);
                ids.add(m.id());
            }
        });
        context.waitTicks(8);
    }

    private static void removeMemos(ClientGameTestContext context, List<UUID> ids) {
        List<UUID> copy = List.copyOf(ids);
        ids.clear();
        context.runOnClient(client -> {
            MemoRepository repo = StoreSession.repository();
            if (repo != null) {
                for (UUID id : copy) {
                    repo.remove(id);
                }
            }
        });
        context.waitTicks(5);
    }

    private static final double[][] REGION_A = {{60, 30}, {210, 0}, {300, 80}, {250, 170}, {90, 150}};

    /** 基本のメモ3種（囲み＝日本語の文字付き・塗りあり、線、文字だけ）。今の画面の中心を基準に置き直す。 */
    private static List<Memo> basicMemos(Projection p) {
        List<Memo> list = new ArrayList<>();
        list.add(memo(MemoKind.REGION, points(p, REGION_A), NoteColor.RED.rgb(), 2, FillLevel.NORMAL.opacity(),
                "港の倉庫\n資材置き場", 1.5f));
        list.add(memo(MemoKind.LINE, points(p, new double[][] {{-330, 210}, {-180, 140}, {-60, 190}, {40, 230}}),
                NoteColor.BLUE.rgb(), 3, 0f, "", 1.5f));
        list.add(memo(MemoKind.TEXT, points(p, new double[][] {{-200, 60}}), NoteColor.LIME.rgb(), 2, 0f,
                "ここに拠点", 1.5f));
        return list;
    }

    /** 基本メモの囲みの内側の点（画面の物理ピクセル）。クリック・ホバーの位置に使う。 */
    private static double[] regionInside(Projection p) {
        MapPoint c = Shapes.centroid(points(p, REGION_A));
        return new double[] {p.toScreenX(c.x()), p.toScreenY(c.z())};
    }

    // ---- 入力 ----

    /** 手書きの一筆（囲み）を、画面の割合の軌跡で描く。 */
    private static void dragEnclose(ClientGameTestContext context) {
        dragPath(context, new double[][] {{0.35, 0.65}, {0.50, 0.35}, {0.65, 0.65}, {0.50, 0.70}, {0.36, 0.66}});
    }

    /** 手書きの一筆（線）。帯（左上）に載らない、画面の下寄りを通す。 */
    private static void dragLine(ClientGameTestContext context) {
        dragPath(context, new double[][] {{0.30, 0.75}, {0.45, 0.62}, {0.60, 0.72}, {0.75, 0.60}});
    }

    /** 画面の幅・高さに対する割合の軌跡を、左ボタンを押したままなぞる。 */
    private static void dragPath(ClientGameTestContext context, double[][] ratios) {
        int width = context.computeOnClient(client -> client.getWindow().getWidth());
        int height = context.computeOnClient(client -> client.getWindow().getHeight());
        double[][] path = new double[ratios.length][];
        for (int i = 0; i < ratios.length; i++) {
            path[i] = new double[] {width * ratios[i][0], height * ratios[i][1]};
        }
        context.getInput().setCursorPos(path[0][0], path[0][1]);
        context.waitTicks(2);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        for (int i = 1; i < path.length; i++) {
            double fx = path[i - 1][0];
            double fy = path[i - 1][1];
            for (int s = 1; s <= 10; s++) {
                context.getInput().setCursorPos(fx + (path[i][0] - fx) * s / 10, fy + (path[i][1] - fy) * s / 10);
                context.waitTicks(1);
            }
        }
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(15);
    }

    private static void closeEditScreenWithEsc(ClientGameTestContext context, Object[] holder) {
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(10);
        ensureFullscreen(context, holder);
    }

    // ---- 組み合わせの撮影 ----

    private static void shootCombination(ClientGameTestContext context, Object[] holder, DrawingState state,
                                         List<UUID> basicIds, String lang, int w, int h, int scale, boolean full,
                                         boolean extras) {
        // 細部の撮影（説明の行の各状態・文字入力の画面・ツールチップ・既定）は、GUI 倍率 2 の大きい画面と 640x480 で撮る
        boolean detail = full && scale == 2 && lang.equals("ja") && (w == 1920 || w == 640);
        String prefix = lang + "_gui" + scale + "_" + w + "x" + h;
        reopenFullscreen(context, holder);
        removeMemos(context, basicIds);
        Projection p = projection(context, holder[0]);
        log(prefix + ": blockSize=" + p.scale() + " 中心=(" + p.centerX() + "," + p.centerZ() + ")");
        addMemos(context, basicMemos(p), basicIds);

        // 01 全画面マップ（手書きはオフ）
        setMode(context, state, false, Tool.ENCLOSE);
        snap(context, holder, prefix, "01_map_off");

        // 02 手書きモードの帯と説明の行（ツールで説明の行が変わる）
        setMode(context, state, true, Tool.ENCLOSE);
        snap(context, holder, prefix, "02_band_enclose");
        setMode(context, state, true, Tool.ERASER);
        snap(context, holder, prefix, "02_band_eraser");
        if (extras) {
            for (Tool t : new Tool[] {Tool.LINE, Tool.TEXT, Tool.EDIT}) {
                setMode(context, state, true, t);
                snap(context, holder, prefix, "02_band_" + t.name().toLowerCase(java.util.Locale.ROOT));
            }
        }

        // 03 囲みを描いた直後の文字入力の画面（日本語を数行）
        setMode(context, state, true, Tool.ENCLOSE);
        dragEnclose(context);
        String screen = screenOf(context);
        log(prefix + ": 囲んだ後の画面 = " + screen);
        context.getInput().typeChars("渡り廊下の計画");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.getInput().typeChars("二階に資材置き場を作る");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.getInput().typeChars("水路は東側から引く");
        context.waitTicks(5);
        snap(context, holder, prefix, "03_note_input");
        closeEditScreenWithEsc(context, holder);

        if (!full) {
            setMode(context, state, false, null);
            return;
        }

        // 04 編集の画面（編集ツールで既存の囲みをクリック）
        double[] inside = regionInside(projection(context, holder[0]));
        setMode(context, state, true, Tool.EDIT);
        context.getInput().setCursorPos(inside[0], inside[1]);
        context.waitTicks(5);
        if (extras) {
            snap(context, holder, prefix, "05_hover_edit", true);
        }
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(10);
        log(prefix + ": 編集ツールのクリック後の画面 = " + screenOf(context));
        snap(context, holder, prefix, "04_note_edit");
        closeEditScreenWithEsc(context, holder);

        // 05 メモの上にカーソルを置いた時の表示（消しゴム）
        setMode(context, state, true, Tool.ERASER);
        context.getInput().setCursorPos(inside[0], inside[1]);
        context.waitTicks(5);
        snap(context, holder, prefix, "05_hover_eraser", true);

        if (extras) {
            shootCompare(context, holder, state, prefix);
        }
        if (detail) {
            shootDetails(context, holder, state, basicIds, prefix);
        }
        setMode(context, state, false, null);
    }

    /**
     * 06 色（10色）・塗りの濃さ・線の太さ・文字の大きさの見比べ。
     * 下端には JourneyMap のツールバー・座標の表示があるので、画面中心より上（帯の右）に並べる。
     */
    private static void shootCompare(ClientGameTestContext context, Object[] holder, DrawingState state,
                                     String prefix) {
        List<UUID> ids = new ArrayList<>();
        try {
            Projection p = projection(context, holder[0]);
            List<Memo> memos = new ArrayList<>();
            // 色: 10色の小さな囲み（塗りは濃いめ）
            for (int i = 0; i < NoteColor.list().size(); i++) {
                memos.add(memo(MemoKind.REGION, points(p, rect(-315 + 70 * i, -380, 25, 25)),
                        NoteColor.list().get(i).rgb(), 2, FillLevel.STRONG.opacity(), "", 1.0f));
            }
            // 塗りの濃さ: なし・薄い・標準・濃い（同じ色、文字付き）
            String[] fillNames = {"なし", "薄い", "標準", "濃い"};
            for (int j = 0; j < FillLevel.values().length; j++) {
                memos.add(memo(MemoKind.REGION, points(p, rect(-315 + 90 * j, -290, 40, 30)),
                        NoteColor.AQUA.rgb(), 2, FillLevel.values()[j].opacity(), fillNames[j], 1.0f));
            }
            // 線の太さ: 細・中・太
            for (int k = 0; k < 3; k++) {
                memos.add(memo(MemoKind.LINE, points(p, new double[][] {{-315, -210 + 25 * k}, {-95, -210 + 25 * k}}),
                        NoteColor.BLUE.rgb(), k + 1, 0f, "", 1.0f));
            }
            // 文字の大きさ: 小・中・大
            memos.add(memo(MemoKind.TEXT, points(p, new double[][] {{20, -170}}), NoteColor.WHITE.rgb(), 2, 0f,
                    "小さい文字", 1.0f));
            memos.add(memo(MemoKind.TEXT, points(p, new double[][] {{140, -170}}), NoteColor.WHITE.rgb(), 2, 0f,
                    "中くらい", 1.5f));
            memos.add(memo(MemoKind.TEXT, points(p, new double[][] {{300, -170}}), NoteColor.WHITE.rgb(), 2, 0f,
                    "大きい文字", 2.0f));
            addMemos(context, memos, ids);
            setMode(context, state, false, null);
            context.getInput().setCursorPos(
                    context.computeOnClient(c -> c.getWindow().getWidth()) * 0.9,
                    context.computeOnClient(c -> c.getWindow().getHeight()) * 0.1);
            context.waitTicks(5);
            snap(context, holder, prefix, "06_compare_styles");
        } finally {
            removeMemos(context, ids);
        }
    }

    // ---- 細部の撮影（08〜15） ----

    /** 今の画面（全画面マップ）の右列にある MapScribble の切替ボタン。無ければ null。 */
    private static AbstractWidget findToggle(Minecraft client) {
        //? if >=26.2 {
        /*Screen screen = client.gui.screen();
        *///?} else {
        Screen screen = client.screen;
        //?}
        if (screen == null) {
            return null;
        }
        try {
            Field renderables = Screen.class.getDeclaredField("renderables");
            renderables.setAccessible(true);
            for (Object part : (List<?>) renderables.get(screen)) {
                for (Class<?> c = part.getClass(); c != null; c = c.getSuperclass()) {
                    if (c.getName().equals("journeymap.client.ui.theme.ThemeButton")) {
                        Field icon = c.getDeclaredField("icon");
                        icon.setAccessible(true);
                        Object id = icon.get(part);
                        if (id != null && id.toString().contains("mapscribble") && id.toString().contains("toggle")) {
                            return (AbstractWidget) part;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }

    private static Field storeField(String name) {
        try {
            Field f = MemoStore.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 帯の説明の一時のお知らせ（4 秒）が消えるのを待つ（お知らせが「0件」「隠している」より優先されるため）。 */
    private static void waitNoticeExpire(ClientGameTestContext context) {
        context.waitTicks(5);
        try {
            Thread.sleep(4500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        context.waitTicks(5);
    }

    /** マウスを GUI 座標 (gx, gy) の位置へ動かして、ツールチップが出るのを待つ。 */
    private static void hoverGui(ClientGameTestContext context, double gx, double gy) {
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(gx * scale, gy * scale);
        context.waitTicks(8);
    }

    /**
     * 08 説明の行の各状態、09 初回の既定、10 文字のメモ（空欄・入力済み）、11 線のメモ、12 ボタンのツールチップ。
     * 状態は試験用に差し込む（メモの窓口の「読めなかった」「使えない」「保存失敗」の通知先と使える印を直接動かし、
     * 終わるときに戻す）。「読み込み中」は実際には起きない状態（読み込みは同期）なので、同じ文言を一時のお知らせとして出す。
     */
    private static void shootDetails(ClientGameTestContext context, Object[] holder, DrawingState state,
                                     List<UUID> basicIds, String prefix) {
        // 08 説明の行の各状態（帯の説明の行。ツールは囲み）
        setMode(context, state, true, Tool.ENCLOSE);
        removeMemos(context, basicIds);
        waitNoticeExpire(context);
        snap(context, holder, prefix, "08_hint_empty");

        context.runOnClient(client -> state.notice("mapscribble.msg.loading"));
        context.waitTicks(2);
        snap(context, holder, prefix, "08_hint_loading");

        MemoStore store = (MemoStore) StoreSession.repository();
        Field writable = storeField("writable");
        try {
            context.runOnClient(client -> {
                try {
                    writable.setBoolean(store, false);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            });
            waitNoticeExpire(context);
            snap(context, holder, prefix, "08_hint_not_ready");

            @SuppressWarnings("unchecked")
            Consumer<String> problem = (Consumer<String>) storeField("problemHandler").get(store);
            context.runOnClient(client -> problem.accept("test"));
            context.waitTicks(3);
            snap(context, holder, prefix, "08_hint_load_failed");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } finally {
            context.runOnClient(client -> {
                try {
                    writable.setBoolean(store, true);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            });
            context.waitTicks(3);
        }

        try {
            @SuppressWarnings("unchecked")
            Consumer<Boolean> saveState = (Consumer<Boolean>) storeField("saveStateHandler").get(store);
            context.runOnClient(client -> saveState.accept(true));
            context.waitTicks(4);
            snap(context, holder, prefix, "08_hint_save_failed");
            context.runOnClient(client -> saveState.accept(false));
            context.waitTicks(4);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        // 初回の既定（囲み・赤・中）。他の試験が変えた値を、起動直後の既定へ戻してから撮る
        context.runOnClient(client -> {
            state.setColor(DrawingState.DEFAULT_COLOR);
            state.setWidth(2);
            state.setFillOpacity(FillLevel.NORMAL.opacity());
            state.setTextScale(1.5f);
            state.setOnMinimap(true);
            state.setVisible(true);
        });
        setMode(context, state, true, Tool.ENCLOSE);
        waitNoticeExpire(context);
        snap(context, holder, prefix, "09_default_band");
        dragEnclose(context);
        log(prefix + ": 既定の囲みの後の画面 = " + screenOf(context));
        snap(context, holder, prefix, "09_default_enclose_input");
        closeEditScreenWithEsc(context, holder);

        // 10 文字のメモ（クリックした場所に置く）。空欄 → 入力済み
        setMode(context, state, true, Tool.TEXT);
        clickMap(context, 0.55, 0.55);
        log(prefix + ": 文字ツールのクリック後の画面 = " + screenOf(context));
        snap(context, holder, prefix, "10_text_empty");
        context.getInput().typeChars("ここに拠点");
        context.waitTicks(5);
        snap(context, holder, prefix, "10_text_typed");
        closeEditScreenWithEsc(context, holder);

        // 11 線のメモ
        setMode(context, state, true, Tool.LINE);
        dragLine(context);
        log(prefix + ": 線の後の画面 = " + screenOf(context));
        context.getInput().typeChars("水路の予定");
        context.waitTicks(5);
        snap(context, holder, prefix, "11_line_input");
        closeEditScreenWithEsc(context, holder);

        // 12 ツールチップ（帯の道具ボタン 5 つ、右列の切替ボタン）
        setMode(context, state, true, Tool.ENCLOSE);
        for (BandLayout.Cell cell : BandLayout.cells()) {
            if (cell.kind() != BandLayout.Kind.TOOL) {
                continue;
            }
            hoverGui(context, cell.x() + cell.w() / 2.0, cell.y() + cell.h() / 2.0);
            snap(context, holder, prefix, "12_tooltip_tool_" + Tool.values()[cell.index()].name().toLowerCase(
                    java.util.Locale.ROOT), true);
        }
        hoverToggle(context, holder, prefix, "12_tooltip_toggle_on");
        setMode(context, state, false, null);
        hoverToggle(context, holder, prefix, "12_tooltip_toggle_off");
        parkCursor(context);
    }

    private static void hoverToggle(ClientGameTestContext context, Object[] holder, String prefix, String suffix) {
        double[] centre = context.computeOnClient(client -> {
            AbstractWidget toggle = findToggle(client);
            return toggle == null ? null
                    : new double[] {toggle.getX() + toggle.getWidth() / 2.0, toggle.getY() + toggle.getHeight() / 2.0};
        });
        if (centre == null) {
            log(prefix + ": 右列の切替ボタンが見つからないため " + suffix + " は撮らない");
            return;
        }
        hoverGui(context, centre[0], centre[1]);
        snap(context, holder, prefix, suffix, true);
    }

    /** 画面の幅・高さに対する割合の位置を左クリックする。 */
    private static void clickMap(ClientGameTestContext context, double rx, double ry) {
        int width = context.computeOnClient(client -> client.getWindow().getWidth());
        int height = context.computeOnClient(client -> client.getWindow().getHeight());
        context.getInput().setCursorPos(width * rx, height * ry);
        context.waitTicks(3);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(10);
        if (!screenOf(context).endsWith(".NoteEditScreen")) {
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
        }
    }

    /** 07 拡大した地図での斜めの線と、円に近い囲み（輪郭が階段状でないかを見る）。 */
    private static void shootZoom(ClientGameTestContext context, Object[] holder, DrawingState state, String lang,
                                  int w, int h, int scale) {
        String prefix = lang + "_gui" + scale + "_" + w + "x" + h;
        List<UUID> ids = new ArrayList<>();
        int zoomSteps = 4;
        try {
            reopenFullscreen(context, holder);
            setMode(context, state, false, Tool.ENCLOSE);
            double before = blockSize(holder[0]);
            context.getInput().setCursorPos(w * 0.5, h * 0.5);
            context.waitTicks(3);
            for (int i = 0; i < zoomSteps; i++) {
                context.getInput().scroll(1);
                context.waitTicks(4);
            }
            context.waitTicks(10);
            ensureFullscreen(context, holder);
            Projection p = projection(context, holder[0]);
            log(prefix + ": 拡大 blockSize " + before + " -> " + p.scale());
            List<Memo> memos = new ArrayList<>();
            memos.add(memo(MemoKind.REGION, points(p, circle(420, 20, 240)), NoteColor.BLUE.rgb(), 2,
                    FillLevel.NORMAL.opacity(), "", 1.5f));
            memos.add(memo(MemoKind.REGION, points(p, circle(-150, 330, 160)), NoteColor.RED.rgb(), 1, 0f, "", 1.5f));
            memos.add(memo(MemoKind.LINE, points(p, new double[][] {{-700, -250}, {100, 450}}), NoteColor.LIME.rgb(),
                    1, 0f, "", 1.5f));
            memos.add(memo(MemoKind.LINE, points(p, new double[][] {{-560, -230}, {240, 470}}),
                    NoteColor.YELLOW.rgb(), 3, 0f, "", 1.5f));
            addMemos(context, memos, ids);
            context.getInput().setCursorPos(w * 0.9, h * 0.08);
            context.waitTicks(5);
            snap(context, holder, prefix, "07_zoom_diagonal_circle");
        } finally {
            removeMemos(context, ids);
            // 拡大を戻す（以降の試験へ持ち越さない）
            context.getInput().setCursorPos(w * 0.5, h * 0.5);
            context.waitTicks(3);
            for (int i = 0; i < zoomSteps; i++) {
                context.getInput().scroll(-1);
                context.waitTicks(4);
            }
            context.waitTicks(5);
        }
    }

    // ---- 画面の大きさ・倍率・言語 ----

    /** 画面の大きさと GUI 倍率を変えて、実際の倍率を返す（頭打ちで要求より小さくなることがある）。 */
    private static int applyDisplay(ClientGameTestContext context, int w, int h, int scale) {
        context.getInput().resizeWindow(w, h);
        context.waitTicks(10);
        context.runOnClient(client -> {
            client.options.guiScale().set(scale);
            client.resizeGui();
        });
        context.waitTicks(10);
        return context.computeOnClient(client -> client.getWindow().getGuiScale());
    }

    private static void switchLanguage(ClientGameTestContext context, String code) {
        CompletableFuture<?>[] future = new CompletableFuture<?>[1];
        context.runOnClient(client -> {
            client.getLanguageManager().setSelected(code);
            client.options.languageCode = code;
            future[0] = client.reloadResourcePacks();
        });
        context.waitFor(client -> future[0] != null && future[0].isDone(), 1200);
        context.waitTicks(30);
        log("言語を切り替えた: " + code);
    }

    /** 単独で動かす入口（登録していない）。通常は AllClientGameTest が1つのワールドで続けて run を呼ぶ。 */
    @Override
    public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext singleplayer = TestWorlds.open(context);
        try {
            run(context);
        } finally {
            TestWorlds.close(context, singleplayer);
        }
    }

    /** ワールドが開いて描画を待ち終わった状態から始める。 */
    public static void run(ClientGameTestContext context) {
        shotCount = 0;
        skipCount = 0;
        failCount = 0;
        DrawingState state = ScribbleSession.state();
        Object[] holder = new Object[1];
        context.runOnClient(client -> holder[0] = openFullscreen());
        context.waitTicks(30);
        ensureFullscreen(context, holder);
        try {
            context.waitFor(client -> StoreSession.repository().isAvailable(), TIMEOUT_TICKS);
        } catch (Throwable t) {
            log("メモの窓口が使えないため撮影しない: " + t);
            return;
        }
        if (state == null || ScribbleSession.controller() == null) {
            log("手書きの状態が取り付いていないため撮影しない");
            return;
        }

        Saved saved = context.computeOnClient(client -> new Saved(state.isModeOn(), state.tool(), state.color(),
                state.width(), state.isVisible(), state.fillOpacity(), state.textScale(), state.onMinimap(),
                client.options.guiScale().get(), client.options.languageCode, client.getWindow().getWidth(),
                client.getWindow().getHeight()));
        log("元の状態: guiScale=" + saved.guiScale() + " 言語=" + saved.language() + " 画面="
                + saved.windowWidth() + "x" + saved.windowHeight());
        List<UUID> basicIds = new ArrayList<>();

        // 他の試験が残したメモが写り込まないよう、いったん外す（終わるときに戻す）
        List<Memo> existing = context.computeOnClient(client -> List.copyOf(StoreSession.repository().memos()));
        context.runOnClient(client -> {
            MemoRepository repo = StoreSession.repository();
            for (Memo m : existing) {
                repo.remove(m.id());
            }
        });
        log("既存のメモ " + existing.size() + " 件をいったん外した");

        try {
            // 日本語: 画面の大きさ × GUI 倍率
            switchLanguage(context, "ja_jp");
            for (int[] size : SIZES) {
                for (int scale : SCALES) {
                    int actual = applyDisplay(context, size[0], size[1], scale);
                    if (actual != scale) {
                        skipCount++;
                        log("ja_jp " + size[0] + "x" + size[1] + " 倍率 " + scale + ": 実際の倍率が " + actual
                                + " に頭打ちのため撮らない");
                        continue;
                    }
                    boolean extras = size[0] == 1920 && scale == 2;
                    runCombination(context, holder, state, basicIds, "ja", size[0], size[1], scale, true, extras);
                }
            }

            // 英語: GUI 倍率 2 で、大きい画面と小さい画面
            switchLanguage(context, "en_us");
            for (int[] size : new int[][] {{1920, 1080}, {854, 480}}) {
                int actual = applyDisplay(context, size[0], size[1], 2);
                if (actual != 2) {
                    skipCount++;
                    log("en_us " + size[0] + "x" + size[1] + " 倍率 2: 実際の倍率が " + actual + " のため撮らない");
                    continue;
                }
                runCombination(context, holder, state, basicIds, "en", size[0], size[1], 2, false, false);
            }

            // 日本語へ戻して、拡大した地図の斜めの線と円
            switchLanguage(context, "ja_jp");
            int actual = applyDisplay(context, 1920, 1080, 2);
            if (actual == 2) {
                try {
                    shootZoom(context, holder, state, "ja", 1920, 1080, 2);
                } catch (Throwable t) {
                    failCount++;
                    log("ja 07 拡大の撮影に失敗: " + t);
                }
            } else {
                skipCount++;
                log("ja 07 拡大: 実際の倍率が " + actual + " のため撮らない");
            }
        } finally {
            removeMemos(context, basicIds);
            context.setScreen(() -> null);
            context.waitTicks(5);
            context.runOnClient(client -> {
                MemoRepository repo = StoreSession.repository();
                if (repo != null) {
                    for (Memo m : existing) {
                        repo.add(m);
                    }
                }
                state.setModeOn(saved.modeOn());
                state.setTool(saved.tool());
                state.setColor(saved.color());
                state.setWidth(saved.width());
                state.setVisible(saved.visible());
                state.setFillOpacity(saved.fillOpacity());
                state.setTextScale(saved.textScale());
                state.setOnMinimap(saved.onMinimap());
                client.options.guiScale().set(saved.guiScale());
            });
            if (!saved.language().equals(context.computeOnClient(client -> client.options.languageCode))) {
                switchLanguage(context, saved.language());
            }
            context.getInput().resizeWindow(saved.windowWidth(), saved.windowHeight());
            context.waitTicks(10);
            context.runOnClient(client -> client.resizeGui());
            context.waitTicks(10);
            log("撮影 " + shotCount + " 枚 / 撮らなかった組 " + skipCount + " / 失敗した組 " + failCount);
        }
    }

    /** 1組の撮影。失敗してもログに残して次へ進む。 */
    private static void runCombination(ClientGameTestContext context, Object[] holder, DrawingState state,
                                       List<UUID> basicIds, String lang, int w, int h, int scale, boolean full,
                                       boolean extras) {
        try {
            shootCombination(context, holder, state, basicIds, lang, w, h, scale, full, extras);
        } catch (Throwable t) {
            failCount++;
            log(lang + " " + w + "x" + h + " 倍率 " + scale + " の撮影に失敗: " + t);
            try {
                context.setScreen(() -> null);
                context.waitTicks(10);
            } catch (Throwable ignored) {
                // 後始末の失敗は握りつぶさず、次の組の開き直しで拾う
            }
        }
    }
}
