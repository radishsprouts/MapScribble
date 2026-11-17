package io.github.radishsprouts.mapscribble.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.compat.Gfx;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.Draft;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import io.github.radishsprouts.mapscribble.store.NotebookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.network.chat.Component;

/**
 * 画面まわりの窓口。ツールの帯の描画と入力、文字入力の画面を開く処理、キー操作、最後に使った値の保存をまとめる。
 * 地図MODのクラスは直接参照しない。Minecraft の主スレッドからだけ使う。
 */
public final class ScribbleUi {
    public static final String NOTICE_DISCARDED = "mapscribble.msg.discarded";

    private final DrawingController controller;
    private final DrawingState state;
    private final ToolBand band;
    /** 共有の待ち行列。帯が出ていない間に起きたお知らせ（StoreSession が積む）も、ここから次に帯を出した時に出る。 */
    private final NoticeTimer notice = NoticeTimer.shared();
    private final BandMenu menu = new BandMenu();
    /** メモの保存先の操作の窓口。つなぎ込み（StoreSession）が差し込む。未接続（null）でも落ちない。 */
    private Supplier<NotebookAccess> notebooks = () -> null;
    private Supplier<NotebookExtras> notebookExtras = () -> NotebookExtras.NONE;
    private final Path prefsFile;
    private String lastSavedPrefs;
    private boolean loadFailed;
    private boolean saveFailed;

    private Memo tooltipMemo;
    private Tool tooltipTool;
    private List<Component> memoTooltip = List.of();

    public ScribbleUi(DrawingController controller, Path prefsFile) {
        this.controller = controller;
        this.state = controller.state();
        this.prefsFile = prefsFile;
        this.band = new ToolBand(state, controller);

        UiPrefs.load(prefsFile, state);
        this.lastSavedPrefs = UiPrefs.toJson(state);

        // メニューを開いている間は、画面のどこも「帯の上」として扱い、地図に描かない・地図を動かさない
        state.setBandHitTest((x, y) -> state.isModeOn()
                && (menu.isOpen() || BandLayout.contains(x, y, state.bandX(), state.bandY())));
        band.setMenuButtonHandler(this::toggleMenu);
        state.setNoticeHandler(key -> notice.push(key, nowMs()));
        state.setDraftHandler(this::openNew);
        state.setEditHandler(this::openEdit);
        state.addChangeListener(this::onStateChanged);
    }

    public ToolBand band() {
        return band;
    }

    /** メモの保存先の窓口を差し込む（シングルプレイ・未接続は null を返す Supplier でよい）。 */
    public void setNotebookAccess(Supplier<NotebookAccess> access, Supplier<NotebookExtras> extras) {
        this.notebooks = access == null ? () -> null : access;
        this.notebookExtras = extras == null ? () -> NotebookExtras.NONE : extras;
    }

    public boolean isMenuOpen() {
        return menu.isOpen();
    }

    private void toggleMenu() {
        if (menu.isOpen()) {
            closeMenu();
        } else {
            menu.open();
            band.setMenuOpen(true);
        }
    }

    private void closeMenu() {
        menu.close();
        band.setMenuOpen(false);
    }

    private static long nowMs() {
        return NoticeTimer.nowMs();
    }

    public void onLoadFailed() {
        loadFailed = true;
    }

    public void onSaveStateChanged(boolean failed) {
        saveFailed = failed;
    }

    private void onStateChanged() {
        if (!state.isModeOn()) {
            controller.cancelStroke();
            loadFailed = false;
            band.release();
            closeMenu();
        }
        persistIfChanged();
    }

    private void persistIfChanged() {
        String json = UiPrefs.toJson(state);
        if (!json.equals(lastSavedPrefs)) {
            lastSavedPrefs = json;
            UiPrefs.save(prefsFile, state);
        }
    }

    public void render(Gfx g, int mouseX, int mouseY, Memo hoverMemo) {
        renderInternal(g, mouseX, mouseY, hoverMemo);
    }

    private void renderInternal(Gfx g, int mouseX, int mouseY, Memo hoverMemo) {
        // 手書きモードでない間・メニューを開いている間は説明の行を描かないので、表示中のお知らせの時間を止める
        notice.tick(nowMs(), !state.isModeOn() || menu.isOpen());
        if (!state.isModeOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        band.drag(mouseX, mouseY, screenW, screenH);
        keepBandOnScreen(screenW, screenH);
        long now = nowMs();
        MemoRepository repo = controller.repository();
        if (repo != null) {
            loadFailed = false;
        }
        // メニューを開いている間は説明の行を描かないので、お知らせは取り出さない（時間も上の tick で止めている）
        boolean loading = repo == null && !loadFailed && notebookExtras.get().isLoading();
        String noticeKey = menu.isOpen() ? null : notice.activeKey(now);
        HintResolver.Line hint = HintResolver.resolve(new HintResolver.Input(
                loadFailed, saveFailed, repo == null && !loadFailed && !loading, loading, noticeKey,
                noticeKey == null ? List.of() : notice.activeArgs(),
                !state.isVisible(),
                repo != null && repo.memos().isEmpty() && state.tool() == Tool.ENCLOSE, state.tool()));
        float alpha = noticeKey != null && noticeKey.equals(hint.translationKey()) ? notice.alpha(now) : 1f;
        band.setMenuOpen(menu.isOpen());
        band.render(g, mc.font, mouseX, mouseY, hint, alpha, screenH);
        if (menu.isOpen()) {
            // メニューは帯より手前。開いている間は説明の行も、メモのホバーの文言も出さない
            menu.render(g, mc.font, mouseX, mouseY, state.bandX(), state.bandY(), screenW, screenH, menuItems());
            return;
        }

        if (hoverMemo != null && !BandLayout.contains(mouseX, mouseY, state.bandX(), state.bandY())) {
            if (hoverMemo != tooltipMemo || state.tool() != tooltipTool) {
                tooltipMemo = hoverMemo;
                tooltipTool = state.tool();
                memoTooltip = List.of(Component.translatable(
                        state.tool() == Tool.ERASER ? "mapscribble.hover.erase" : "mapscribble.hover.edit",
                        describe(hoverMemo)));
            }
            GuiCompat.drawTooltip(g, mc.font, memoTooltip, mouseX, mouseY);
        }
    }

    /**
     * 帯が画面の外へはみ出さないようにする。帯の幅を広げた（終了ボタンの追加）ため、旧バージョンで保存した
     * 右寄りの位置や、画面を小さくした後の位置だと右端が切れて、出口の終了ボタンが見えなくなる。
     */
    private void keepBandOnScreen(int screenW, int screenH) {
        if (band.isDragging()) {
            return;
        }
        int x = Math.max(0, Math.min(Math.max(0, screenW - BandLayout.WIDTH), state.bandX()));
        int y = Math.max(0, Math.min(Math.max(0, screenH - BandLayout.HEIGHT), state.bandY()));
        if (x != state.bandX() || y != state.bandY()) {
            state.setBandPosition(x, y);
        }
    }

    static Component describe(Memo memo) {
        String text = memo.text() == null ? "" : memo.text().strip();
        int newline = text.indexOf('\n');
        String first = newline < 0 ? text : text.substring(0, newline).strip();
        if (!first.isEmpty()) {
            return Component.literal(first);
        }
        return Component.translatable(memo.kind() == MemoKind.REGION
                ? "mapscribble.note.untitled.enclose" : "mapscribble.note.untitled.line");
    }

    public boolean onMouseClick(double guiX, double guiY, int button) {
        return onMouseClick(guiX, guiY, button == InputConstants.MOUSE_BUTTON_LEFT);
    }

    public boolean onMouseClick(double guiX, double guiY, boolean leftButton) {
        if (!state.isModeOn()) {
            return false;
        }
        if (menu.isOpen()) {
            // 開いている間のクリックは、メニューの中でも外でも他へ渡さず消費する
            Minecraft mc = Minecraft.getInstance();
            boolean consumed = menu.click(guiX, guiY, leftButton, state.bandX(), state.bandY(),
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), menuItems());
            if (!menu.isOpen()) {
                band.setMenuOpen(false);
            }
            return consumed;
        }
        return band.click(guiX, guiY, leftButton);
    }

    // ---- メニュー ----

    private List<BandMenu.Item> menuItems() {
        NotebookAccess access = notebooks.get();
        boolean available = access != null && access.isAvailable();
        String note;
        Component tooltip = null;
        if (!available) {
            // シングルプレイ（と未接続）。項目は隠さず、押せない理由を文で示す
            note = I18n.get("mapscribble.menu.notebooks.single");
            tooltip = Component.translatable("mapscribble.menu.notebooks.single.desc");
        } else {
            NotebookAccess.State st = access.state();
            int inUse = NotebookPanel.inUseIndex(st);
            if (!NotebookPanel.isSplit(NotebookPanel.kindOf(st, false))) {
                note = I18n.get("mapscribble.menu.notebooks.shared");
            } else if (inUse >= 0) {
                note = NotebookNames.display(st.notebooks().get(inUse).name());
            } else {
                // 分けている接続先でワールドを見分けられない時は、補足を出さない（「分けていません」は誤り）
                note = "";
            }
        }
        return List.of(new BandMenu.Item(Component.translatable("mapscribble.menu.notebooks"), note, available, tooltip,
                () -> openNotebooks(access)));
    }

    private void openNotebooks(NotebookAccess access) {
        Screen parent = ScreenCompat.currentScreen();
        if (parent == null || access == null) {
            return;
        }
        ScreenCompat.setScreen(new NotebookScreen(parent, access, notebookExtras.get(), outcome -> {
            // 切り替えてから移した時も、切り替えたお知らせを落とさない（先に積み、移した結果を後に積む）
            if (outcome.switchedTo() != null) {
                notice.push("mapscribble.msg.notebook_switched", nowMs(), outcome.switchedTo());
            }
            if (outcome.moveFailed()) {
                state.notice("mapscribble.msg.move_failed");
            } else if (outcome.moveUndone()) {
                state.notice("mapscribble.msg.move_undone");
            } else if (outcome.movedCount() > 0) {
                notice.push("mapscribble.msg.moved", nowMs(), outcome.movedCount());
            }
        }));
    }

    public boolean onMouseRelease(double guiX, double guiY, int button) {
        band.release();
        persistIfChanged();
        return true;
    }

    public void onMouseRelease() {
        band.release();
        persistIfChanged();
    }

    //? if >=1.21.11 {
    public boolean onKey(KeyEvent event) {
        if (state.isModeOn() && menu.isOpen()) {
            if (menu.key(event.key(), menuItems())) {
                band.setMenuOpen(menu.isOpen());
                return true;
            }
            // ツールの切替・元に戻す・やり直す・モードの切替は、メニューを閉じてからそのキーの操作を行う
            if (ScribbleKeys.isToggle(event) || ScribbleKeys.toolFor(event) != null
                    || (GuiCompat.isControlDown(event) && (event.key() == InputConstants.KEY_Z
                    || event.key() == InputConstants.KEY_Y))) {
                closeMenu();
            }
        }
        if (ScribbleKeys.isToggle(event)) {
            state.toggleMode();
            return true;
        }
        if (!state.isModeOn()) {
            return false;
        }
        Tool tool = ScribbleKeys.toolFor(event);
        if (tool != null) {
            state.setTool(tool);
            return true;
        }
        if (!controller.isActive() || !GuiCompat.isControlDown(event)) {
            return false;
        }
        if (event.key() == InputConstants.KEY_Z) {
            if (event.hasShiftDown()) {
                controller.redo();
            } else {
                controller.undo();
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_Y) {
            controller.redo();
            return true;
        }
        return false;
    }
    //?} else {
    /*public boolean onKey(int keyCode, int scanCode, int modifiers) {
        if (state.isModeOn() && menu.isOpen()) {
            if (menu.key(keyCode, menuItems())) {
                band.setMenuOpen(menu.isOpen());
                return true;
            }
            // ツールの切替・元に戻す・やり直す・モードの切替は、メニューを閉じてからそのキーの操作を行う
            if (ScribbleKeys.isToggle(keyCode, scanCode) || ScribbleKeys.toolFor(keyCode, scanCode) != null
                    || (GuiCompat.isControlDown(modifiers) && (keyCode == 90 || keyCode == 89))) {
                closeMenu();
            }
        }
        if (ScribbleKeys.isToggle(keyCode, scanCode)) {
            state.toggleMode();
            return true;
        }
        if (!state.isModeOn()) {
            return false;
        }
        Tool tool = ScribbleKeys.toolFor(keyCode, scanCode);
        if (tool != null) {
            state.setTool(tool);
            return true;
        }
        if (!controller.isActive() || !GuiCompat.isControlDown(modifiers)) {
            return false;
        }
        // GLFW_KEY_Z = 90, GLFW_KEY_Y = 89
        if (keyCode == 90) {
            if (GuiCompat.isShiftDown(modifiers)) {
                controller.redo();
            } else {
                controller.undo();
            }
            return true;
        }
        if (keyCode == 89) {
            controller.redo();
            return true;
        }
        return false;
    }
    *///?}

    private void openNew(Draft draft) {
        Screen parent = ScreenCompat.currentScreen();
        if (parent == null) {
            return;
        }
        NoteEditScreen.Spec spec = new NoteEditScreen.Spec(draft.kind(), false, "", draft.color(),
                FillLevel.nearest(draft.fillOpacity()), TextSize.nearest(draft.textScale()), draft.onMinimap());
        ScreenCompat.setScreen(new NoteEditScreen(parent, spec, new NoteEditScreen.Callbacks() {
            @Override
            public void confirm(NoteEditScreen.Result r) {
                float fill = draft.kind() == MemoKind.REGION ? r.fill().opacity() : draft.fillOpacity();
                Draft edited = new Draft(draft.kind(), draft.points(), r.color(), draft.width(), fill,
                        r.size().scale(), r.onMinimap());
                controller.add(edited.toMemo(r.text()));
                state.setColor(r.color());
                if (draft.kind() == MemoKind.REGION) {
                    state.setFillOpacity(r.fill().opacity());
                }
                state.setTextScale(r.size().scale());
                state.setOnMinimap(r.onMinimap());
                persistIfChanged();
            }

            @Override
            public void cancel() {
                state.notice(NOTICE_DISCARDED);
            }

            @Override
            public void delete() {
            }
        }));
    }

    private void openEdit(Memo memo) {
        Screen parent = ScreenCompat.currentScreen();
        if (parent == null) {
            return;
        }
        NoteEditScreen.Spec spec = new NoteEditScreen.Spec(memo.kind(), true, memo.text(), memo.color(),
                FillLevel.nearest(memo.fillOpacity()), TextSize.nearest(memo.textScale()), memo.onMinimap());
        ScreenCompat.setScreen(new NoteEditScreen(parent, spec, new NoteEditScreen.Callbacks() {
            @Override
            public void confirm(NoteEditScreen.Result r) {
                float fill = memo.kind() == MemoKind.REGION && r.fill() != FillLevel.nearest(memo.fillOpacity())
                        ? r.fill().opacity() : memo.fillOpacity();
                float scale = r.size() != TextSize.nearest(memo.textScale()) ? r.size().scale() : memo.textScale();
                Memo after = new Memo(memo.id(), memo.kind(), memo.points(), r.color(), memo.width(), fill, r.text(),
                        scale, r.onMinimap(), memo.created(), memo.updated());
                if (!after.equals(memo)) {
                    controller.update(memo, after);
                }
            }

            @Override
            public void cancel() {
            }

            @Override
            public void delete() {
                controller.remove(memo);
            }
        }));
    }
}
