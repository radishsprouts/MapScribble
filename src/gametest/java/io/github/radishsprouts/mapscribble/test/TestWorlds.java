package io.github.radishsprouts.mapscribble.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * gametest 用のワールドの開閉。
 *
 * <p>{@code TestSingleplayerContext.close()} は使わない。26.1.2 / 26.2 では、close が描画スレッドで
 * {@code IntegratedServer.halt} の完了を待ち、その間サーバーのスレッドは gametest の同期（Phaser）で
 * 描画スレッドを待つため、互いに待ち合って永久に止まる（スレッドダンプで確認。ワールドを開いてすぐ閉じるだけでも起きる）。
 * そこで、サーバー側のスレッドから {@code halt(false)} を呼んで先に止め、クライアントがワールドを抜けるのを待つ。
 */
@SuppressWarnings("UnstableApiUsage")
final class TestWorlds {
    private TestWorlds() {
    }

    /** 新しいシングルのワールドを作り、描画と少しの tick を待つ。閉じる時は {@link #close} を使う。 */
    static TestSingleplayerContext open(ClientGameTestContext context) {
        TestSingleplayerContext singleplayer = context.worldBuilder().create();
        //? if >=26.2 {
        /*singleplayer.getConnection().waitForChunksRender();
        *///?} else {
        singleplayer.getClientLevel().waitForChunksRender();
        //?}
        context.waitTicks(100);
        return singleplayer;
    }

    /** ワールドを閉じ、タイトル画面で終える（gametest の最後の状態の条件）。 */
    static void close(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        // 画面を開いたままだと描画側の処理が絡むので、先に閉じる
        context.setScreen(() -> null);
        context.waitTicks(5);
        singleplayer.getServer().runOnServer(server -> server.halt(false));
        context.waitFor(client -> client.level == null, 600);
        context.waitTicks(5);
        context.setScreen(TitleScreen::new);
    }
}
