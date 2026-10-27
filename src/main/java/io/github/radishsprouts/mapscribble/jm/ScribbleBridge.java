package io.github.radishsprouts.mapscribble.jm;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.HitTester;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.map.MidDragHandler;
import io.github.radishsprouts.mapscribble.map.PrimaryMapBridge;
import io.github.radishsprouts.mapscribble.map.ScreenMapHost;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.BandLayout;
import io.github.radishsprouts.mapscribble.ui.ScribbleUi;
import io.github.radishsprouts.mapscribble.ui.UiPrefs;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.event.FullscreenMapEvent;
//? if >=26.1 {
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.fullscreen.ThemeButtonDisplay;
import journeymap.api.v2.common.event.FullscreenEventRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * JourneyMap 連携の本体。入力（ドラッグ・クリック・離す・Ctrl+Z/Y）、全画面マップの自前の描画、
 * ミニマップのオーバーレイを、JourneyMap のプラグイン（{@link MapScribblePlugin}）から 1 か所で配線する。
 */
public final class ScribbleBridge implements PrimaryMapBridge {
    private static ScribbleBridge INSTANCE;

    /** 左ボタン。26.3 は SDL のボタン番号で値が違うので、定数を使う。 */
    private static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    //? if >=1.21.11 {
    private static final Identifier TOGGLE_ICON = GuiCompat.texture("textures/gui/toggle.png");
    //?} else {
    /*private static final ResourceLocation TOGGLE_ICON = GuiCompat.texture("textures/gui/toggle.png");
    *///?}

    private final IClientAPI api;
    private final Supplier<MemoRepository> repositorySupplier;
    private final DrawingController controller;
    private final DrawingState state;
    private final FullscreenRenderer renderer;
    private final MinimapOverlays minimap;
    private final ScribbleUi ui;

    /** 右列の「手書き」ボタン（全画面マップを開き直すたびに作り直される）。 */
    private IThemeButton toggleButton;
    /** 新しく足した切替ボタンが、画面の部品の一覧に二重に入っていないかを、まだ見ていない。 */
    private boolean toggleDedupePending;
    /** Screen の描画する部品の一覧（private の renderables）。取れなければ null（一度だけ警告して諦める）。 */
    private Field renderablesField;
    private boolean renderablesFieldTried;

    // 消しゴム・編集でカーソルの下にあるメモの判定の使い回し（マウス・地図・メモが変わるまで同じ答え）
    private int hoverX = Integer.MIN_VALUE;
    private int hoverY = Integer.MIN_VALUE;
    private Projection hoverProjection;
    private List<Memo> hoverMemos;
    private Tool hoverTool;
    private boolean hoverTextVisible = true;
    private Memo hoverResult;

    /** 今開いている JourneyMap の全画面マップ（開いていなければ null）。 */
    private IFullscreen fullscreen;
    /** 今の一筆の間に、JourneyMap のドラッグ通知が届いた回数（原因の切り分けのため。一筆の開始で 0 に戻す）。 */
    private int dragEventsInStroke;
    /** 今の一筆の間に、描画ごとのカーソル位置で一筆を進めた回数。 */
    private int pumpedFramesInStroke;
    /** ドラッグ通知が届かなかった事実を、ログへ一度だけ書いたか。 */
    private boolean warnedNoDragEvents;
    /** 離した通知が届かなかった事実を、ログへ一度だけ書いたか。 */
    private boolean warnedLostRelease;

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
        INSTANCE = this;
        this.api = api;
        this.repositorySupplier = repositorySupplier;
        // MOD の初期化で作られたものを使い回す（作り直すと Xaero・FTB の画面と一筆・履歴が分かれる）
        this.controller = ScribbleSession.install(repositorySupplier);
        this.state = controller.state();
        this.renderer = new FullscreenRenderer(controller);
        this.minimap = new MinimapOverlays(api);
        this.ui = new ScribbleUi(controller, ScreenCompat.gameDirectory().resolve("config").resolve(UiPrefs.FILE_NAME));
        // メモのファイルを読めなかった時は、帯の説明の行に出す（他の画面の受け手は消さずに足す）
        StoreSession.addNotifier(message -> ui.onLoadFailed());
        // メモを保存できなかった時（と、その後に保存できた時）も、同じ行に出す・消す
        StoreSession.addSaveNotifier(ui::onSaveStateChanged);

        // ScreenMapHost にプライマリブリッジとして登録
        ScreenMapHost.setPrimaryBridge(this);
    }

    public static ScribbleBridge instance() {
        return INSTANCE;
    }

    /** イベントを購読する。 */
    public void register() {
        //? if >=26.1 {
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(MapScribble.MOD_ID, this::onRender);
        //?}
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(MapScribble.MOD_ID, this::onClick);
        FullscreenEventRegistry.FULLSCREEN_MAP_DRAG_EVENT.subscribe(MapScribble.MOD_ID, this::onDrag);
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            track(event.getFullscreen());
            addToggleButton(event.getThemeButtonDisplay());
        });

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
        toggleDedupePending = true;
    }

    private void dedupeToggleButton(Screen screen) {
        IThemeButton button = toggleButton;
        if (!toggleDedupePending || button == null || screen == null) {
            return;
        }
        toggleDedupePending = false;
        removeExtra(screen.children(), button);
        List<?> renderables = renderablesOf(screen);
        if (renderables != null) {
            removeExtra(renderables, button);
        }
    }

    /** 一覧の中の同じ実体を、最初の1つだけ残して取り除く。 */
    private static void removeExtra(List<?> parts, Object target) {
        boolean seen = false;
        Iterator<?> it = parts.iterator();
        while (it.hasNext()) {
            if (it.next() == target) {
                if (seen) {
                    it.remove();
                }
                seen = true;
            }
        }
    }

    private List<?> renderablesOf(Screen screen) {
        if (!renderablesFieldTried) {
            renderablesFieldTried = true;
            String[] candidateNames = {"renderables", "field_33816", "f_169369_"};
            for (String name : candidateNames) {
                try {
                    Field field = Screen.class.getDeclaredField(name);
                    field.setAccessible(true);
                    renderablesField = field;
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (renderablesField == null) {
                MapScribble.LOGGER.warn("Screen.renderables を読めないため、切替ボタンの二重登録を直せません");
            }
        }
        if (renderablesField == null) {
            return null;
        }
        try {
            return (List<?>) renderablesField.get(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
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
        Screen shown = ScreenCompat.currentScreen();
        if (shown != fs.getScreen()) {
            fullscreen = null;
            controller.cancelStroke();
            MidDragHandler.reset();
            return null;
        }
        return fs;
    }

    @Override
    public boolean handles(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (fullscreen != null && fullscreen.getScreen() == screen) {
            return true;
        }
        return screen instanceof IFullscreen;
    }

    @Override
    public void onScreenInit(Screen screen) {
        if (screen instanceof IFullscreen fs) {
            track(fs);
        }
        IFullscreen fs = fullscreen;
        if (fs != null && fs.getScreen() == screen) {
            dedupeToggleButton(screen);
        }
    }

    @Override
    public void onScreenClose(Screen screen) {
        IFullscreen fs = fullscreen;
        if (fs != null && fs.getScreen() == screen) {
            controller.cancelStroke();
            MidDragHandler.reset();
            fullscreen = null;
        }
    }

    // ---- 描画 ----

    //? if >=26.1 {
    private void onRender(FullscreenRenderEvent event) {
        track(event.getFullscreen());
        dedupeToggleButton(event.getFullscreen().getScreen());
        pumpStroke(event.getFullscreen());
        renderer.render(event);
        syncToggleButton();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        ui.render(event.getGraphics(), mouseX, mouseY, hoverMemo(event.getFullscreen(), mouseX, mouseY));

        // 中ボタンドラッグ中のスクロール追従
        if (MidDragHandler.isDragging()) {
            IFullscreen fs = event.getFullscreen();
            Projection projection = MapProjection.of(fs);
            int guiScale = MapProjection.guiScale();
            MidDragHandler.onMouseMove(mouseX * guiScale, mouseY * guiScale, (dx, dy) -> {
                double worldDx = projection.pixelsToWorld(dx);
                double worldDy = projection.pixelsToWorld(dy);
                fs.centerOn(MapProjection.getCenterBlockX(fs) - worldDx, MapProjection.getCenterBlockZ(fs) - worldDy);
            });
        }
    }
    //?}

    //? if >=26.1 {
    @Override
    public void onScreenRender(GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY) {
    //?} else {
    /*@Override
    public void onScreenRender(GuiGraphics gfx, Screen screen, int mouseX, int mouseY) {
    *///?}
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen) {
            return;
        }
        pumpStroke(fs);
        //? if <26.1 {
        /*dedupeToggleButton(screen);
        renderer.render(gfx, fs);
        syncToggleButton();
        ui.render(gfx, mouseX, mouseY, hoverMemo(fs, mouseX, mouseY));

        // 中ボタンドラッグ中のスクロール追従
        if (MidDragHandler.isDragging()) {
            Projection projection = MapProjection.of(fs);
            int guiScale = MapProjection.guiScale();
            MidDragHandler.onMouseMove(mouseX * guiScale, mouseY * guiScale, (dx, dy) -> {
                double worldDx = projection.pixelsToWorld(dx);
                double worldDy = projection.pixelsToWorld(dy);
                fs.centerOn(MapProjection.getCenterBlockX(fs) - worldDx, MapProjection.getCenterBlockZ(fs) - worldDy);
            });
        }
        *///?}
    }

    /**
     * 一筆の最中、描画のたびにカーソルの今の位置で一筆を進める。
     * JourneyMap のドラッグ通知（FULLSCREEN_MAP_DRAG_EVENT）は、JourneyMap の内部の状態によっては届かなくなり、
     * 押した瞬間だけ描けて一筆が進まず確定もしないことがあった。通知に頼らず、ゲームが持つカーソル位置から進める。
     * 左ボタンを離した通知が届かなかった時も、OS のウィンドウから押下状態を読んで一筆を確定する（26.3 は読めないので何もしない）。
     */
    private void pumpStroke(IFullscreen fs) {
        if (controller.activeStroke() == null) {
            return;
        }
        Projection projection = MapProjection.of(fs);
        double physX = ScreenCompat.mousePhysicalX();
        double physY = ScreenCompat.mousePhysicalY();
        int guiScale = MapProjection.guiScale();
        // ドラッグ通知と同じく、帯の上では一筆を進めない
        if (!state.isOverBand(physX / guiScale, physY / guiScale)) {
            controller.extendStroke(physX, physY, projection);
        }
        pumpedFramesInStroke++;

        Boolean down = ScreenCompat.isLeftMouseDown();
        if (down != null && !down) {
            if (!warnedLostRelease) {
                warnedLostRelease = true;
                MapScribble.LOGGER.warn("左ボタンを離した通知が届かないまま一筆が残っていたので、描画の時点で確定しました"
                        + "（ドラッグ通知 {} 回、描画で進めた回数 {}）", dragEventsInStroke, pumpedFramesInStroke);
            }
            finishPumpedStroke(projection);
        }
    }

    /** 一筆を確定する。ドラッグ通知が一度も届かず描画で補っていたなら、その事実をログへ一度だけ残す。 */
    private void finishPumpedStroke(Projection projection) {
        if (dragEventsInStroke == 0 && pumpedFramesInStroke > 10 && !warnedNoDragEvents) {
            warnedNoDragEvents = true;
            MapScribble.LOGGER.warn("JourneyMap のドラッグ通知が一度も届かなかったので、描画のたびのカーソル位置で一筆を進めました"
                    + "（描画で進めた回数 {}）", pumpedFramesInStroke);
        }
        controller.finishStroke(projection);
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
        boolean textVisible = state.isTextVisible();
        if (guiX == hoverX && guiY == hoverY && tool == hoverTool && memos == hoverMemos
                && textVisible == hoverTextVisible && projection.equals(hoverProjection)) {
            return hoverResult;
        }
        int guiScale = MapProjection.guiScale();
        double px = guiX * guiScale + guiScale / 2.0;
        double py = guiY * guiScale + guiScale / 2.0;
        hoverX = guiX;
        hoverY = guiY;
        hoverTool = tool;
        hoverMemos = memos;
        hoverTextVisible = textVisible;
        hoverProjection = projection;
        hoverResult = HitTester.find(memos, projection.toWorldX(px), projection.toWorldZ(py),
                projection.pixelsToWorld(1.0), guiScale, measure, textVisible);
        return hoverResult;
    }

    // ---- 入力 ----

    private void onClick(FullscreenMapEvent.ClickEvent event) {
        if (event.getStage() != FullscreenMapEvent.Stage.PRE) {
            return;
        }
        IFullscreen fs = current();
        if (fs == null) {
            return;
        }
        Point2D.Double pos = event.getMousePosition();

        // ミドルボタン押下でドラッグスクロール開始（手書きモードON/OFF問わず動作）
        if (event.getButton() == 2) {
            MidDragHandler.onMouseDown(2, pos.x, pos.y);
            cancel(event);
            return;
        }

        if (!controller.isActive()) {
            return;
        }

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
                // 前の一筆が離す通知を受け取れずに残っていたら、先に確定する
                if (controller.activeStroke() != null) {
                    controller.finishStroke(projection);
                }
                releaseScreenInputState(fs.getScreen());
                dragEventsInStroke = 0;
                pumpedFramesInStroke = 0;
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

    /**
     * 一筆を始める時に、画面が握っている入力欄のフォーカスとドラッグ中の印を外す。
     * 見えない入力欄などが握ったままだと、以後のドラッグがそちらへ流れ、地図側のドラッグ通知が出なくなる疑いがある。
     */
    private static void releaseScreenInputState(Screen screen) {
        if (screen == null) {
            return;
        }
        screen.setFocused(null);
        screen.setDragging(false);
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
                projection.pixelsToWorld(1.0), MapProjection.guiScale(), measure, state.isTextVisible());
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
        if (fs == null) {
            return;
        }
        Point2D.Double pos = event.getMousePosition();

        // ミドルボタン押下中ドラッグによるマップスクロール（パン）
        if (event.getButton() == 2 || MidDragHandler.isDragging()) {
            cancel(event);
            Projection projection = MapProjection.of(fs);
            MidDragHandler.onMouseMove(pos.x, pos.y, (dx, dy) -> {
                double worldDx = projection.pixelsToWorld(dx);
                double worldDy = projection.pixelsToWorld(dy);
                fs.centerOn(MapProjection.getCenterBlockX(fs) - worldDx, MapProjection.getCenterBlockZ(fs) - worldDy);
            });
            return;
        }

        if (!controller.isActive()) {
            return;
        }

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
        dragEventsInStroke++;
        controller.extendStroke(pos.x, pos.y, MapProjection.of(fs));
    }

    @Override
    public boolean onMouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen) {
            return false;
        }
        int guiScale = MapProjection.guiScale();
        if (button == 2) {
            MidDragHandler.onMouseDown(2, mouseX * guiScale, mouseY * guiScale);
            return true;
        }
        return ui.onMouseClick(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseReleased(Screen screen, double mouseX, double mouseY, int button) {
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen) {
            return false;
        }
        ui.onMouseRelease(mouseX, mouseY, button);
        if (button == 2) {
            MidDragHandler.onMouseUp(2);
            return true;
        }
        if (button == MOUSE_LEFT && controller.activeStroke() != null) {
            Projection projection = MapProjection.of(fs);
            // 離した位置まで一筆を伸ばしてから確定する
            int guiScale = MapProjection.guiScale();
            controller.extendStroke(mouseX * guiScale, mouseY * guiScale, projection);
            finishPumpedStroke(projection);
        }
        return true;
    }

    //? if >=1.21.11 {
    /** 全画面マップでのキー（切替・ツール・Ctrl+Z/Y）。文字の入力欄にフォーカスがある時は何も奪わない。 */
    @Override
    public boolean onKeyPressed(Screen screen, KeyEvent event) {
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen || screen.getFocused() instanceof EditBox) {
            return false;
        }
        return ui.onKey(event);
    }

    @Override
    public boolean onKeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        return onKeyPressed(screen, new KeyEvent(keyCode, scanCode, modifiers));
    }
    //?} else {
    /*@Override
    public boolean onKeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        IFullscreen fs = current();
        if (fs == null || fs.getScreen() != screen || screen.getFocused() instanceof EditBox) {
            return false;
        }
        return ui.onKey(keyCode, scanCode, modifiers);
    }
    *///?}

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

    @Override
    public void tick() {
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
        if (minimap.sync(memos, dim, state.isTextVisible())) {
            controller.onDimensionChanged();
        }
    }
}
