package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.MapScribble;
import java.io.File;
import java.util.function.Consumer;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.event.ClientEventRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * JourneyMap のサーバー・ディメンションの切り替わりに、メモの手持ちを追従させる（04章）。
 * 全体で1つだけ持つ。呼び出しは主スレッドから。
 */
public final class StoreSession {
    private static final MemoStore STORE = new MemoStore();
    private static final SessionController CONTROLLER = new SessionController(STORE);
    private static IClientAPI api;
    private static boolean attached;

    private StoreSession() {
    }

    public static MemoRepository repository() {
        return STORE;
    }

    /** ファイルを読めなかった時などの通知先。画面の担当がつなぐ。 */
    public static void setNotifier(Consumer<String> notifier) {
        STORE.setProblemHandler(notifier);
    }

    /**
     * 保存に失敗した時に true、その後の保存が成功した時に false を渡す。呼ばれるのは Minecraft の主スレッド。
     * 書き込みは主スレッドの外で行うので、ここで主スレッドへ戻している。
     */
    public static void setSaveNotifier(Consumer<Boolean> notifier) {
        STORE.setSaveStateHandler(failed -> Minecraft.getInstance().execute(() -> notifier.accept(failed)));
    }

    /** JourneyMap プラグインの initialize から1回だけ呼ぶ。 */
    public static void attach(IClientAPI clientApi) {
        if (attached) {
            return;
        }
        attached = true;
        api = clientApi;

        ClientEventRegistry.MAPPING_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            switch (event.getStage()) {
                case MAPPING_STARTED -> CONTROLLER.started(keyOf(event.dimension, event.getWorldId()));
                case MAPPING_STOPPED -> CONTROLLER.stopped();
                default -> { }
            }
        });

        ClientEventRegistry.DISPLAY_UPDATE_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            // 全画面マップの時だけ比べ直す（Context の型は版で場所が違うので名前で見る）
            if ("Fullscreen".equals(event.uiState.ui.name())) {
                CONTROLLER.recheck(currentKey());
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CONTROLLER.recheck(currentKey()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CONTROLLER.flush());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CONTROLLER.flush();
            STORE.shutdown();
        });
    }

    private static SessionKey keyOf(ResourceKey<Level> dimension, String worldId) {
        if (dimension == null || api == null) {
            return null;
        }
        File dir = api.getDataPath(MapScribble.MOD_ID);
        if (dir == null) {
            return null;
        }
        return new SessionKey(dir.toPath(), worldId, dimension.identifier().toString());
    }

    /** 今の JourneyMap の値と今いるディメンションから鍵を作る。取れなければ null。 */
    private static SessionKey currentKey() {
        if (api == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        return keyOf(mc.level.dimension(), api.getWorldId());
    }
}
