package io.github.radishsprouts.mapscribble.test;

import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * 全画面マップの右列の「手書きモードにする」切替ボタンが、1つだけであることを確かめる。
 * 全画面マップを開く・閉じて開き直す・画面を初期化し直す、のそれぞれの後で、画面の部品の中にある
 * MapScribble の切替ボタンを数え、同じ座標に重なっていないことと、オンオフの表示が状態と合っていることを見る。
 * 結果は標準出力へ「[MS-TOGGLE] 」を付けて出す。期待と違えば AssertionError で落とす。
 */
@SuppressWarnings("UnstableApiUsage")
public class ToggleButtonClientGameTest implements FabricClientGameTest {
    private static void log(String message) {
        System.out.println("[MS-TOGGLE] " + message);
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

    private static Screen currentScreen(Minecraft client) {
        //? if >=26.2 {
        /*return client.gui.screen();
        *///?} else {
        return client.screen;
        //?}
    }

    private static String screenName(Minecraft client) {
        Screen screen = currentScreen(client);
        return screen == null ? "null" : screen.getClass().getName();
    }

    /** Screen が持つ「描画する部品」の一覧（private の renderables）。 */
    @SuppressWarnings("unchecked")
    private static List<Object> renderablesOf(Screen screen) {
        try {
            Field field = Screen.class.getDeclaredField("renderables");
            field.setAccessible(true);
            return new ArrayList<>((List<Object>) field.get(screen));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** MapScribble の切替ボタンか（アイコンの識別子で見分ける。JourneyMap の ThemeButton の icon）。 */
    private static boolean isMapScribbleToggle(Object part) {
        for (Class<?> c = part.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("journeymap.client.ui.theme.ThemeButton")) {
                try {
                    Field icon = c.getDeclaredField("icon");
                    icon.setAccessible(true);
                    Object id = icon.get(part);
                    return id != null && id.toString().contains("mapscribble") && id.toString().contains("toggle");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return false;
    }

    private static List<Object> togglesIn(List<?> parts) {
        List<Object> found = new ArrayList<>();
        for (Object part : parts) {
            if (isMapScribbleToggle(part)) {
                found.add(part);
            }
        }
        return found;
    }

    private static Boolean toggledOf(Object button) {
        try {
            return (Boolean) button.getClass().getMethod("getToggled").invoke(button);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 今の全画面マップの切替ボタンを数えて検査し、ボタンを1つ返す。 */
    private static Object verifySingle(ClientGameTestContext context, String when) {
        check(context.computeOnClient(ToggleButtonClientGameTest::screenName).endsWith(".Fullscreen"),
                when + ": 全画面マップが開いている");
        return context.computeOnClient(client -> {
            Screen screen = currentScreen(client);
            List<Object> inRenderables = togglesIn(renderablesOf(screen));
            List<GuiEventListener> children = new ArrayList<>(screen.children());
            List<Object> inChildren = togglesIn(children);
            Set<Object> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
            distinct.addAll(inRenderables);
            distinct.addAll(inChildren);
            log(when + ": 切替ボタン renderables=" + inRenderables.size() + " children=" + inChildren.size()
                    + " 別々の実体=" + distinct.size());
            check(inRenderables.size() == 1, when + ": 描画する部品の中の切替ボタンは1つ（実際 " + inRenderables.size() + "）");
            check(inChildren.size() == 1, when + ": 入力を受ける部品の中の切替ボタンは1つ（実際 " + inChildren.size() + "）");
            check(distinct.size() == 1, when + ": 切替ボタンの実体は1つ（実際 " + distinct.size() + "）");
            AbstractWidget widget = (AbstractWidget) distinct.iterator().next();
            log(when + ": 切替ボタンの座標 (" + widget.getX() + "," + widget.getY() + ")");
            return widget;
        });
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
        DrawingState state = ScribbleSession.state();
        check(state != null, "手書きの状態が取り付いている");

        // 閉じた状態から開く
        context.setScreen(() -> null);
        context.waitTicks(10);
        context.runOnClient(client -> openFullscreen());
        context.waitTicks(40);
        verifySingle(context, "開いた直後");

        // 初期化し直し（ウィンドウのサイズ変更や別の画面から戻った時と同じ経路）
        context.runOnClient(client -> {
            Screen screen = currentScreen(client);
            screen.resize(screen.width, screen.height);
        });
        context.waitTicks(10);
        verifySingle(context, "resize の後");

        // 閉じて開き直し（オフのまま）
        context.setScreen(() -> null);
        context.waitTicks(10);
        context.runOnClient(client -> openFullscreen());
        context.waitTicks(30);
        Object button = verifySingle(context, "開き直した後（オフ）");
        check(Boolean.FALSE.equals(toggledOf(button)), "開き直した後（オフ）: ボタンの表示はオフ");

        // オンにして、閉じて開き直しても、ボタンが1つでオンの表示のまま
        context.runOnClient(client -> state.setModeOn(true));
        context.waitTicks(10);
        button = verifySingle(context, "オンにした後");
        check(Boolean.TRUE.equals(toggledOf(button)), "オンにした後: ボタンの表示はオン");
        context.setScreen(() -> null);
        context.waitTicks(10);
        context.runOnClient(client -> openFullscreen());
        context.waitTicks(30);
        button = verifySingle(context, "開き直した後（オン）");
        check(Boolean.TRUE.equals(toggledOf(button)), "開き直した後（オン）: ボタンの表示はオン");

        // オフへ戻す（以後の試験へ状態を持ち越さない）
        context.runOnClient(client -> state.setModeOn(false));
        context.waitTicks(10);
        button = verifySingle(context, "オフへ戻した後");
        check(Boolean.FALSE.equals(toggledOf(button)), "オフへ戻した後: ボタンの表示はオフ");
        log("切替ボタンの重複なし");
    }
}
