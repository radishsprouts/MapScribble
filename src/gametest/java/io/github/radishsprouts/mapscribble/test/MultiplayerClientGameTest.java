package io.github.radishsprouts.mapscribble.test;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.EditLayout;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * マルチプレイ（専用サーバーに接続した状態）で描いたメモが、マルチ用の保存先
 * （&lt;ゲームフォルダ&gt;/mapscribble/servers/&lt;サーバーのアドレス&gt;/）へ書かれ、
 * シングル用（&lt;ゲームフォルダ&gt;/mapscribble/saves/）へは書かれないことを確かめる
 * （保存先は StoreSession.createKey が決める。地図 MOD には依存しない）。
 * 経過は標準出力へ「[MS-MP] 」を付けて出す。期待と違えば AssertionError で落とす。
 *
 * <p>流れ: 専用サーバーを立てて接続 → 全画面マップで囲む → 文字 → 保存 → mp の下のファイルを確かめる（sp の下は変わらない）
 * → 切断して接続し直す → 同じメモが読み込まれる。
 */
@SuppressWarnings("UnstableApiUsage")
public class MultiplayerClientGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 600;
    private static final String NOTE = "mp note";
    /** mapscribble/ の下の、マルチ（専用サーバーへの接続）の保存先のフォルダ名。StoreSession.createKey の "servers/" */
    private static final String MULTI_KIND = "servers";
    /** 同じく、シングルのワールドの保存先のフォルダ名。"saves/" */
    private static final String SINGLE_KIND = "saves";

    private static void log(String message) {
        System.out.println("[MS-MP] " + message);
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

    private static void clickGui(ClientGameTestContext context, double guiX, double guiY, double scale) {
        context.getInput().setCursorPos(guiX * scale, guiY * scale);
        context.waitTicks(3);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(5);
    }

    private static long countWithText(ClientGameTestContext context, String text) {
        return context.computeOnClient(client -> StoreSession.repository().memos().stream()
                .filter(m -> text.equals(m.text())).count());
    }

    /** メモの保存先の根（StoreSession.createKey の baseDir）。<ゲームフォルダ>/mapscribble/ */
    private static Path dataRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("mapscribble");
    }

    /**
     * mapscribble/<kind>/ の下にあるオーバーワールドのメモのファイル（kind は "servers"＝マルチ、"saves"＝シングル）。
     * ファイル名は MemoFile.fileName("minecraft:overworld") と同じ。
     */
    private static List<Path> overworldFiles(String kind) {
        Path root = dataRoot().resolve(kind);
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

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean anyContains(List<Path> files, String text) {
        for (Path p : files) {
            if (read(p).contains(text)) {
                return true;
            }
        }
        return false;
    }

    /** パス → 中身。sp の下が変わっていないことを比べるための写し。 */
    private static Map<String, String> snapshot(String kind) {
        Map<String, String> map = new TreeMap<>();
        for (Path p : overworldFiles(kind)) {
            map.put(p.toString().replace('\\', '/'), read(p));
        }
        return map;
    }

    private static boolean waitFileIn(ClientGameTestContext context, String kind, String text) {
        for (int i = 0; i < 100; i++) {
            if (anyContains(overworldFiles(kind), text)) {
                return true;
            }
            context.waitTicks(1);
        }
        return anyContains(overworldFiles(kind), text);
    }

    /** 初回起動の JourneyMap の案内画面を閉じて、全画面マップを開く。 */
    private static void openMapScreen(ClientGameTestContext context) {
        context.runOnClient(client -> openFullscreen());
        context.waitTicks(40);
        for (int attempt = 0; attempt < 3; attempt++) {
            if (context.computeOnClient(MultiplayerClientGameTest::screenName).endsWith(".Fullscreen")) {
                break;
            }
            int w = context.computeOnClient(client -> client.getWindow().getWidth());
            int h = context.computeOnClient(client -> client.getWindow().getHeight());
            context.getInput().setCursorPos(w * 0.676, h * 0.9375);
            context.waitTicks(3);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(20);
        }
        if (!context.computeOnClient(MultiplayerClientGameTest::screenName).endsWith(".Fullscreen")) {
            context.runOnClient(client -> openFullscreen());
            context.waitTicks(30);
        }
        check(context.computeOnClient(MultiplayerClientGameTest::screenName).endsWith(".Fullscreen"),
                "全画面マップが開いている");
    }

    /** 単独で動かす入口（登録していない）。通常は AllClientGameTest が、シングルの試験の後に run を呼ぶ。 */
    @Override
    public void runTest(ClientGameTestContext context) {
        run(context);
    }

    /** タイトル画面（ワールドに入っていない状態）から始め、タイトル画面で終える。 */
    public static void run(ClientGameTestContext context) {
        Map<String, String> spBefore = snapshot(SINGLE_KIND);
        log("saves（シングル用）の mapscribble ファイル（開始時）: " + spBefore.keySet());
        List<Path> mpBefore = overworldFiles(MULTI_KIND);
        log("servers（マルチ用）の mapscribble ファイル（開始時）: " + mpBefore);

        var properties = new java.util.Properties();
        properties.setProperty("server-port", String.valueOf(freePort()));
        var server = context.worldBuilder().createServer(properties);
        try {
            var connection = server.connect();
            try {
                //? if >=26.2 {
                /*connection.waitForChunksRender();
                *///?} else {
                connection.getClientLevel().waitForChunksRender();
                //?}
                context.waitTicks(100);
                drawAndVerify(context, spBefore);
            } finally {
                disconnect(context, connection);
            }

            // 切断して接続し直す。同じメモが読み込まれる
            log("切断した。接続し直す");
            var again = server.connect();
            try {
                //? if >=26.2 {
                /*again.waitForChunksRender();
                *///?} else {
                again.getClientLevel().waitForChunksRender();
                //?}
                context.waitTicks(100);
                openMapScreen(context);
                context.waitFor(client -> StoreSession.repository().isAvailable(), TIMEOUT_TICKS);
                context.waitFor(client -> StoreSession.repository().memos().stream()
                        .anyMatch(m -> NOTE.equals(m.text())), TIMEOUT_TICKS);
                check(countWithText(context, NOTE) == 1, "接続し直すと、保存した「" + NOTE + "」のメモが 1 件読み込まれた");
                context.takeScreenshot("mapscribble-mp-reconnect");
            } finally {
                disconnect(context, again);
            }
        } finally {
            server.close();
            context.waitTicks(5);
            context.setScreen(TitleScreen::new);
        }
        log("マルチプレイの試験が最後まで通った");
    }

    /** 他のサーバー（開発用の 25565 等）と重ならない、空いているポートを1つ選ぶ。 */
    private static int freePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 画面を閉じてから切断し、クライアントがワールドを抜けるのを待つ。 */
    private static void disconnect(ClientGameTestContext context, AutoCloseable connection) {
        context.setScreen(() -> null);
        context.waitTicks(5);
        try {
            connection.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        context.waitFor(client -> client.level == null, TIMEOUT_TICKS);
        context.waitTicks(20);
    }

    private static void drawAndVerify(ClientGameTestContext context, Map<String, String> spBefore) {
        openMapScreen(context);
        context.waitFor(client -> StoreSession.repository().isAvailable(), TIMEOUT_TICKS);
        check(ScribbleSession.controller() != null, "コントローラが取り付いている");
        DrawingState state = ScribbleSession.state();
        double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        int guiW = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
        int guiH = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
        int width = context.computeOnClient(client -> client.getWindow().getWidth());
        int height = context.computeOnClient(client -> client.getWindow().getHeight());


        int n0 = context.computeOnClient(client -> StoreSession.repository().memos().size());
        log("描く前のメモ数 n0=" + n0 + " 内訳=" + context.computeOnClient(client -> StoreSession.repository().memos().stream()
                .map(m -> m.kind() + ":" + m.text()).toList()));
        context.runOnClient(client -> {
            state.setModeOn(true);
            state.setTool(Tool.ENCLOSE);
        });
        context.waitTicks(10);

        // 1. 手書きで囲む（一筆）
        double[][] path = {
                {width * 0.35, height * 0.65}, {width * 0.50, height * 0.35}, {width * 0.65, height * 0.65},
                {width * 0.50, height * 0.70}, {width * 0.36, height * 0.66}
        };
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
        var controller = ScribbleSession.controller();
        log("離す前: 一筆=" + (controller.activeStroke() == null ? "なし" : controller.activeStroke().points().size()
                + "点") + " モード=" + state.isModeOn() + " ツール=" + state.tool() + " isActive=" + controller.isActive()
                + " 画面=" + context.computeOnClient(MultiplayerClientGameTest::screenName));
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(15);
        log("離した後: 一筆=" + (controller.activeStroke() == null ? "なし" : "あり") + " メモ数="
                + context.computeOnClient(client -> StoreSession.repository().memos().size()) + " 画面="
                + context.computeOnClient(MultiplayerClientGameTest::screenName));
        check(context.computeOnClient(MultiplayerClientGameTest::screenName).endsWith(".NoteEditScreen"),
                "囲んだ後に文字入力の画面が開く（今: " + context.computeOnClient(MultiplayerClientGameTest::screenName) + "）");

        // 2. 文字を入れて確定する
        context.getInput().typeChars(NOTE);
        context.waitTicks(5);
        EditLayout layout = new EditLayout(MemoKind.REGION, false, guiH);
        double saveX = layout.panelX(guiW) + layout.saveX() + layout.footerButtonWidth() / 2.0;
        double saveY = layout.panelY() + layout.buttonsY() + EditLayout.BUTTON_HEIGHT / 2.0;
        clickGui(context, saveX, saveY, scale);
        context.waitTicks(10);
        check(context.computeOnClient(MultiplayerClientGameTest::screenName).endsWith(".Fullscreen"),
                "確定で全画面マップへ戻る");
        check(context.computeOnClient(client -> StoreSession.repository().memos().size()) == n0 + 1,
                "メモが 1 件増えた");
        check(countWithText(context, NOTE) == 1, "入れた文字「" + NOTE + "」のメモがある");

        // 3. 保存先: mapscribble/servers/<サーバーのフォルダ>/ の下に書かれる
        check(waitFileIn(context, MULTI_KIND, NOTE), "mapscribble/servers/<サーバー>/minecraft__overworld.json に「"
                + NOTE + "」が書き出された（" + overworldFiles(MULTI_KIND) + "）");
        Path servers = dataRoot().resolve(MULTI_KIND);
        for (Path p : overworldFiles(MULTI_KIND)) {
            if (read(p).contains(NOTE)) {
                log("書かれたファイル（実際）= " + p.toAbsolutePath());
                check(servers.equals(p.getParent().getParent()),
                        "サーバーごとのフォルダ（servers/<サーバー>/）の直下に書かれている: " + servers.relativize(p));
            }
        }

        // 4. シングル用（saves/）の下には書かれていない（内容も変わっていない）
        List<Path> spNow = overworldFiles(SINGLE_KIND);
        check(!anyContains(spNow, NOTE), "mapscribble/saves/ の下のどのファイルにも「" + NOTE + "」が無い");
        check(snapshot(SINGLE_KIND).equals(spBefore), "mapscribble/saves/ の下のファイルが、開始時から変わっていない");
        context.takeScreenshot("mapscribble-mp-end");
    }
}
