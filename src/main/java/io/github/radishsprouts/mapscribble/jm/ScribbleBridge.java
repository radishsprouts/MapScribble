package io.github.radishsprouts.mapscribble.jm;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.HitTester;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * JourneyMap 連携の本体。入力（ドラッグ・クリック・離す・Ctrl+Z/Y）、全画面マップの自前の描画、
 * ミニマップのオーバーレイを、JourneyMap のプラグイン（{@link MapScribblePlugin}）から 1 か所で配線する。
 * このクラスの先は JourneyMap のプラグインクラスを参照しない。
 */
public final class ScribbleBridge {
    /** GLFW のキー（Z・Y）。 */
    private static final int KEY_Z = 90;
    private static final int KEY_Y = 89;
    private static final int MOUSE_LEFT = 0;

    private final IClientAPI api;
    private final Supplier<MemoRepository> repositorySupplier;
    private final DrawingController controller;
    private final DrawingState state;
    private final FullscreenRenderer renderer;
    private final MinimapOverlays minimap;

    /** 今開いている JourneyMap の全画面マップ（開いていなければ null）。 */
    private IFullscreen fullscreen;
    /** 変更の通知を登録済みの窓口。 */
    private final Set<MemoRepository> listened = Collections.newSetFromMap(new IdentityHashMap<>());
    private MemoRepository lastRepository;

    private final HitTester.TextMeasure measure = new HitTester.TextMeasure() {
        @Override
        public int width(String line) {
            return Minecraft.getInstance().font.width(line);
        }

        @Override
        public int lineHeight() {
            return Minecraft.getInstance().font.lineHeight;
        }
    };

    public ScribbleBridge(IClientAPI api, Supplier<MemoRepository> repositorySupplier) {
        this.api = api;
        this.repositorySupplier = repositorySupplier;
        this.controller = ScribbleSession.install(repositorySupplier);
        this.state = controller.state();
        this.renderer = new FullscreenRenderer(controller);
        this.minimap = new MinimapOverlays(api);
    }

    /** イベントを購読する。 */
    public void register() {
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(MapScribble.MOD_ID, this::onRender);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(MapScribble.MOD_ID, this::onClick);
        FullscreenEventRegistry.FULLSCREEN_MAP_DRAG_EVENT.subscribe(MapScribble.MOD_ID, this::onDrag);
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MapScribble.MOD_ID,
                event -> track(event.getFullscreen()));

        // 画面ごとの離す・キーのイベントは、画面の初期化の後に登録する（全画面マップかどうかは実行時に同一性で見る）
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> hook(screen));

        // ミニマップの同期。窓口の切り替わり・ディメンションの切り替わりを毎 tick の比較で拾う（比較だけで軽い）
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        state.addChangeListener(this::syncMinimap);
    }

    // ---- 状態の追跡 ----

    private void track(IFullscreen fs) {
        this.fullscreen = fs;
    }

    /** 今開いている全画面マップ。別の画面に切り替わっていれば null。 */
    private IFullscreen current() {
        IFullscreen fs = fullscreen;
        if (fs == null) {
            return null;
        }
        //? if >=26.2 {
        /*Screen shown = Minecraft.getInstance().gui.screen();
        *///?} else {
        Screen shown = Minecraft.getInstance().screen;
        //?}
        if (shown != fs.getScreen()) {
            fullscreen = null;
            controller.cancelStroke();
            return null;
        }
        return fs;
    }

    /**
     * 離す・キーのイベントを画面へ登録する。Fabric は画面が（再）初期化されるたびに、画面ごとのイベントを
     * 初期状態へ戻す（ウィンドウのサイズ変更や、JourneyMap の別の画面から戻った時など）。
     * そのため重複を恐れず、初期化のたびに登録し直す（実機で、登録を1回で済ませると離すイベントが来なくなることを確認した）。
     */
    private void hook(Screen screen) {
        if (screen == null) {
            return;
        }
        ScreenMouseEvents.afterMouseRelease(screen).register(this::afterMouseRelease);
        ScreenKeyboardEvents.allowKeyPress(screen).register(this::allowKeyPress);
    }

    // ---- 描画 ----

    private void onRender(FullscreenRenderEvent event) {
        track(event.getFullscreen());
        renderer.render(event);
    }

    // ---- 入力 ----

    private void onClick(FullscreenMapEvent.ClickEvent event) {
        if (event.getStage() != FullscreenMapEvent.Stage.PRE) {
            return;
        }
        IFullscreen fs = current();
        if (fs == null || !controller.isActive()) {
            return;
        }
        Point2D.Double pos = event.getMousePosition();
        int guiScale = MapProjection.guiScale();
        if (state.isOverBand(pos.x / guiScale, pos.y / guiScale)) {
            cancel(event);
            return;
        }
        if (event.getButton() != MOUSE_LEFT) {
            return;
        }
        Projection projection = MapProjection.of(fs);
        Tool tool = state.tool();
        switch (tool) {
            case ENCLOSE:
            case LINE:
                // 一筆の始点。ドラッグが来なければ、離した時に「小さすぎる」として捨てられる
                controller.beginStroke(pos.x, pos.y, projection);
                cancel(event);
                break;
            case TEXT:
                controller.placeText(projection.toWorldX(pos.x), projection.toWorldZ(pos.y));
                cancel(event);
                break;
            case ERASER:
            case EDIT:
                pick(tool, projection, pos);
                cancel(event);
                break;
            default:
                break;
        }
    }

    private void pick(Tool tool, Projection projection, Point2D.Double pos) {
        if (!state.isVisible()) {
            state.notice(DrawingController.NOTICE_HIDDEN_PICK);
            return;
        }
        MemoRepository repo = controller.repository();
        if (repo == null) {
            return;
        }
        List<Memo> memos = repo.memos();
        Memo hit = HitTester.find(memos, projection.toWorldX(pos.x), projection.toWorldZ(pos.y),
                projection.pixelsToWorld(1.0), MapProjection.guiScale(), measure);
        if (tool == Tool.ERASER) {
            controller.erase(hit);
        } else {
            controller.requestEdit(hit);
        }
    }

    private void onDrag(FullscreenMapEvent.MouseDraggedEvent event) {
        if (event.getStage() != FullscreenMapEvent.Stage.PRE) {
            return;
        }
        IFullscreen fs = current();
        if (fs == null || !controller.isActive()) {
            return;
        }
        Point2D.Double pos = event.getMousePosition();
        int guiScale = MapProjection.guiScale();
        if (state.isOverBand(pos.x / guiScale, pos.y / guiScale)) {
            cancel(event);
            return;
        }
        if (!state.tool().isStrokeTool()) {
            return;
        }
        // 手書きモード中は最初から毎回取り消す（地図を動かさない）
        cancel(event);
        controller.extendStroke(pos.x, pos.y, MapProjection.of(fs));
    }

    private boolean afterMouseRelease(Screen screen, MouseButtonEvent event, boolean consumed) {
        IFullscreen fs = current();
        if (fs != null && fs.getScreen() == screen && event.button() == MOUSE_LEFT
                && controller.activeStroke() != null) {
            controller.finishStroke(MapProjection.of(fs));
        }
        return consumed;
    }

    private boolean allowKeyPress(Screen screen, KeyEvent event) {
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen || !controller.isActive() || !event.hasControlDown()) {
            return true;
        }
        if (event.key() == KEY_Z) {
            if (event.hasShiftDown()) {
                controller.redo();
            } else {
                controller.undo();
            }
            return false;
        }
        if (event.key() == KEY_Y) {
            controller.redo();
            return false;
        }
        return true;
    }

    private static void cancel(FullscreenMapEvent.ClickEvent event) {
        if (event.isCancellable()) {
            event.cancel();
        }
    }

    private static void cancel(FullscreenMapEvent.MouseDraggedEvent event) {
        if (event.isCancellable()) {
            event.cancel();
        }
    }

    // ---- ミニマップの同期 ----

    private void tick() {
        MemoRepository repo = repositorySupplier.get();
        if (repo != lastRepository) {
            lastRepository = repo;
            if (repo != null && listened.add(repo)) {
                repo.addListener(this::syncMinimap);
            }
            syncMinimap();
        }
        ResourceKey<Level> dim = currentDimension();
        if (dim != minimapDimension) {
            syncMinimap();
        }
    }

    private ResourceKey<Level> minimapDimension;

    private static ResourceKey<Level> currentDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.dimension();
    }

    private void syncMinimap() {
        MemoRepository repo = controller.repository();
        ResourceKey<Level> dim = currentDimension();
        List<Memo> memos = repo == null || !state.isVisible() ? List.of() : repo.memos();
        minimapDimension = dim;
        if (minimap.sync(memos, dim)) {
            controller.onDimensionChanged();
        }
    }
}
