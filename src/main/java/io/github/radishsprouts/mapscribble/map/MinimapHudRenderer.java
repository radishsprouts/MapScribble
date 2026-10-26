package io.github.radishsprouts.mapscribble.map;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.MapCanvasRenderer;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.geom.ClipRegion;
import io.github.radishsprouts.mapscribble.geom.MapFrame;
import io.github.radishsprouts.mapscribble.geom.MinimapGeometry;
import io.github.radishsprouts.mapscribble.geom.MinimapSource;
import io.github.radishsprouts.mapscribble.map.xaero.XaeroMinimapSource;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.util.List;
import net.minecraft.client.Minecraft;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Xaero's Minimap のミニマップの枠の中へ、手書きメモを重ねて描く。
 *
 * <p>枠の位置・大きさ・ズーム・回転・形は {@link MinimapSource} が Xaero から取り、
 * ここは「描くか決める → 幾何を取る → 枠の内側へ切り抜く → 描く」という毎フレームの流れだけを持つ。
 * 幾何が取れない（Xaero が入っていない・無効・隠れている・取得経路が変わった）ときは、
 * 何も描かずに戻るだけで、例外を投げ上げない。
 *
 * <p>FTB Chunks / JourneyMap のミニマップには対応していない（全画面マップのみ）。
 */
public final class MinimapHudRenderer {
    /** ミニマップ専用の描画エンジン。全画面マップ（{@link ScreenMapHost}）とはキャッシュを分ける。 */
    private static MapCanvasRenderer canvasRenderer;
    private static final MinimapSource SOURCE = new XaeroMinimapSource();

    /** 描画中の例外をログに出す回数の上限（毎フレーム出さないため）。 */
    private static final int MAX_ERROR_LOGS = 3;
    private static int errorLogs;

    private MinimapHudRenderer() {
    }

    public static synchronized void init(DrawingController controller) {
        if (canvasRenderer == null) {
            canvasRenderer = new MapCanvasRenderer(controller);
        }
    }

    //? if >=26.1 {
    public static void render(GuiGraphicsExtractor gfx) {
    //?} else {
    /*public static void render(GuiGraphics gfx) {
    *///?}
        render(gfx, GuiCompat.partialTicks());
    }

    //? if >=26.1 {
    public static void render(GuiGraphicsExtractor gfx, float partialTicks) {
    //?} else {
    /*public static void render(GuiGraphics gfx, float partialTicks) {
    *///?}
        Minecraft mc = Minecraft.getInstance();
        if (ScreenCompat.currentScreen() != null || mc.level == null || mc.player == null) {
            return;
        }
        if (canvasRenderer == null) {
            return;
        }

        DrawingController controller = ScribbleSession.controller();
        if (controller == null || !controller.state().isVisible()) {
            return;
        }

        MemoRepository repo = controller.repository();
        if (repo == null || !repo.isAvailable()) {
            return;
        }

        List<Memo> memos = repo.memos();
        if (memos.isEmpty()) {
            return;
        }

        MinimapGeometry geometry = SOURCE.geometry(partialTicks);
        if (geometry == null) {
            return;
        }
        // Xaero が別の次元の地図を出している間は、地形と合わない位置に出るので描かない
        Object mapDimension = geometry.dimension();
        if (mapDimension != null && !mapDimension.equals(mc.level.dimension())) {
            return;
        }

        int guiScale = (int) Math.max(1.0, mc.getWindow().getGuiScale());
        ClipRegion clip = ClipRegion.of(geometry);
        MapFrame frame = MapFrame.minimap(geometry, guiScale, mc.level.dimension());

        // scissor の引数は GUI 座標。縁のメモが欠けないよう外側へ丸める
        int sx0 = (int) Math.floor(clip.minX() / guiScale);
        int sy0 = (int) Math.floor(clip.minY() / guiScale);
        int sx1 = (int) Math.ceil(clip.maxX() / guiScale);
        int sy1 = (int) Math.ceil(clip.maxY() / guiScale);
        if (sx1 <= sx0 || sy1 <= sy0) {
            return;
        }

        // 例外が出ても scissor を必ず戻す（残ると HUD 全体が切り抜かれたままになる）
        GuiCompat.enableScissor(gfx, sx0, sy0, sx1, sy1);
        try {
            canvasRenderer.render(gfx, frame);
        } catch (Throwable t) {
            logErrorOnce(t);
        } finally {
            GuiCompat.disableScissor(gfx);
        }
    }

    /** 描画中の例外は最初の数回だけ記録する（毎フレーム出るとログが埋まる）。 */
    private static void logErrorOnce(Throwable t) {
        if (errorLogs >= MAX_ERROR_LOGS) {
            return;
        }
        errorLogs++;
        MapScribble.LOGGER.warn("[MapScribble] ミニマップへの手書きメモの描画に失敗しました（{}/{} 回目）",
                errorLogs, MAX_ERROR_LOGS, t);
    }
}
