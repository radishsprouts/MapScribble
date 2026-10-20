package io.github.radishsprouts.mapscribble.test;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * 実機で JourneyMap の全画面マップを開き、手書きの一筆（囲み・線）が描け、確定し、取り消せることを確かめる（作業5）。
 * 結果は標準出力へ「[MS-TEST] 」を付けて出す。画面の確認用に撮影する。
 */
@SuppressWarnings("UnstableApiUsage")
public class DrawingClientGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 600;

    private static void log(String message) {
        System.out.println("[MS-TEST] " + message);
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

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            //? if >=26.2 {
            /*singleplayer.getConnection().waitForChunksRender();
            *///?} else {
            singleplayer.getClientLevel().waitForChunksRender();
            //?}
            context.waitTicks(100);

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
                context.getInput().pressMouse(0);
                context.waitTicks(20);
            }

            // メモの窓口が使えるまで待つ（JourneyMap の MAPPING_STARTED で窓口ができる）
            boolean available;
            try {
                context.waitFor(client -> StoreSession.repository().isAvailable(), TIMEOUT_TICKS);
                available = true;
            } catch (Throwable t) {
                available = false;
            }
            log("repository available: " + available);
            DrawingController controller = ScribbleSession.controller();
            log("controller installed: " + (controller != null));
            if (controller == null || !available) {
                context.takeScreenshot("mapscribble-fullscreen-no-repo");
                return;
            }

            DrawingState state = ScribbleSession.state();
            context.runOnClient(client -> {
                state.setModeOn(true);
                state.setTool(Tool.ENCLOSE);
            });
            context.waitTicks(10);

            int width = context.computeOnClient(client -> client.getWindow().getWidth());
            int height = context.computeOnClient(client -> client.getWindow().getHeight());
            log("window " + width + "x" + height + " guiScale="
                    + context.computeOnClient(client -> client.getWindow().getGuiScale()));

            double cx0 = context.computeOnClient(client -> centerX(fullscreen));
            double cz0 = context.computeOnClient(client -> centerZ(fullscreen));

            // 斜めの三角形を一筆で囲む
            double[][] path = {
                    {width * 0.30, height * 0.70}, {width * 0.50, height * 0.30}, {width * 0.70, height * 0.65},
                    {width * 0.50, height * 0.68}, {width * 0.31, height * 0.71}
            };
            context.getInput().setCursorPos(path[0][0], path[0][1]);
            context.waitTicks(2);
            context.getInput().holdMouse(0);
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
            context.getInput().releaseMouse(0);
            context.waitTicks(10);

            double cx1 = context.computeOnClient(client -> centerX(fullscreen));
            double cz1 = context.computeOnClient(client -> centerZ(fullscreen));
            log("map center moved by drag: dx=" + (cx1 - cx0) + " dz=" + (cz1 - cz0)
                    + " (0 なら、クリックの取り消しでマップが動かなかった)");

            List<Memo> memos = StoreSession.repository().memos();
            log("memos after release: " + memos.size());
            if (!memos.isEmpty()) {
                Memo m = memos.get(memos.size() - 1);
                log("memo kind=" + m.kind() + " points=" + m.points().size() + " first=" + m.points().get(0));
            }
            context.takeScreenshot("mapscribble-region-committed");

            // 線
            context.runOnClient(client -> state.setTool(Tool.LINE));
            context.getInput().setCursorPos(width * 0.20, height * 0.20);
            context.waitTicks(2);
            context.getInput().holdMouse(0);
            context.waitTicks(2);
            for (int s = 1; s <= 20; s++) {
                context.getInput().setCursorPos(width * 0.20 + width * 0.01 * s, height * 0.20 + height * 0.012 * s);
                context.waitTicks(1);
            }
            context.getInput().releaseMouse(0);
            context.waitTicks(10);
            log("memos after line: " + StoreSession.repository().memos().size());
            context.takeScreenshot("mapscribble-line-committed");

            // 取り消し。テスト用の入力は Ctrl の修飾ビットを付けない（キーイベントは届くが modifiers=0 になる）ので、
            // Ctrl+Z のキー配線そのものは確かめられない。取り消しの効果は、実際の窓口に対して controller.undo() を呼んで確かめる
            context.getInput().holdControl();
            context.getInput().pressKey(90);
            context.getInput().releaseControl();
            context.waitTicks(5);
            log("memos after synthetic Ctrl+Z key (modifiers are 0 in the harness): "
                    + StoreSession.repository().memos().size());
            context.runOnClient(client -> controller.undo());
            context.waitTicks(5);
            log("memos after controller.undo(): " + StoreSession.repository().memos().size());
            context.takeScreenshot("mapscribble-after-undo");

            // ズーム直後に描いても座標が合うか: ズームして一筆描き、1 ブロック=blockSize の再計算が追従するかを見る
            context.getInput().scroll(1);
            context.waitTicks(1);
            context.runOnClient(client -> state.setTool(Tool.ENCLOSE));
            context.getInput().setCursorPos(width * 0.40, height * 0.40);
            context.waitTicks(1);
            context.getInput().holdMouse(0);
            context.waitTicks(1);
            context.getInput().setCursorPos(width * 0.50, height * 0.40);
            context.waitTicks(1);
            context.getInput().setCursorPos(width * 0.50, height * 0.50);
            context.waitTicks(1);
            context.getInput().releaseMouse(0);
            context.waitTicks(5);
            log("memos after zoomed stroke: " + StoreSession.repository().memos().size());
            context.takeScreenshot("mapscribble-after-zoom");

            // ミニマップ: 全画面を閉じて撮る
            context.setScreen(() -> null);
            context.waitTicks(40);
            context.takeScreenshot("mapscribble-minimap");
        }
    }
}
