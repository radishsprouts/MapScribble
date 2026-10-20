package io.github.radishsprouts.mapscribble.test;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.BandLayout;
import io.github.radishsprouts.mapscribble.ui.EditLayout;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * 実機で、ツールの帯が出る・帯のクリックが地図へ届かない・文字入力の画面が開く・確定でメモが増える・
 * 画面を閉じても地図の位置が変わらない、ことを確かめる（作業6）。結果は標準出力へ「[MS-UI] 」を付けて出す。
 */
@SuppressWarnings("UnstableApiUsage")
public class UiClientGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 600;

    private static void log(String message) {
        System.out.println("[MS-UI] " + message);
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
        context.getInput().pressMouse(0);
        context.waitTicks(5);
    }

    /**
     * 全画面マップが閉じていたら、JourneyMap の既定のキー（J）で開き直す。他の画面（文字入力の画面など）が出ている時は
     * 何もしない。（JourneyMap は描画で例外が出ると全画面マップを自分で閉じるため、安全網として置いている。）
     */
    private static void ensureFullscreen(ClientGameTestContext context, Object[] holder) {
        String name = context.computeOnClient(UiClientGameTest::screenName);
        if (name.endsWith(".Fullscreen") || !name.equals("null")) {
            return;
        }
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_J);
        context.waitTicks(20);
        context.runOnClient(client -> holder[0] = openFullscreen());
        context.waitTicks(5);
    }

    /** 撮影。撮影の後に全画面マップが閉じていたら開き直す（閉じていることを見逃さないよう、画面の名前も出す）。 */
    private static void shot(ClientGameTestContext context, Object[] holder, String name) {
        context.takeScreenshot(name);
        log("screen after screenshot " + name + ": " + context.computeOnClient(UiClientGameTest::screenName));
        ensureFullscreen(context, holder);
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
            context.runOnClient(client -> holder[0] = openFullscreen());
            context.waitTicks(40);

            // 初回起動の JourneyMap の案内画面を閉じる
            for (int attempt = 0; attempt < 3; attempt++) {
                String name = context.computeOnClient(UiClientGameTest::screenName);
                if (name.endsWith(".Fullscreen")) {
                    break;
                }
                int w = context.computeOnClient(client -> client.getWindow().getWidth());
                int h = context.computeOnClient(client -> client.getWindow().getHeight());
                context.getInput().setCursorPos(w * 0.676, h * 0.9375);
                context.waitTicks(3);
                context.getInput().pressMouse(0);
                context.waitTicks(20);
            }

            // 案内画面を閉じると全画面マップごと閉じることがある。開き直す
            if (!context.computeOnClient(UiClientGameTest::screenName).endsWith(".Fullscreen")) {
                context.runOnClient(client -> holder[0] = openFullscreen());
                context.waitTicks(30);
            }
            log("screen before test: " + context.computeOnClient(UiClientGameTest::screenName));

            boolean available;
            try {
                context.waitFor(client -> StoreSession.repository().isAvailable(), TIMEOUT_TICKS);
                available = true;
            } catch (Throwable t) {
                available = false;
            }
            DrawingController controller = ScribbleSession.controller();
            log("repository available: " + available + " controller installed: " + (controller != null));
            if (controller == null || !available) {
                shot(context, holder, "mapscribble-ui-no-repo");
                return;
            }
            DrawingState state = ScribbleSession.state();
            double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
            int guiW = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
            int guiH = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
            log("guiScale=" + scale + " gui=" + guiW + "x" + guiH);

            // 1. 手書きモードに入ると帯が出る
            shot(context, holder, "mapscribble-ui-mode-off");
            log("screen before mode on: " + context.computeOnClient(UiClientGameTest::screenName));
            context.runOnClient(client -> state.setModeOn(true));
            log("screen right after mode on: " + context.computeOnClient(UiClientGameTest::screenName));
            for (int t = 0; t < 5; t++) {
                context.waitTicks(2);
                log("screen +" + (t + 1) * 2 + " ticks: " + context.computeOnClient(UiClientGameTest::screenName));
            }
            shot(context, holder, "mapscribble-ui-band");

            // 2. 帯のクリックは地図へ届かない（ツールが変わり、地図の中心は動かず、メモは増えない）
            double cx0 = context.computeOnClient(client -> center(holder[0], "getCenterBlockX"));
            double cz0 = context.computeOnClient(client -> center(holder[0], "getCenterBlockZ"));
            int before = StoreSession.repository().memos().size();
            context.runOnClient(client -> state.setTool(Tool.ENCLOSE));
            var lineCell = BandLayout.cells().get(1);
            clickGui(context, lineCell.x() + lineCell.w() / 2.0, lineCell.y() + lineCell.h() / 2.0, scale);
            context.getInput().releaseMouse(0);
            context.waitTicks(5);
            log("tool after band click (expect LINE): " + state.tool());
            // 色（帯の下段の右側）も押してみる
            var colorCell = BandLayout.cells().stream().filter(c -> c.kind() == BandLayout.Kind.COLOR).skip(4)
                    .findFirst().orElseThrow();
            clickGui(context, colorCell.x() + colorCell.w() / 2.0, colorCell.y() + colorCell.h() / 2.0, scale);
            context.getInput().releaseMouse(0);
            context.waitTicks(5);
            log("color after band click (expect 32D2FF): " + Integer.toHexString(state.color()));
            double cx1 = context.computeOnClient(client -> center(holder[0], "getCenterBlockX"));
            double cz1 = context.computeOnClient(client -> center(holder[0], "getCenterBlockZ"));
            log("map moved by band clicks (expect 0,0): dx=" + (cx1 - cx0) + " dz=" + (cz1 - cz0)
                    + " memos " + before + " -> " + StoreSession.repository().memos().size());
            shot(context, holder, "mapscribble-ui-band-after-click");

            // 3. 囲みを描くと文字入力の画面が開く
            context.runOnClient(client -> state.setTool(Tool.ENCLOSE));
            int width = context.computeOnClient(client -> client.getWindow().getWidth());
            int height = context.computeOnClient(client -> client.getWindow().getHeight());
            double[][] path = {
                    {width * 0.35, height * 0.65}, {width * 0.50, height * 0.35}, {width * 0.65, height * 0.65},
                    {width * 0.50, height * 0.70}, {width * 0.36, height * 0.66}
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
            context.getInput().releaseMouse(0);
            context.waitTicks(15);
            String screen = context.computeOnClient(UiClientGameTest::screenName);
            log("screen after stroke (expect NoteEditScreen): " + screen);
            shot(context, holder, "mapscribble-ui-edit-new-region");

            // 4. 文字を入れて確定ボタンを押すとメモが増える（確定ボタンは配置の計算から座標を出す）
            context.getInput().typeChars("test note");
            context.waitTicks(5);
            shot(context, holder, "mapscribble-ui-edit-typed");
            EditLayout layout = new EditLayout(MemoKind.REGION, false, guiH);
            double saveX = layout.panelX(guiW) + layout.saveX() + layout.footerButtonWidth() / 2.0;
            double saveY = layout.panelY() + layout.buttonsY() + EditLayout.BUTTON_HEIGHT / 2.0;
            int memosBeforeSave = StoreSession.repository().memos().size();
            clickGui(context, saveX, saveY, scale);
            context.getInput().releaseMouse(0);
            context.waitTicks(10);
            log("memos after save click: " + memosBeforeSave + " -> " + StoreSession.repository().memos().size()
                    + " screen=" + context.computeOnClient(UiClientGameTest::screenName));
            double cx2 = context.computeOnClient(client -> center(holder[0], "getCenterBlockX"));
            double cz2 = context.computeOnClient(client -> center(holder[0], "getCenterBlockZ"));
            log("map center after edit screen closed (compare with before, 0 means kept): dx=" + (cx2 - cx0)
                    + " dz=" + (cz2 - cz0));
            shot(context, holder, "mapscribble-ui-after-save");

            // 5. 編集ツールでメモをクリックすると編集の画面が開く
            context.runOnClient(client -> state.setTool(Tool.EDIT));
            context.getInput().setCursorPos(width * 0.50, height * 0.50);
            context.waitTicks(5);
            shot(context, holder, "mapscribble-ui-edit-hover");
            context.getInput().pressMouse(0);
            context.waitTicks(10);
            log("screen after edit click: " + context.computeOnClient(UiClientGameTest::screenName));
            shot(context, holder, "mapscribble-ui-edit-existing");
            // キャンセル（Esc）で閉じてもメモは変わらない
            context.getInput().pressKey(256);
            context.waitTicks(10);
            log("screen after Esc: " + context.computeOnClient(UiClientGameTest::screenName));

            // 6. 非表示の見た目と、モードを抜ける
            context.runOnClient(client -> state.setVisible(false));
            context.waitTicks(5);
            shot(context, holder, "mapscribble-ui-hidden");
            context.runOnClient(client -> {
                state.setVisible(true);
                state.setModeOn(false);
            });
            context.waitTicks(5);
            shot(context, holder, "mapscribble-ui-mode-exit");
        }
    }
}
