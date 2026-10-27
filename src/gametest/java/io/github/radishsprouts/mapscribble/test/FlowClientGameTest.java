package io.github.radishsprouts.mapscribble.test;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.BandLayout;
import io.github.radishsprouts.mapscribble.ui.EditLayout;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * 06章 3 の流れを、入力の再現で端から端まで確かめる（作業7）:
 * 手書きで囲む → 文字を入れる → 保存される（メモとファイル）→ 消しゴムで消す → Ctrl+Z で戻る（やり直しも）。
 * 期待と違えば AssertionError で落とす。経過は標準出力へ「[MS-FLOW] 」を付けて出す。
 */
@SuppressWarnings("UnstableApiUsage")
public class FlowClientGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 600;
    private static final String NOTE = "flow note";

    private static void log(String message) {
        System.out.println("[MS-FLOW] " + message);
    }

    private static void check(boolean ok, String message) {
        log((ok ? "OK   " : "FAIL ") + message);
        if (!ok) {
            throw new AssertionError(message);
        }
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

    private static void clickGui(ClientGameTestContext context, double guiX, double guiY, double scale) {
        context.getInput().setCursorPos(guiX * scale, guiY * scale);
        context.waitTicks(3);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
    }

    private static int memoCount(ClientGameTestContext context) {
        return context.computeOnClient(client -> StoreSession.repository().memos().size());
    }

    private static long countWithText(ClientGameTestContext context, String text) {
        return context.computeOnClient(client -> StoreSession.repository().memos().stream()
                .filter(m -> text.equals(m.text())).count());
    }

    /**
     * シングルのワールドのメモの保存先（<ゲームフォルダ>/mapscribble/saves/<ワールド名>/。StoreSession.createKey が決める）
     * にある、オーバーワールドのファイルを全部集める。
     */
    private static List<Path> overworldFiles() {
        Path root = FabricLoader.getInstance().getGameDir().resolve("mapscribble").resolve("saves");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.getFileName().toString().equals("minecraft__overworld.json"))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean anyFileContains(String text) {
        for (Path p : overworldFiles()) {
            try {
                if (Files.readString(p, StandardCharsets.UTF_8).contains(text)) {
                    return true;
                }
            } catch (IOException e) {
                // 書き換えの瞬間に読めないことがある。次の確認で読み直す
            }
        }
        return false;
    }

    /** 書き出し予約（約1秒のまとめ）を待ちながら、ファイルの中身が期待に合うまで最大 5 秒待つ。 */
    private static boolean waitFile(ClientGameTestContext context, String text, boolean expectPresent) {
        for (int i = 0; i < 100; i++) {
            if (anyFileContains(text) == expectPresent) {
                return true;
            }
            context.waitTicks(1);
        }
        return anyFileContains(text) == expectPresent;
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
        Object[] holder = new Object[1];
        context.runOnClient(client -> holder[0] = openFullscreen());
        context.waitTicks(40);

        // 初回起動の JourneyMap の案内画面を閉じる
        for (int attempt = 0; attempt < 3; attempt++) {
            if (context.computeOnClient(FlowClientGameTest::screenName).endsWith(".Fullscreen")) {
                break;
            }
            int w = context.computeOnClient(client -> client.getWindow().getWidth());
            int h = context.computeOnClient(client -> client.getWindow().getHeight());
            context.getInput().setCursorPos(w * 0.676, h * 0.9375);
            context.waitTicks(3);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(20);
        }
        if (!context.computeOnClient(FlowClientGameTest::screenName).endsWith(".Fullscreen")) {
            context.runOnClient(client -> holder[0] = openFullscreen());
            context.waitTicks(30);
        }
        check(context.computeOnClient(FlowClientGameTest::screenName).endsWith(".Fullscreen"),
                "全画面マップが開いている");

        context.waitFor(client -> StoreSession.repository().isAvailable(), TIMEOUT_TICKS);
        DrawingController controller = ScribbleSession.controller();
        check(controller != null, "コントローラが取り付いている");
        DrawingState state = ScribbleSession.state();
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        int guiW = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
        int guiH = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
        int width = context.computeOnClient(client -> client.getWindow().getWidth());
        int height = context.computeOnClient(client -> client.getWindow().getHeight());

        int n0 = memoCount(context);
        log("memos at start: " + n0);
        context.runOnClient(client -> {
            state.setModeOn(true);
            state.setTool(Tool.ENCLOSE);
        });
        context.waitTicks(10);
        double cx0 = context.computeOnClient(client -> center(holder[0], "getCenterBlockX"));
        double cz0 = context.computeOnClient(client -> center(holder[0], "getCenterBlockZ"));

        // 1. 手書きで囲む（一筆）
        double[][] path = {
                {width * 0.35, height * 0.65}, {width * 0.50, height * 0.35}, {width * 0.65, height * 0.65},
                {width * 0.50, height * 0.70}, {width * 0.36, height * 0.66}
        };
        context.getInput().setCursorPos(path[0][0], path[0][1]);
        context.waitTicks(2);
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        for (int i = 1; i < path.length; i++) {
            double fx = path[i - 1][0];
            double fy = path[i - 1][1];
            for (int s = 1; s <= 10; s++) {
                context.getInput().setCursorPos(fx + (path[i][0] - fx) * s / 10, fy + (path[i][1] - fy) * s / 10);
                context.waitTicks(1);
            }
        }
        log("離す前: 描いている一筆=" + (controller.activeStroke() == null ? "なし" : controller.activeStroke().points().size()
                + "点") + " モード=" + state.isModeOn() + " ツール=" + state.tool() + " isActive=" + controller.isActive());
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(15);
        log("離した後: 描いている一筆=" + (controller.activeStroke() == null ? "なし" : "あり") + " メモ数=" + memoCount(context)
                + " 画面=" + context.computeOnClient(FlowClientGameTest::screenName));

        // 2. 文字入力の画面が開き、地図は動いていない
        check(context.computeOnClient(FlowClientGameTest::screenName).endsWith(".NoteEditScreen"),
                "囲んだ後に文字入力の画面が開く（今: " + context.computeOnClient(FlowClientGameTest::screenName) + "）");
        check(memoCount(context) == n0, "確定の前はメモが増えていない");
        double cx1 = context.computeOnClient(client -> center(holder[0], "getCenterBlockX"));
        double cz1 = context.computeOnClient(client -> center(holder[0], "getCenterBlockZ"));
        check(cx1 == cx0 && cz1 == cz0, "ドラッグで地図が動いていない（dx=" + (cx1 - cx0) + " dz=" + (cz1 - cz0) + "）");

        // 3. 文字を入れて確定する
        context.getInput().typeChars(NOTE);
        context.waitTicks(5);
        EditLayout layout = new EditLayout(MemoKind.REGION, false, guiH);
        double saveX = layout.panelX(guiW) + layout.saveX() + layout.footerButtonWidth() / 2.0;
        double saveY = layout.panelY() + layout.buttonsY() + EditLayout.BUTTON_HEIGHT / 2.0;
        clickGui(context, saveX, saveY, scale);
        context.waitTicks(10);
        check(context.computeOnClient(FlowClientGameTest::screenName).endsWith(".Fullscreen"),
                "確定で全画面マップへ戻る（今: " + context.computeOnClient(FlowClientGameTest::screenName) + "）");
        check(memoCount(context) == n0 + 1, "メモが 1 件増えた（" + n0 + " -> " + memoCount(context) + "）");
        check(countWithText(context, NOTE) == 1, "入れた文字「" + NOTE + "」のメモがある");
        Memo saved = context.computeOnClient(client -> {
            List<Memo> all = StoreSession.repository().memos();
            return all.get(all.size() - 1);
        });
        check(saved.kind() == MemoKind.REGION && saved.points().size() >= 3, "囲み（REGION）で点が 3 つ以上: points="
                + saved.points().size());

        // 4. ファイルに保存される
        check(waitFile(context, NOTE, true), "mapscribble/saves/<ワールド>/minecraft__overworld.json に「" + NOTE
                + "」が書き出された（" + overworldFiles() + "）");

        // 5. 消しゴム（数字キー 4）で、描いた囲みの中をクリックして消す
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_4);
        context.waitTicks(3);
        check(state.tool() == Tool.ERASER, "数字キー 4 で消しゴムに切り替わる（今: " + state.tool() + "）");
        context.getInput().setCursorPos(width * 0.50, height * 0.50);
        context.waitTicks(5);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(10);
        check(memoCount(context) == n0, "消しゴムのクリックでメモが消えた（今 " + memoCount(context) + "）");
        check(waitFile(context, NOTE, false), "ファイルからも「" + NOTE + "」が消えた");

        // 6. Ctrl+Z で戻る。キー配線の確認が先。効かなければ、同じ取り消しを帯の取り消しボタンのクリックで確かめる
        context.getInput().holdControl();
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Z);
        context.getInput().releaseControl();
        context.waitTicks(5);
        boolean keyWorked = memoCount(context) == n0 + 1;
        log("Ctrl+Z のキー入力で戻った: " + keyWorked);
        if (!keyWorked) {
            BandLayout.Cell undo = BandLayout.cells().stream().filter(c -> c.kind() == BandLayout.Kind.UNDO)
                    .findFirst().orElseThrow();
            clickGui(context, undo.x() + undo.w() / 2.0, undo.y() + undo.h() / 2.0, scale);
            context.waitTicks(5);
        }
        check(memoCount(context) == n0 + 1, "取り消しでメモが戻った（Ctrl+Z のキー入力で戻った=" + keyWorked + "）");
        check(countWithText(context, NOTE) == 1, "戻ったメモは入れた文字を保っている");
        check(waitFile(context, NOTE, true), "戻したメモがファイルにも書き戻された");

        // 7. やり直し（Ctrl+Y のキー、効かなければ帯のボタン）で、もう一度消える
        context.getInput().holdControl();
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Y);
        context.getInput().releaseControl();
        context.waitTicks(5);
        boolean redoKeyWorked = memoCount(context) == n0;
        log("Ctrl+Y のキー入力でやり直せた: " + redoKeyWorked);
        if (!redoKeyWorked) {
            BandLayout.Cell redo = BandLayout.cells().stream().filter(c -> c.kind() == BandLayout.Kind.REDO)
                    .findFirst().orElseThrow();
            clickGui(context, redo.x() + redo.w() / 2.0, redo.y() + redo.h() / 2.0, scale);
            context.waitTicks(5);
        }
        check(memoCount(context) == n0, "やり直しで再び消えた（Ctrl+Y のキー入力で=" + redoKeyWorked + "）");
        check(waitFile(context, NOTE, false), "やり直しの後もファイルに「" + NOTE + "」が無い");

        context.takeScreenshot("mapscribble-flow-end");
    }
}
