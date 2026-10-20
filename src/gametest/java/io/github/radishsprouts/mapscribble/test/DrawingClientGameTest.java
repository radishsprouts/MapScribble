package io.github.radishsprouts.mapscribble.test;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * 実機で JourneyMap の全画面マップを開き、手書きの一筆（囲み・線）が描け、確定し、取り消せることを確かめる（作業5・7）。
 * 経過は標準出力へ「[MS-TEST] 」を付けて出し、期待と違えば AssertionError で落とす。画面の確認用に撮影する。
 *
 * <p>この試験は確定後の処理（文字入力の画面を開く）を外す（{@code setDraftHandler(null)}）ので、
 * 同じワールドで続けて動かす時は最後に置く（AllClientGameTest）。
 */
@SuppressWarnings("UnstableApiUsage")
public class DrawingClientGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 600;

    private static void log(String message) {
        System.out.println("[MS-TEST] " + message);
    }

    private static void check(boolean ok, String message) {
        log((ok ? "OK   " : "FAIL ") + message);
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    /** 全画面マップ（journeymap.client.ui.fullscreen.Fullscreen）を開く。JourneyMap の内部クラスなのでリフレクションで呼ぶ。 */
    private static Object openFullscreen(Minecraft client) {
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

    private static double centerX(Object fullscreen) {
        return center(fullscreen, "getCenterBlockX");
    }

    private static double centerZ(Object fullscreen) {
        return center(fullscreen, "getCenterBlockZ");
    }

    private static int memoCount(ClientGameTestContext context) {
        return context.computeOnClient(client -> StoreSession.repository().memos().size());
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
        context.runOnClient(client -> holder[0] = openFullscreen(client));
        Object fullscreen = holder[0];
        context.waitTicks(40);
        log("fullscreen opened: " + fullscreen.getClass().getName());

        // 初回起動の JourneyMap は「About / What's New」の画面を全画面マップの上に出す。閉じるボタンを押して消す
        for (int attempt = 0; attempt < 3; attempt++) {
            String screenName = context.computeOnClient(DrawingClientGameTest::screenName);
            log("current screen: " + screenName);
            if (screenName.endsWith(".Fullscreen")) {
                break;
            }
            int w = context.computeOnClient(client -> client.getWindow().getWidth());
            int h = context.computeOnClient(client -> client.getWindow().getHeight());
            context.getInput().setCursorPos(w * 0.676, h * 0.9375);
            context.waitTicks(3);
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(20);
        }

        // 案内画面を閉じると全画面マップごと閉じることがある。開き直す
        if (!context.computeOnClient(DrawingClientGameTest::screenName).endsWith(".Fullscreen")) {
            context.runOnClient(client -> holder[0] = openFullscreen(client));
            context.waitTicks(30);
            if (!context.computeOnClient(DrawingClientGameTest::screenName).endsWith(".Fullscreen")) {
                // 開いた直後に閉じられる場合は、JourneyMap の既定のキー（J）で開く
                context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_J);
                context.waitTicks(30);
                // 開いている全画面マップを取り直す（開いていれば同じものが返る）
                context.runOnClient(client -> holder[0] = openFullscreen(client));
            }
            fullscreen = holder[0];
        }
        final Object fullscreenNow = fullscreen;
        check(context.computeOnClient(DrawingClientGameTest::screenName).endsWith(".Fullscreen"),
                "全画面マップが開いている（今: " + context.computeOnClient(DrawingClientGameTest::screenName) + "）");

        // メモの窓口が使えるまで待つ（JourneyMap の MAPPING_STARTED で窓口ができる）
        context.waitFor(client -> StoreSession.repository().isAvailable(), TIMEOUT_TICKS);
        check(StoreSession.repository().isAvailable(), "メモの窓口が使える");
        DrawingController controller = ScribbleSession.controller();
        check(controller != null, "コントローラが取り付いている");

        DrawingState state = ScribbleSession.state();
        // 一筆の確定後は文字入力の画面が開く（作業6）。この試験は一筆の描画と取り消しだけを確かめるので、画面は開かせない
        // （画面を含む確認は UiClientGameTest / FlowClientGameTest）
        state.setDraftHandler(null);
        context.runOnClient(client -> {
            state.setModeOn(true);
            state.setTool(Tool.ENCLOSE);
        });
        context.waitTicks(10);

        int width = context.computeOnClient(client -> client.getWindow().getWidth());
        int height = context.computeOnClient(client -> client.getWindow().getHeight());
        log("window " + width + "x" + height + " guiScale="
                + context.computeOnClient(client -> client.getWindow().getGuiScale()));

        int n0 = memoCount(context);
        double cx0 = context.computeOnClient(client -> centerX(fullscreenNow));
        double cz0 = context.computeOnClient(client -> centerZ(fullscreenNow));

        // 斜めの三角形を一筆で囲む
        double[][] path = {
                {width * 0.30, height * 0.70}, {width * 0.50, height * 0.30}, {width * 0.70, height * 0.65},
                {width * 0.50, height * 0.68}, {width * 0.31, height * 0.71}
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
        // 描画中の見た目（離す前）
        context.takeScreenshot("mapscribble-stroke-in-progress");
        check(controller.activeStroke() != null, "離す前は描いている最中の一筆がある");
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(10);

        double cx1 = context.computeOnClient(client -> centerX(fullscreenNow));
        double cz1 = context.computeOnClient(client -> centerZ(fullscreenNow));
        check(cx1 == cx0 && cz1 == cz0, "ドラッグで地図が動かない: dx=" + (cx1 - cx0) + " dz=" + (cz1 - cz0));

        List<Memo> memos = StoreSession.repository().memos();
        check(memos.size() == n0 + 1, "囲みが 1 件追加された（" + n0 + " -> " + memos.size() + "）");
        Memo m = memos.get(memos.size() - 1);
        check(m.kind() == MemoKind.REGION && m.points().size() >= 3,
                "追加されたのは囲み（REGION）で点が 3 つ以上: kind=" + m.kind() + " points=" + m.points().size()
                        + " first=" + m.points().get(0));
        context.takeScreenshot("mapscribble-region-committed");

        // 線（始点は帯（GUI 28,44 〜 236,101）の外に置く）
        context.runOnClient(client -> state.setTool(Tool.LINE));
        context.getInput().setCursorPos(width * 0.60, height * 0.20);
        context.waitTicks(2);
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        for (int s = 1; s <= 20; s++) {
            context.getInput().setCursorPos(width * 0.60 + width * 0.01 * s, height * 0.20 + height * 0.012 * s);
            context.waitTicks(1);
        }
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(10);
        check(memoCount(context) == n0 + 2, "線が 1 件追加された（今 " + memoCount(context) + "）");
        check(StoreSession.repository().memos().get(n0 + 1).kind() == MemoKind.LINE, "追加されたのは線（LINE）");
        context.takeScreenshot("mapscribble-line-committed");

        // Ctrl+Z のキー入力で取り消す（Ctrl の押下は窓のキー状態でも見ているので、テストの入力でも効く）
        context.getInput().holdControl();
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Z);
        context.getInput().releaseControl();
        context.waitTicks(5);
        check(memoCount(context) == n0 + 1, "Ctrl+Z のキー入力で直前の線が取り消された（今 " + memoCount(context) + "）");
        context.takeScreenshot("mapscribble-after-undo");

        // ズーム直後に描いても座標が合うか: ズームして一筆描き、1 ブロック=blockSize の再計算が追従するかを見る
        context.getInput().scroll(1);
        context.waitTicks(5);
        context.runOnClient(client -> state.setTool(Tool.ENCLOSE));
        context.getInput().setCursorPos(width * 0.60, height * 0.50);
        context.waitTicks(1);
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(1);
        double[][] zoomed = {{0.80, 0.50}, {0.80, 0.70}, {0.60, 0.70}};
        for (double[] p : zoomed) {
            context.getInput().setCursorPos(width * p[0], height * p[1]);
            context.waitTicks(2);
        }
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
        check(memoCount(context) == n0 + 2, "ズーム後の一筆も追加された（今 " + memoCount(context) + "）");
        context.takeScreenshot("mapscribble-after-zoom");

        // ミニマップ: 全画面を閉じて撮る
        context.setScreen(() -> null);
        context.waitTicks(40);
        context.takeScreenshot("mapscribble-minimap");
    }
}
