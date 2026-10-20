package io.github.radishsprouts.mapscribble.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * gametest の入口は、これ1つだけ（src/gametest/resources/fabric.mod.json）。
 * ワールドを1回だけ作り、3つの試験を同じワールドで順に動かす（ワールドの開閉は {@link TestWorlds}）。
 *
 * <p>順序: Flow（文字入力の画面を含む、囲む→文字→保存→消す→戻す）→ Ui（帯・入力画面・編集）→ Drawing
 * （Drawing は確定後の処理を外すので最後）。期待と違えば AssertionError で落ち、全部通れば
 * 「[MS-ALL] 3つの試験が最後まで通った」を出す。
 */
@SuppressWarnings("UnstableApiUsage")
public class AllClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext singleplayer = TestWorlds.open(context);

        try {
            FlowClientGameTest.run(context);
            closeScreen(context);
            UiClientGameTest.run(context);
            closeScreen(context);
            DrawingClientGameTest.run(context);
            System.out.println("[MS-ALL] 3つの試験が最後まで通った");
        } finally {
            // 落ちた時もワールドを閉じて、gametest が止まらず終わるようにする
            TestWorlds.close(context, singleplayer);
        }
    }

    private static void closeScreen(ClientGameTestContext context) {
        context.setScreen(() -> null);
        context.waitTicks(20);
    }
}
