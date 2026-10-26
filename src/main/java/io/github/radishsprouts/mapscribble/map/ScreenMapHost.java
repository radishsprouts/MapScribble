package io.github.radishsprouts.mapscribble.map;

import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.HitTester;
import io.github.radishsprouts.mapscribble.draw.MapCanvasRenderer;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.map.ftb.FtbChunksAdapter;
import io.github.radishsprouts.mapscribble.map.jm.JourneyMapLegacyAdapter;
import io.github.radishsprouts.mapscribble.map.xaero.XaeroMapAdapter;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.BandLayout;
import io.github.radishsprouts.mapscribble.ui.ScribbleUi;
import io.github.radishsprouts.mapscribble.ui.UiPrefs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import net.minecraft.network.chat.Component;

/**
 * Xaero's World Map および FTB Chunks 画面に対する手書き入力・描画・UIを仲介するホスト。
 */
public final class ScreenMapHost {
    //? if >=1.21.11 {
    private static final Identifier TOGGLE_ICON = GuiCompat.texture("textures/gui/toggle.png");
    //?} else {
    /*private static final ResourceLocation TOGGLE_ICON = GuiCompat.texture("textures/gui/toggle.png");
    *///?}

    /** 右上の切替ボタン: オンの面（帯の「選択中」と同じ）とオン・ホバーの面。 */
    private static final int TOGGLE_ON_FACE = 0xFF2E7DD7;
    private static final int TOGGLE_ON_HOVER_FACE = 0xFF4A93E4;
    /** 右上の切替ボタン: オフの縁（明るい地形の上でも面に対して 3:1 以上）。 */
    private static final int TOGGLE_OFF_BORDER = 0xFFA8ADB4;
    /** ツールチップ 2 行目の灰色。 */
    private static final int TOOLTIP_GRAY = 0xA0A0A0;

    private static final List<MapAdapter> ADAPTERS = new ArrayList<>();
    private static PrimaryMapBridge primaryBridge;
    private static MapCanvasRenderer canvasRenderer;
    private static ScribbleUi scribbleUi;
    private static boolean leftMouseDown;
    private static double lastMouseX;
    private static double lastMouseY;

    private static final HitTester.TextMeasure TEXT_MEASURE = new HitTester.TextMeasure() {
        @Override
        public int width(String line) {
            return Minecraft.getInstance().font.width(line);
        }

        @Override
        public int lineHeight() {
            return Minecraft.getInstance().font.lineHeight;
        }
    };

    static {
        ADAPTERS.add(new XaeroMapAdapter());
        ADAPTERS.add(new FtbChunksAdapter());
        ADAPTERS.add(new JourneyMapLegacyAdapter());
    }

    private ScreenMapHost() {
    }

    public static synchronized void setPrimaryBridge(PrimaryMapBridge bridge) {
        primaryBridge = bridge;
    }

    public static boolean hasPrimaryBridge() {
        return primaryBridge != null;
    }

    public static void tick() {
        if (primaryBridge != null) {
            primaryBridge.tick();
        }
    }

    public static synchronized void init(DrawingController controller) {
        if (canvasRenderer == null) {
            canvasRenderer = new MapCanvasRenderer(controller);
            scribbleUi = new ScribbleUi(controller,
                    ScreenCompat.gameDirectory().resolve("config").resolve(UiPrefs.FILE_NAME));
            StoreSession.addNotifier(message -> scribbleUi.onLoadFailed());
            StoreSession.addSaveNotifier(scribbleUi::onSaveStateChanged);
        }
    }

    public static MapAdapter getAdapter(Screen screen) {
        if (screen == null) {
            return null;
        }
        for (MapAdapter adapter : ADAPTERS) {
            if (adapter.handles(screen)) {
                return adapter;
            }
        }
        return null;
    }

    public static boolean handles(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            return true;
        }
        return getAdapter(screen) != null;
    }

    public static void onScreenInit(Screen screen) {
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            primaryBridge.onScreenInit(screen);
            return;
        }
        MapAdapter adapter = getAdapter(screen);
        if (adapter != null) {
            adapter.onScreenInit(screen);
        }
    }

    public static void onScreenClose(Screen screen) {
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            primaryBridge.onScreenClose(screen);
            return;
        }
        MapAdapter adapter = getAdapter(screen);
        if (adapter != null) {
            DrawingController controller = ScribbleSession.controller();
            if (controller != null) {
                controller.cancelStroke();
            }
            MidDragHandler.reset();
            leftMouseDown = false;
            adapter.onScreenClose(screen);
        }
    }

    //? if >=26.1 {
    public static void onScreenRender(GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY, float delta) {
    //?} else {
    /*public static void onScreenRender(GuiGraphics gfx, Screen screen, int mouseX, int mouseY, float delta) {
    *///?}
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            primaryBridge.onScreenRender(gfx, screen, mouseX, mouseY);
            return;
        }
        MapAdapter adapter = getAdapter(screen);
        if (adapter == null) {
            return;
        }
        Projection proj = adapter.currentProjection(screen);
        if (proj == null) {
            return;
        }
        DrawingController controller = ScribbleSession.controller();
        if (controller == null) {
            return;
        }

        int guiScale = (int) Math.max(1.0, (double) Minecraft.getInstance().getWindow().getGuiScale());
        double physX = mouseX * guiScale;
        double physY = mouseY * guiScale;

        // ドラッグ移動の反映（物理ピクセルで計算）
        if (MidDragHandler.isDragging()) {
            MidDragHandler.onMouseMove(physX, physY, (dx, dy) -> adapter.scroll(screen, dx, dy));
        } else if (leftMouseDown && controller.state().isModeOn()) {
            adapter.suppressMapDrag(screen);
            if (controller.state().tool().isStrokeTool()) {
                controller.extendStroke(physX, physY, proj);
            }
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // 手書きメモの描画
        Object dim = adapter.currentDimension(screen);
        if (canvasRenderer != null) {
            canvasRenderer.render(gfx, proj, dim, guiScale);
        }

        // ホバー中のメモ検索（消しゴム・編集用）
        Memo hover = null;
        Tool tool = controller.state().tool();
        if (controller.state().isVisible() && (tool == Tool.ERASER || tool == Tool.EDIT)
                && !BandLayout.contains(mouseX, mouseY)) {
            MemoRepository repo = controller.repository();
            if (repo != null && repo.isAvailable()) {
                double px = physX + guiScale / 2.0;
                double py = physY + guiScale / 2.0;
                hover = HitTester.find(repo.memos(), proj.toWorldX(px), proj.toWorldZ(py),
                        proj.pixelsToWorld(1.0), guiScale, TEXT_MEASURE, controller.state().isTextVisible());
            }
        }

        // 右上の切替ボタンは、オン・オフのどちらでも描く（オンの間は押すと手書きモードを終える）。
        // 帯をドラッグして重ねた時に帯が手前になるよう、帯より先に描く
        renderToggleButton(gfx, screen, adapter, mouseX, mouseY, controller.state().isModeOn());

        // UI帯の描画
        if (scribbleUi != null) {
            scribbleUi.render(gfx, mouseX, mouseY, hover);
        }
    }

    private static boolean isToggleHovered(Screen screen, MapAdapter adapter, double mouseX, double mouseY) {
        int bx = adapter != null ? adapter.toggleButtonX(screen) : screen.width - 24;
        int by = adapter != null ? adapter.toggleButtonY(screen) : 6;
        return mouseX >= bx && mouseX < bx + 20 && mouseY >= by && mouseY < by + 20;
    }

    //? if >=26.1 {
    private static void renderToggleButton(GuiGraphicsExtractor gfx, Screen screen, MapAdapter adapter, int mouseX, int mouseY,
                                           boolean modeOn) {
    //?} else {
    /*private static void renderToggleButton(GuiGraphics gfx, Screen screen, MapAdapter adapter, int mouseX, int mouseY,
                                           boolean modeOn) {
    *///?}
        int bx = adapter != null ? adapter.toggleButtonX(screen) : screen.width - 24;
        int by = adapter != null ? adapter.toggleButtonY(screen) : 6;
        // 帯が手前に重なっている間は、帯のボタンが反応するので、こちらはホバー扱いにしない
        boolean hovered = isToggleHovered(screen, adapter, mouseX, mouseY) && !isOverBandNow(modeOn, mouseX, mouseY);
        int bg;
        int border;
        int iconTint;
        if (modeOn) {
            bg = hovered ? TOGGLE_ON_HOVER_FACE : TOGGLE_ON_FACE;
            border = 0xFFFFFFFF;
            iconTint = 0xFFFFFFFF;
        } else {
            bg = hovered ? 0xEE303030 : 0xCC181818;
            border = hovered ? 0xFFFFFFFF : TOGGLE_OFF_BORDER;
            iconTint = hovered ? 0xFFFFFFFF : 0xFFE6E6E6;
        }
        GuiCompat.fill(gfx, bx, by, bx + 20, by + 20, bg);
        GuiCompat.outline(gfx, bx, by, 20, 20, border);
        GuiCompat.blitIcon(gfx, TOGGLE_ICON, bx + 2, by + 2, 16, iconTint);
        if (hovered) {
            String key = modeOn ? "mapscribble.button.toggle.on" : "mapscribble.button.toggle.off";
            List<Component> lines = List.of(Component.translatable(key),
                    GuiCompat.withColor(Component.translatable(key + ".desc"), TOOLTIP_GRAY));
            GuiCompat.drawTooltip(gfx, Minecraft.getInstance().font, lines, mouseX, mouseY);
        }
    }

    /** 手書きモード中で、この GUI 座標の点がツールの帯の上か。 */
    private static boolean isOverBandNow(boolean modeOn, double mouseX, double mouseY) {
        DrawingController controller = ScribbleSession.controller();
        return modeOn && controller != null && controller.state().isOverBand(mouseX, mouseY);
    }

    public static boolean onMouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            return primaryBridge.onMouseClicked(screen, mouseX, mouseY, button);
        }
        MapAdapter adapter = getAdapter(screen);
        if (adapter == null) {
            return false;
        }

        int guiScale = (int) Math.max(1.0, (double) Minecraft.getInstance().getWindow().getGuiScale());
        double physX = mouseX * guiScale;
        double physY = mouseY * guiScale;

        // ミドルボタン押下でドラッグスクロール開始（物理ピクセル）
        if (button == 2) {
            MidDragHandler.onMouseDown(2, physX, physY);
            return true;
        }

        DrawingController controller = ScribbleSession.controller();
        if (controller == null) {
            return false;
        }
        DrawingState state = controller.state();

        // UI帯のクリック処理（帯は切替ボタンより手前に描くので、重なった部分は帯が受ける）
        if (state.isModeOn() && scribbleUi != null && scribbleUi.onMouseClick(mouseX, mouseY, button)) {
            return true;
        }

        // 右上の切替ボタン: オフなら手書きモードを始め、オンなら終える
        if (button == 0 && isToggleHovered(screen, adapter, mouseX, mouseY)) {
            if (state.isModeOn()) {
                controller.cancelStroke();
                leftMouseDown = false;
            }
            state.toggleMode();
            return true;
        }

        if (!state.isModeOn()) {
            return false;
        }

        if (button != 0) {
            return false;
        }

        // 帯の上をクリックした場合は地図へイベントを渡さない
        if (state.isOverBand(mouseX, mouseY)) {
            return true;
        }

        Projection proj = adapter.currentProjection(screen);
        if (proj == null) {
            return false;
        }

        Tool tool = state.tool();
        switch (tool) {
            case ENCLOSE:
            case LINE:
                leftMouseDown = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                controller.beginStroke(physX, physY, proj);
                adapter.suppressMapDrag(screen);
                return true;
            case TEXT:
                controller.placeText(proj.toWorldX(physX), proj.toWorldZ(physY));
                return true;
            case ERASER:
            case EDIT:
                pick(controller, tool, proj, physX, physY, guiScale);
                return true;
            default:
                return false;
        }
    }

    private static void pick(DrawingController controller, Tool tool, Projection proj, double px, double py, int guiScale) {
        DrawingState state = controller.state();
        if (!state.isVisible()) {
            state.notice(DrawingController.NOTICE_HIDDEN_PICK);
            return;
        }
        MemoRepository repo = controller.repository();
        if (repo == null || !repo.isAvailable()) {
            return;
        }
        Memo hit = HitTester.find(repo.memos(), proj.toWorldX(px), proj.toWorldZ(py),
                proj.pixelsToWorld(1.0), guiScale, TEXT_MEASURE, state.isTextVisible());
        if (tool == Tool.ERASER) {
            controller.erase(hit);
        } else {
            controller.requestEdit(hit);
        }
    }

    public static boolean onMouseReleased(Screen screen, double mouseX, double mouseY, int button) {
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            return primaryBridge.onMouseReleased(screen, mouseX, mouseY, button);
        }
        MapAdapter adapter = getAdapter(screen);
        if (adapter == null) {
            return false;
        }

        if (button == 2) {
            MidDragHandler.onMouseUp(2);
            return true;
        }

        leftMouseDown = false;
        if (scribbleUi != null) {
            scribbleUi.onMouseRelease(mouseX, mouseY, button);
        }

        DrawingController controller = ScribbleSession.controller();
        if (controller != null && button == 0 && controller.activeStroke() != null) {
            Projection proj = adapter.currentProjection(screen);
            if (proj != null) {
                controller.finishStroke(proj);
            }
            return true;
        }
        return false;
    }

    //? if >=1.21.11 {
    public static boolean onKeyPressed(Screen screen, KeyEvent event) {
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            return primaryBridge.onKeyPressed(screen, event);
        }
        MapAdapter adapter = getAdapter(screen);
        if (adapter == null || screen.getFocused() instanceof EditBox) {
            return false;
        }
        return scribbleUi != null && scribbleUi.onKey(event);
    }

    public static boolean onKeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            return primaryBridge.onKeyPressed(screen, keyCode, scanCode, modifiers);
        }
        return onKeyPressed(screen, new KeyEvent(keyCode, scanCode, modifiers));
    }
    //?} else {
    /*public static boolean onKeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        if (primaryBridge != null && primaryBridge.handles(screen)) {
            return primaryBridge.onKeyPressed(screen, keyCode, scanCode, modifiers);
        }
        MapAdapter adapter = getAdapter(screen);
        if (adapter == null || screen.getFocused() instanceof EditBox) {
            return false;
        }
        return scribbleUi != null && scribbleUi.onKey(keyCode, scanCode, modifiers);
    }
    *///?}
}
