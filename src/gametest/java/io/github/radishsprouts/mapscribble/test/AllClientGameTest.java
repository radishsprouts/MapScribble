package io.github.radishsprouts.mapscribble.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * gametest の入口は、これ1つだけ（src/gametest/resources/fabric.mod.json）。
 *
 * <p>最初に {@link MultiplayerClientGameTest}（専用サーバーに接続して、マルチ用の保存先を確かめる）を動かす。
 * 後ろの Drawing 試験が確定後の処理（文字入力の画面を開く）を外したまま戻さないので、文字入力の画面を使うマルチの試験と
 * 撮影の試験は、その前に置く。
 *
 * <p>続けてシングルのワールドを1回だけ作り、5つの試験を同じワールドで順に動かす（ワールドの開閉は {@link TestWorlds}）。
 * 順序: Flow（文字入力の画面を含む、囲む→文字→保存→消す→戻す）→ Ui（帯・入力画面・編集）→ Toggle
 * （右列の切替ボタンが1つだけ）→ Screenshot（見た目の確認用の撮影。判定はしない。GUI 倍率・画面の大きさ・言語を
 * 変えるが、終わると元へ戻す）→ Drawing（Drawing は確定後の処理を外すので最後）。期待と違えば
 * AssertionError で落ち、全部通れば「[MS-ALL] マルチと5つの試験（撮影を含む）が最後まで通った」を出す。
 */
@SuppressWarnings("UnstableApiUsage")
public class AllClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        MultiplayerClientGameTest.run(context);

        TestSingleplayerContext singleplayer = TestWorlds.open(context);

        try {
            FlowClientGameTest.run(context);
            closeScreen(context);
            UiClientGameTest.run(context);
            closeScreen(context);
            ToggleButtonClientGameTest.run(context);
            closeScreen(context);
            ScreenshotClientGameTest.run(context);
            closeScreen(context);
            DrawingClientGameTest.run(context);
            System.out.println("[MS-ALL] マルチと5つの試験（撮影を含む）が最後まで通った");
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
