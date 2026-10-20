package io.github.radishsprouts.mapscribble.jm;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.HitTester;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.BandLayout;
import io.github.radishsprouts.mapscribble.ui.ScribbleUi;
import io.github.radishsprouts.mapscribble.ui.UiPrefs;
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
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.fullscreen.ThemeButtonDisplay;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * JourneyMap 連携の本体。入力（ドラッグ・クリック・離す・Ctrl+Z/Y）、全画面マップの自前の描画、
 * ミニマップのオーバーレイを、JourneyMap のプラグイン（{@link MapScribblePlugin}）から 1 か所で配線する。
 * このクラスの先は JourneyMap のプラグインクラスを参照しない。
 */
public final class ScribbleBridge {
    /** 左ボタン。26.3 は SDL のボタン番号で値が違うので、定数を使う。 */
    private static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final Identifier TOGGLE_ICON = GuiCompat.texture("textures/gui/toggle.png");

    private final IClientAPI api;
    private final Supplier<MemoRepository> repositorySupplier;
    private final DrawingController controller;
    private final DrawingState state;
    private final FullscreenRenderer renderer;
    private final MinimapOverlays minimap;
    private final ScribbleUi ui;

    /** 右列の「手書き」ボタン（全画面マップを開き直すたびに作り直される）。 */
    private IThemeButton toggleButton;

    // 消しゴム・編集でカーソルの下にあるメモの判定の使い回し（マウス・地図・メモが変わるまで同じ答え）
    private int hoverX = Integer.MIN_VALUE;
    private int hoverY = Integer.MIN_VALUE;
    private Projection hoverProjection;
    private List<Memo> hoverMemos;
    private Tool hoverTool;
    private Memo hoverResult;

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
        this.ui = new ScribbleUi(controller, FabricLoader.getInstance().getConfigDir().resolve(UiPrefs.FILE_NAME));
        // メモのファイルを読めなかった時は、帯の説明の行に出す
        StoreSession.setNotifier(message -> ui.onLoadFailed());
        // メモを保存できなかった時（と、その後に保存できた時）も、同じ行に出す・消す
        StoreSession.setSaveNotifier(ui::onSaveStateChanged);
    }

    /** イベントを購読する。 */
    public void register() {
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(MapScribble.MOD_ID, this::onRender);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(MapScribble.MOD_ID, this::onClick);
        FullscreenEventRegistry.FULLSCREEN_MAP_DRAG_EVENT.subscribe(MapScribble.MOD_ID, this::onDrag);
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            track(event.getFullscreen());
            addToggleButton(event.getThemeButtonDisplay());
        });

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

    /** 右列に「手書き」の切替ボタンを足す。ラベルは翻訳キー（オンの時は「終える」、オフの時は「にする」）。 */
    private void addToggleButton(ThemeButtonDisplay display) {
        toggleButton = display.addThemeToggleButton("mapscribble.button.toggle.on", "mapscribble.button.toggle.off",
                TOGGLE_ICON, state.isModeOn(), button -> {
                    state.toggleMode();
                    button.setToggled(state.isModeOn());
                });
    }

    /** キーなどでモードが変わった時に、ボタンの見た目を状態へ合わせる（比べるだけで軽い）。 */
    private void syncToggleButton() {
        IThemeButton button = toggleButton;
        if (button != null && button.getToggled() != null && button.getToggled() != state.isModeOn()) {
            button.setToggled(state.isModeOn());
        }
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
        ScreenMouseEvents.allowMouseClick(screen).register(this::allowMouseClick);
        ScreenMouseEvents.afterMouseRelease(screen).register(this::afterMouseRelease);
        ScreenKeyboardEvents.allowKeyPress(screen).register(this::allowKeyPress);
    }

    // ---- 描画 ----

    private void onRender(FullscreenRenderEvent event) {
        track(event.getFullscreen());
        renderer.render(event);
        syncToggleButton();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        ui.render(event.getGraphics(), mouseX, mouseY, hoverMemo(event.getFullscreen(), mouseX, mouseY));
    }

    /** 消しゴム・編集で、カーソルの下にあるメモ。条件が合わなければ null。同じ入力なら前の答えを使い回す。 */
    private Memo hoverMemo(IFullscreen fs, int guiX, int guiY) {
        Tool tool = state.tool();
        if (guiX < 0 || guiY < 0 || !controller.isActive() || !state.isVisible()
                || (tool != Tool.ERASER && tool != Tool.EDIT) || BandLayout.contains(guiX, guiY)) {
            return null;
        }
        MemoRepository repo = controller.repository();
        if (repo == null) {
            return null;
        }
        Projection projection = MapProjection.of(fs);
        List<Memo> memos = repo.memos();
        if (guiX == hoverX && guiY == hoverY && tool == hoverTool && memos == hoverMemos
                && projection.equals(hoverProjection)) {
            return hoverResult;
        }
        int guiScale = MapProjection.guiScale();
        double px = guiX * guiScale + guiScale / 2.0;
        double py = guiY * guiScale + guiScale / 2.0;
        hoverX = guiX;
        hoverY = guiY;
        hoverTool = tool;
        hoverMemos = memos;
        hoverProjection = projection;
        hoverResult = HitTester.find(memos, projection.toWorldX(px), projection.toWorldZ(py),
                projection.pixelsToWorld(1.0), guiScale, measure);
        return hoverResult;
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

    /**
     * 帯の上のクリックは、画面の段階で止めて地図（JourneyMap）へ渡さない。
     * 手書きモードがオンの間は、メモを使えない状態（地図の準備中など）でも帯のボタンは押せる。
     */
    private boolean allowMouseClick(Screen screen, MouseButtonEvent event) {
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen) {
            return true;
        }
        return !ui.onMouseClick(event.x(), event.y(), event.button() == MOUSE_LEFT);
    }

    private boolean afterMouseRelease(Screen screen, MouseButtonEvent event, boolean consumed) {
        IFullscreen fs = current();
        if (fs != null && fs.getScreen() == screen) {
            ui.onMouseRelease();
            if (event.button() == MOUSE_LEFT && controller.activeStroke() != null) {
                controller.finishStroke(MapProjection.of(fs));
            }
        }
        return consumed;
    }

    /** 全画面マップでのキー（切替・ツール・Ctrl+Z/Y）。文字の入力欄にフォーカスがある時は何も奪わない。 */
    private boolean allowKeyPress(Screen screen, KeyEvent event) {
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen || screen.getFocused() instanceof EditBox) {
            return true;
        }
        return !ui.onKey(event);
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
