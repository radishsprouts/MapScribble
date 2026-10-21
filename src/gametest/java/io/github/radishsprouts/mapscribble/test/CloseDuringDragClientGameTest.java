package io.github.radishsprouts.mapscribble.test;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/**
 * 手書きモードでドラッグしている途中（ボタンを押したまま）に全画面マップを閉じると、離すイベントが来ないので
 * 描きかけの一筆が残り、開き直すと線が出たままになる不具合の試験。画面を閉じた時に描きかけが捨てられ、
 * 開き直した後に描きかけの一筆が無いことを見る。結果は標準出力へ「[MS-CLOSE] 」を付けて出す。
 *
 * <p>手書きモードのオンオフと道具は、終わると元へ戻す。
 */
@SuppressWarnings("UnstableApiUsage")
public class CloseDuringDragClientGameTest implements FabricClientGameTest {
    private static void log(String message) {
        System.out.println("[MS-CLOSE] " + message);
    }

    private static void check(boolean ok, String message) {
        log((ok ? "OK   " : "FAIL ") + message);
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static void openFullscreen() {
        try {
            Class<?> ui = Class.forName("journeymap.client.ui.UIManager");
            Object instance = ui.getField("INSTANCE").get(null);
            Method open = ui.getMethod("getOrOpenFullscreenMap");
            open.invoke(instance);
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
        DrawingController controller = ScribbleSession.controller();
        DrawingState state = ScribbleSession.state();
        check(controller != null && state != null, "コントローラと手書きの状態が取り付いている");

        context.setScreen(() -> null);
        context.waitTicks(10);
        context.runOnClient(client -> openFullscreen());
        context.waitTicks(40);
        check(context.computeOnClient(CloseDuringDragClientGameTest::screenName).endsWith(".Fullscreen"),
                "全画面マップが開いている（今: " + context.computeOnClient(CloseDuringDragClientGameTest::screenName) + "）");

        Tool toolBefore = state.tool();
        boolean modeBefore = state.isModeOn();
        context.runOnClient(client -> {
            state.setModeOn(true);
            state.setTool(Tool.LINE);
        });
        context.waitTicks(10);
        check(controller.activeStroke() == null, "ドラッグの前は描きかけの一筆が無い");

        int width = context.computeOnClient(client -> client.getWindow().getWidth());
        int height = context.computeOnClient(client -> client.getWindow().getHeight());

        // 始点は帯の外に置く。押して動かすが、離さない
        context.getInput().setCursorPos(width * 0.60, height * 0.30);
        context.waitTicks(2);
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        for (int s = 1; s <= 10; s++) {
            context.getInput().setCursorPos(width * 0.60 + width * 0.01 * s, height * 0.30 + height * 0.01 * s);
            context.waitTicks(1);
        }
        check(controller.activeStroke() != null, "押したまま動かすと、描きかけの一筆がある");

        // 離さずに画面を閉じる（Esc 相当）
        context.setScreen(() -> null);
        context.waitTicks(10);
        check(controller.activeStroke() == null, "画面を閉じた時点で、描きかけの一筆が捨てられる");

        // 押したままの状態を解いてから、開き直す
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
        context.runOnClient(client -> openFullscreen());
        context.waitTicks(30);
        check(context.computeOnClient(CloseDuringDragClientGameTest::screenName).endsWith(".Fullscreen"),
                "全画面マップを開き直した");
        check(controller.activeStroke() == null, "開き直しても、描きかけの一筆が無い");

        // 以後の試験へ状態を持ち越さない
        context.runOnClient(client -> {
            state.setTool(toolBefore);
            state.setModeOn(modeBefore);
        });
        context.waitTicks(5);
        log("ドラッグ中に閉じても描きかけが残らない");
    }
}
