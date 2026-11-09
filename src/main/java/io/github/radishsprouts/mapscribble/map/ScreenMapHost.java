package io.github.radishsprouts.mapscribble.map;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.HitTester;
import io.github.radishsprouts.mapscribble.draw.MapCanvasRenderer;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.geom.ToggleButtonPlacement;
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

    /** 切替ボタンの移動の段階（画面設計書 3.5.1）。 */
    private enum MovePhase {
        /** 動かしていない。 */
        NONE,
        /** 左ボタンを押している。しきい値の前で、離せば切替（Shift を押していれば掴む）。 */
        PRESSED,
        /** しきい値を超えて、ボタンがカーソルに付いてきている。離すとそこへ置く。 */
        DRAGGING,
        /** Shift+クリックで掴んだ。ボタンを押さずにカーソルに付いてくる。次の左クリックで置く。 */
        HELD
    }

    private static MovePhase movePhase = MovePhase.NONE;
    /** 押した点（GUI 座標・小数）。 */
    private static double pressX;
    private static double pressY;
    /** 押した点とボタン左上の差（掴んだ点を保つ）。 */
    private static int grabX;
    private static int grabY;
    /** 移動中の一時の左上座標（画面内に丸め済み）。保存しない。 */
    private static int moveX;
    private static int moveY;
    /** 押した時に Shift を押していたか。 */
    private static boolean pressWithShift;
    /** 移動を取り消した後、まだ押している左ボタンを離した通知。切替も移動も起こさず捨てる。 */
    private static boolean ignoreLeftRelease;
    /** 右クリックを自分で受けたので、離した通知は地図へ渡さない。 */
    private static boolean swallowRightRelease;

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
            // 移動中に画面が閉じたら取り消す（保存しない）
            resetMove();
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

        // 切替ボタンの移動: しきい値の判定と、カーソルへの追従（保存はしない）
        updateMove(screen, adapter, mouseX, mouseY, guiScale);
        boolean following = movePhase == MovePhase.DRAGGING || movePhase == MovePhase.HELD;

        // ドラッグ移動の反映（物理ピクセルで計算）
        if (MidDragHandler.isDragging()) {
            MidDragHandler.onMouseMove(physX, physY, (dx, dy) -> adapter.scroll(screen, dx, dy));
        } else if (movePhase == MovePhase.PRESSED || movePhase == MovePhase.DRAGGING) {
            // ボタンを押している間は、地図がドラッグで動かないようにする
            adapter.suppressMapDrag(screen);
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
        if (!following && controller.state().isVisible() && (tool == Tool.ERASER || tool == Tool.EDIT)
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
        // 移動中だけは、掴んだボタンが帯の下へ消えないように帯の後で描く（下）
        boolean modeOn = controller.state().isModeOn();
        if (!following) {
            renderToggleButton(gfx, screen, adapter, mouseX, mouseY, modeOn);
        }

        // UI帯の描画。移動中は帯のホバーとツールチップを出さないよう、画面外のマウス座標を渡す
        if (scribbleUi != null) {
            scribbleUi.render(gfx, following ? -1 : mouseX, following ? -1 : mouseY, hover);
        }

        if (following) {
            renderToggleButton(gfx, screen, adapter, mouseX, mouseY, modeOn);
        }
    }

    /** 保存してある（動かした）位置。動かしていない・動かせない地図 MOD は null。 */
    private static ToggleButtonPlacement.Saved savedPlacement(MapAdapter adapter) {
        String key = adapter == null ? null : adapter.toggleButtonKey();
        DrawingController controller = ScribbleSession.controller();
        return key == null || controller == null ? null : controller.state().toggleButton(key);
    }

    /** 切替ボタンの今の左上 X。移動中は一時の座標、そうでなければ保存値（画面内に丸めて）か既定の位置。 */
    private static int toggleX(Screen screen, MapAdapter adapter) {
        if (movePhase == MovePhase.DRAGGING || movePhase == MovePhase.HELD) {
            return moveX;
        }
        ToggleButtonPlacement.Saved saved = savedPlacement(adapter);
        if (saved != null) {
            return ToggleButtonPlacement.resolveX(saved, screen.width);
        }
        return adapter != null ? adapter.toggleButtonX(screen) : screen.width - 24;
    }

    private static int toggleY(Screen screen, MapAdapter adapter) {
        if (movePhase == MovePhase.DRAGGING || movePhase == MovePhase.HELD) {
            return moveY;
        }
        ToggleButtonPlacement.Saved saved = savedPlacement(adapter);
        if (saved != null) {
            return ToggleButtonPlacement.resolveY(saved, screen.height);
        }
        return adapter != null ? adapter.toggleButtonY(screen) : 6;
    }

    private static boolean isToggleHovered(Screen screen, MapAdapter adapter, double mouseX, double mouseY) {
        int bx = toggleX(screen, adapter);
        int by = toggleY(screen, adapter);
        return mouseX >= bx && mouseX < bx + 20 && mouseY >= by && mouseY < by + 20;
    }

    /** 描画のたびに呼ぶ。しきい値を超えたらドラッグにし、ドラッグ中・掴んでいる間は一時の座標を更新する。 */
    private static void updateMove(Screen screen, MapAdapter adapter, int mouseX, int mouseY, int guiScale) {
        if (movePhase == MovePhase.PRESSED
                && ToggleButtonPlacement.isDrag(pressX, pressY, mouseX, mouseY, guiScale)) {
            movePhase = MovePhase.DRAGGING;
        }
        if (movePhase == MovePhase.DRAGGING || movePhase == MovePhase.HELD) {
            moveX = ToggleButtonPlacement.follow(mouseX, grabX, screen.width);
            moveY = ToggleButtonPlacement.follow(mouseY, grabY, screen.height);
        }
    }

    private static void resetMove() {
        movePhase = MovePhase.NONE;
        ignoreLeftRelease = false;
        swallowRightRelease = false;
    }

    /** 置く: 一時の座標を近い端からの距離にして保存する（既定の位置と同じならキーを消す）。保存はこの1回だけ。 */
    private static void place(Screen screen, MapAdapter adapter, DrawingState state, double mouseX, double mouseY) {
        int x = ToggleButtonPlacement.follow(mouseX, grabX, screen.width);
        int y = ToggleButtonPlacement.follow(mouseY, grabY, screen.height);
        movePhase = MovePhase.NONE;
        String key = adapter.toggleButtonKey();
        if (key != null) {
            state.setToggleButton(key, ToggleButtonPlacement.toSaveOrNull(x, y, screen.width, screen.height,
                    adapter.toggleButtonX(screen), adapter.toggleButtonY(screen)));
        }
    }

    /** 移動を取り消す（動かす前の位置のまま）。左ボタンをまだ押している間は、その離した通知を捨てる。 */
    private static void cancelMove() {
        ignoreLeftRelease = movePhase == MovePhase.PRESSED || movePhase == MovePhase.DRAGGING;
        movePhase = MovePhase.NONE;
    }

    /**
     * 地図の画面が受けた右ボタンの「離した」通知を、地図へ渡さないか。右クリックを自分で消費した時は、離した通知で
     * 地図 MOD の右クリックのメニューが開かないように止める。呼び出し側は true なら通知を止める。
     */
    public static boolean shouldBlockRelease(Screen screen, int button) {
        if (button == InputConstants.MOUSE_BUTTON_RIGHT && swallowRightRelease && getAdapter(screen) != null) {
            swallowRightRelease = false;
            return true;
        }
        return false;
    }

    //? if >=26.1 {
    private static void renderToggleButton(GuiGraphicsExtractor gfx, Screen screen, MapAdapter adapter, int mouseX, int mouseY,
                                           boolean modeOn) {
    //?} else {
    /*private static void renderToggleButton(GuiGraphics gfx, Screen screen, MapAdapter adapter, int mouseX, int mouseY,
                                           boolean modeOn) {
    *///?}
        int bx = toggleX(screen, adapter);
        int by = toggleY(screen, adapter);
        boolean moving = movePhase != MovePhase.NONE;
        // 帯が手前に重なっている間は、帯のボタンが反応するので、こちらはホバー扱いにしない。
        // 押している・動かしている・掴んでいる間は、カーソルが載っているのと同じ見た目（新しい色は足さない）
        boolean hovered = moving
                || (isToggleHovered(screen, adapter, mouseX, mouseY) && !isOverBandNow(modeOn, mouseX, mouseY));
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
        if (movePhase == MovePhase.HELD) {
            // 掴んでいる間は置き方の案内を1行だけ。押している間・ドラッグ中は移動先を隠さないよう何も出さない
            GuiCompat.drawTooltip(gfx, Minecraft.getInstance().font,
                    List.of(GuiCompat.withColor(Component.translatable("mapscribble.button.toggle.place"), TOOLTIP_GRAY)),
                    mouseX, mouseY);
        } else if (hovered && !moving) {
            String key = modeOn ? "mapscribble.button.toggle.on" : "mapscribble.button.toggle.off";
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(key));
            lines.add(GuiCompat.withColor(Component.translatable(key + ".desc"), TOOLTIP_GRAY));
            lines.add(GuiCompat.withColor(Component.translatable("mapscribble.button.toggle.move"), TOOLTIP_GRAY));
            if (savedPlacement(adapter) != null) {
                lines.add(GuiCompat.withColor(Component.translatable("mapscribble.button.toggle.reset"), TOOLTIP_GRAY));
            }
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

        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            ignoreLeftRelease = false;
        }

        // 切替ボタンを動かしている間（押している・ドラッグ中・掴んでいる）は、帯より先にここで受ける
        if (movePhase != MovePhase.NONE) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                // 次の左クリックでその場所へ置く（離した通知を取りこぼした場合も同じ）。切替はしない
                if (movePhase == MovePhase.PRESSED) {
                    movePhase = MovePhase.NONE;
                } else {
                    place(screen, adapter, state, mouseX, mouseY);
                }
                return true;
            }
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                // 右クリックで取り消し。地図 MOD の右クリックの処理へも、離した通知へも渡さない
                cancelMove();
                swallowRightRelease = true;
                return true;
            }
            return false;
        }

        // UI帯のクリック処理（帯は切替ボタンより手前に描くので、重なった部分は帯が受ける）
        if (state.isModeOn() && scribbleUi != null && scribbleUi.onMouseClick(mouseX, mouseY, button)) {
            return true;
        }

        if (adapter.toggleButtonKey() != null && isToggleHovered(screen, adapter, mouseX, mouseY)) {
            // 右クリック: 元の位置へ戻す（確認なし。すでに元の位置なら何も変わらない）。地図へは渡さない
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                state.setToggleButton(adapter.toggleButtonKey(), null);
                swallowRightRelease = true;
                return true;
            }
            // 左ボタン: 押した記録だけを残す。切替は離した時（動かさずに離したか、ドラッグかは離す前に決まらない）
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                int bx = toggleX(screen, adapter);
                int by = toggleY(screen, adapter);
                movePhase = MovePhase.PRESSED;
                pressX = mouseX;
                pressY = mouseY;
                grabX = ToggleButtonPlacement.grabOffset(mouseX, bx);
                grabY = ToggleButtonPlacement.grabOffset(mouseY, by);
                moveX = bx;
                moveY = by;
                pressWithShift = GuiCompat.isShiftDown();
                return true;
            }
        }

        if (!state.isModeOn()) {
            return false;
        }

        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
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

    /**
     * 左ボタンを離した時の、切替ボタンの処理。ドラッグならそこへ置く（手書きモードは変えない）。動かさずに離したなら、
     * Shift を押して押していれば掴む、そうでなければ手書きモードを切り替える。1フレームの間に押して動かして離しても
     * 取りこぼさないよう、ここでもしきい値を判定する。
     */
    private static void finishPress(Screen screen, MapAdapter adapter, DrawingController controller,
                                    double mouseX, double mouseY) {
        if (controller == null) {
            movePhase = MovePhase.NONE;
            return;
        }
        DrawingState state = controller.state();
        int guiScale = (int) Math.max(1.0, (double) Minecraft.getInstance().getWindow().getGuiScale());
        boolean drag = movePhase == MovePhase.DRAGGING
                || ToggleButtonPlacement.isDrag(pressX, pressY, mouseX, mouseY, guiScale);
        if (drag) {
            place(screen, adapter, state, mouseX, mouseY);
            return;
        }
        if (pressWithShift) {
            // Shift+クリック: ボタンを掴む。次の左クリックで置く
            movePhase = MovePhase.HELD;
            moveX = ToggleButtonPlacement.follow(mouseX, grabX, screen.width);
            moveY = ToggleButtonPlacement.follow(mouseY, grabY, screen.height);
            return;
        }
        movePhase = MovePhase.NONE;
        // 右上の切替ボタン: オフなら手書きモードを始め、オンなら終える
        if (state.isModeOn()) {
            controller.cancelStroke();
            leftMouseDown = false;
        }
        state.toggleMode();
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

        DrawingController controller = ScribbleSession.controller();

        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (ignoreLeftRelease) {
                // 取り消した移動の左ボタンを離した通知: 切替も移動も起こさない
                ignoreLeftRelease = false;
                return true;
            }
            if (movePhase == MovePhase.PRESSED || movePhase == MovePhase.DRAGGING) {
                finishPress(screen, adapter, controller, mouseX, mouseY);
                return true;
            }
        }

        leftMouseDown = false;
        if (scribbleUi != null) {
            scribbleUi.onMouseRelease(mouseX, mouseY, button);
        }

        if (controller != null && button == InputConstants.MOUSE_BUTTON_LEFT && controller.activeStroke() != null) {
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
        // 切替ボタンの移動中の Esc は移動を取り消すだけで、地図の画面は閉じない
        if (adapter != null && movePhase != MovePhase.NONE && event.key() == InputConstants.KEY_ESCAPE) {
            cancelMove();
            return true;
        }
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
        // 切替ボタンの移動中の Esc は移動を取り消すだけで、地図の画面は閉じない
        if (adapter != null && movePhase != MovePhase.NONE && keyCode == InputConstants.KEY_ESCAPE) {
            cancelMove();
            return true;
        }
        if (adapter == null || screen.getFocused() instanceof EditBox) {
            return false;
        }
        return scribbleUi != null && scribbleUi.onKey(keyCode, scanCode, modifiers);
    }
    *///?}
}
