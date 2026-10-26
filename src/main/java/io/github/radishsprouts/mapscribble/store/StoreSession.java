package io.github.radishsprouts.mapscribble.store;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 地図MOD非依存のメモ保存セッション管理。
 * 保存先は .minecraft/mapscribble/saves/<ワールド名>/ または servers/<ホスト_ポート>/。
 * 旧 JourneyMap フォルダからの自動データ移行にも対応する。
 */
public final class StoreSession {
    private static final MemoStore STORE = new MemoStore();
    private static final SessionController CONTROLLER = new SessionController(STORE);
    /**
     * 読み込みに失敗した時の受け手。地図MODごとに画面の担当が別にいる（JourneyMap 用と Xaero/FTB 用）ので、
     * 1 つに上書きせず全部へ届ける。
     */
    private static final List<Consumer<String>> PROBLEM_NOTIFIERS = new CopyOnWriteArrayList<>();
    /** 保存の失敗・回復の受け手。理由は上と同じ。 */
    private static final List<Consumer<Boolean>> SAVE_NOTIFIERS = new CopyOnWriteArrayList<>();

    /** 毎 tick の呼び出しを安く弾くための、前回の入力。 */
    private static ResourceKey<Level> lastDimension;
    private static Object lastConnection;

    static {
        STORE.setProblemHandler(message -> {
            for (Consumer<String> notifier : PROBLEM_NOTIFIERS) {
                notifier.accept(message);
            }
        });
        // 書き込みは主スレッドの外で行うので、ここで主スレッドへ戻す
        STORE.setSaveStateHandler(failed -> Minecraft.getInstance().execute(() -> {
            for (Consumer<Boolean> notifier : SAVE_NOTIFIERS) {
                notifier.accept(failed);
            }
        }));
    }

    private StoreSession() {
    }

    public static MemoRepository repository() {
        return STORE;
    }

    /** ファイルを読めなかった時の通知先を足す。画面の担当ごとに呼ぶ（後から呼んでも前の受け手は消えない）。 */
    public static void addNotifier(Consumer<String> notifier) {
        if (notifier != null) {
            PROBLEM_NOTIFIERS.add(notifier);
        }
    }

    /**
     * 保存に失敗した時に true、その後の保存が成功した時に false を渡す受け手を足す。
     * 呼ばれるのは Minecraft の主スレッド。
     */
    public static void addSaveNotifier(Consumer<Boolean> notifier) {
        if (notifier != null) {
            SAVE_NOTIFIERS.add(notifier);
        }
    }

    /**
     * ディメンション切り替え時・接続時、そしてクライアントの毎 tick（20 回/秒）から呼ぶ。
     * 前回と同じディメンション・同じ接続先なら、鍵も組み立てずに返る。
     * ここで弾かないと毎 tick メモのファイルを読み直し、メモの実体が入れ替わって描画側のキャッシュが外れる。
     */
    public static void updateDimension(ResourceKey<Level> dimension) {
        if (dimension == null) {
            lastDimension = null;
            lastConnection = null;
            CONTROLLER.update(null, null);
            return;
        }
        Object connection = currentConnection();
        if (dimension.equals(lastDimension) && connection == lastConnection && CONTROLLER.current() != null) {
            return;
        }
        lastDimension = dimension;
        lastConnection = connection;
        // 鍵の組み立てと、古いデータの移行の確認（ファイルシステムへの問い合わせ）は、ここから先でしか走らない
        CONTROLLER.update(createKey(dimension),
                key -> migrateFromJourneyMapIfNeeded(key.dataPath(), key.dimension()));
    }

    /** 今つないでいる先。切り替わりの検出にしか使わないので、型は見ない（同じ実体のままかだけを見る）。 */
    private static Object currentConnection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            return mc.getSingleplayerServer();
        }
        return mc.getCurrentServer();
    }

    public static void flush() {
        CONTROLLER.flush();
    }

    public static void shutdown() {
        CONTROLLER.flush();
        STORE.shutdown();
    }

    private static SessionKey createKey(ResourceKey<Level> dimension) {
        Minecraft mc = Minecraft.getInstance();
        Path baseDir = ScreenCompat.gameDirectory().resolve("mapscribble");
        String worldFolder;
        try {
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                worldFolder = "saves/" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).getParent().getFileName().toString();
            } else if (mc.getCurrentServer() != null) {
                String ip = mc.getCurrentServer().ip.replace(':', '_').replaceAll("[^a-zA-Z0-9._-]", "_");
                worldFolder = "servers/" + ip;
            } else {
                worldFolder = "saves/unknown";
            }
        } catch (Exception e) {
            worldFolder = "saves/unknown";
        }

        Path saveDir = baseDir.resolve(worldFolder);
        String dimId = ScreenCompat.dimensionId(dimension);
        // 旧 JourneyMap データからの移行は、鍵が変わった時だけ（updateDimension から開く直前に）行う
        return new SessionKey(saveDir, worldFolder, dimId);
    }

    private static void migrateFromJourneyMapIfNeeded(Path targetDir, String dimId) {
        try {
            Path jmAddonDir = ScreenCompat.gameDirectory().resolve("journeymap").resolve("addon-data").resolve(MapScribble.MOD_ID);
            if (!Files.exists(jmAddonDir)) {
                return;
            }
            String fileName = MemoFile.fileName(dimId);
            Path oldFile = jmAddonDir.resolve(fileName);
            Path newFile = targetDir.resolve(fileName);
            if (Files.exists(oldFile) && !Files.exists(newFile)) {
                Files.createDirectories(targetDir);
                Files.copy(oldFile, newFile, StandardCopyOption.COPY_ATTRIBUTES);
                MapScribble.LOGGER.info("Migrated legacy MapScribble note from {} to {}", oldFile, newFile);
            }
        } catch (Exception e) {
            MapScribble.LOGGER.warn("Failed to check or migrate legacy notes: {}", e.getMessage());
        }
    }
}
