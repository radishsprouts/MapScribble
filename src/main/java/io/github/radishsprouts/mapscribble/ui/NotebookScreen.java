package io.github.radishsprouts.mapscribble.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.Gfx;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.store.NotebookAccess;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;

/**
 * メモの保存先の画面（設計書 3.7）。メニューの「保存先…」から開く。画面は {@link NotebookAccess} と
 * {@link NotebookExtras} だけを呼び、ファイルを知らない。何も繋がっていない（null・{@link NotebookExtras#NONE}）時も落ちない。
 * 閉じたら親の画面（全画面マップ）へ戻り、起きたことを {@link Callbacks} へ渡す。
 */
public final class NotebookScreen extends Screen {
    /** 画面を閉じた時に伝える、この画面の中で起きたこと。 */
    public record Outcome(String switchedTo, int movedCount, boolean moveUndone, boolean moveFailed) {
    }

    public interface Callbacks {
        void onClosed(Outcome outcome);
    }

    private static final int CURTAIN = 0x80000000;
    private static final int PANEL_BG = 0xF0202428;
    private static final int PANEL_EDGE = 0xFF50565E;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int LABEL = 0xFFE0E0E0;
    private static final int RENAME_EDIT_WIDTH = 190;

    private final Screen parent;
    private final NotebookAccess access;
    private final NotebookExtras extras;
    private final Callbacks callbacks;

    private NotebookLayout layout;
    private CycleButton<Boolean> splitButton;
    private NotebookList list;
    private EditBox renameEdit;
    private Button renameButton;
    private Button newButton;
    private Button moveAllButton;
    private Button moveVisibleButton;
    private Button doneButton;
    private boolean parentFailed;

    private boolean renaming;
    /** 名前の変更を始めたボタン（「名前を変える」か「新しいメモ帳」）。キーで終えた時にフォーカスを戻す先。 */
    private Button renameStarter;
    /** マウスのクリックの処理の最中か（その間に名前の入力欄へ移したフォーカスは、バニラが押したボタンへ上書きする）。 */
    private boolean inMouseClick;
    /** クリックの処理の最中に名前の変更へ入った。クリックの処理が終わった後で、もう一度入力欄へフォーカスを移す。 */
    private boolean focusRenameAfterClick;
    private String renameError;
    /** 名前が重なった既存のメモ帳の名前（renameError が error.duplicate の時だけ。無ければ null）。 */
    private String renameErrorName;
    private boolean moving;
    private boolean moveFailed;
    private int movedCount;
    private String movedName = "";
    private boolean moveUndone;
    private String switchedTo;
    /** 「…」で切った行にマウスを載せた時に全文を出すための、この描画の候補。 */
    private final List<Truncated> truncated = new ArrayList<>();

    private record Truncated(int x, int y, int w, String full) {
    }

    public NotebookScreen(Screen parent, NotebookAccess access, NotebookExtras extras, Callbacks callbacks) {
        super(Component.translatable("mapscribble.notebook.title"));
        this.parent = parent;
        this.access = access;
        this.extras = extras == null ? NotebookExtras.NONE : extras;
        this.callbacks = callbacks;
    }

    // ---- 状態 ----

    private NotebookAccess.State state() {
        return access == null ? null : access.state();
    }

    private NotebookPanel.Kind kind() {
        return NotebookPanel.kindOf(state(), extras.isLoading());
    }

    private List<NotebookAccess.Info> notebooks() {
        NotebookAccess.State s = state();
        return s == null ? List.of() : s.notebooks();
    }

    private NotebookAccess.Info inUse() {
        int i = NotebookPanel.inUseIndex(state());
        return i < 0 ? null : notebooks().get(i);
    }

    private boolean busy() {
        return moving || extras.isMoving();
    }

    // ---- 部品 ----

    @Override
    protected void init() {
        layout = new NotebookLayout(height);
        int px = layout.panelX(width);
        int py = layout.panelY(height);
        int left = px + NotebookLayout.MARGIN;

        splitButton = splitButton(left, py + layout.switchY());
        addRenderableWidget(splitButton);

        list = new NotebookList(font, left, py + layout.listY(), NotebookLayout.CONTENT_WIDTH,
                layout.visibleRows(), this::onPickNotebook);
        addRenderableWidget(list);

        renameEdit = new EditBox(font, left, py + layout.listY(), RENAME_EDIT_WIDTH, NotebookLayout.ROW_HEIGHT,
                Component.translatable("mapscribble.notebook.rename.label"));
        renameEdit.setMaxLength(NotebookPanel.NAME_MAX);
        renameEdit.visible = false;
        renameEdit.active = false;
        addRenderableWidget(renameEdit);

        renameButton = addRenderableWidget(Button.builder(Component.translatable("mapscribble.notebook.rename"),
                        b -> onRenameButton())
                .bounds(left - NotebookLayout.MARGIN + layout.renameX(), py + layout.renameY(), layout.renameWidth(),
                        NotebookLayout.BUTTON_HEIGHT).build());
        newButton = addRenderableWidget(Button.builder(Component.translatable("mapscribble.notebook.new"),
                        b -> onNewButton())
                .bounds(px + layout.newX(), py + layout.renameY(), layout.newWidth(), NotebookLayout.BUTTON_HEIGHT)
                .build());
        moveAllButton = addRenderableWidget(Button.builder(Component.empty(), b -> onMoveAllButton())
                .bounds(px + layout.moveAllX(), py + layout.moveY(), layout.moveWidth(), NotebookLayout.BUTTON_HEIGHT)
                .build());
        moveVisibleButton = addRenderableWidget(Button.builder(Component.empty(), b -> askMove(false))
                .bounds(px + layout.moveVisibleX(), py + layout.moveY(), layout.moveWidth(),
                        NotebookLayout.BUTTON_HEIGHT).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> close())
                .bounds(px + layout.doneX(), py + layout.doneY(), layout.doneWidth(), NotebookLayout.BUTTON_HEIGHT)
                .build());

        refresh();
    }

    private CycleButton<Boolean> splitButton(int x, int y) {
        boolean on = NotebookPanel.switchOn(kind());
        //? if >=1.21.11 {
        return CycleButton.<Boolean>builder(
                        v -> Component.translatable(v ? "options.on" : "options.off"), on)
                .withValues(Arrays.asList(Boolean.TRUE, Boolean.FALSE))
                .create(x, y, NotebookLayout.CONTENT_WIDTH, NotebookLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.notebook.split"), (b, v) -> onSplitChanged(v));
        //?} else {
        /*return CycleButton.<Boolean>builder(
                        v -> Component.translatable(v ? "options.on" : "options.off"))
                .withValues(Arrays.asList(Boolean.TRUE, Boolean.FALSE))
                .withInitialValue(on)
                .create(x, y, NotebookLayout.CONTENT_WIDTH, NotebookLayout.BUTTON_HEIGHT,
                        Component.translatable("mapscribble.notebook.split"), (b, v) -> onSplitChanged(v));
        *///?}
    }

    /** 状態を部品へ反映する。操作の結果・状態の変化のたびに呼ぶ（部品は作り直さない）。 */
    private void refresh() {
        if (layout == null) {
            return;
        }
        NotebookPanel.Kind kind = kind();
        boolean busy = busy();
        boolean idle = !busy && !renaming;

        splitButton.setValue(NotebookPanel.switchOn(kind));
        splitButton.active = NotebookPanel.switchEnabled(kind) && idle;

        list.setPickable(NotebookPanel.listEnabled(kind) && idle);
        // 分けていない接続先でも、記録にあるメモ帳は灰色の押せない行で出す（設計書 3.7）
        list.setDimmed(kind == NotebookPanel.Kind.UNSPLIT);
        list.setRows(listRows());
        list.setRenamingInUse(renaming);
        if (kind == NotebookPanel.Kind.LOADING) {
            list.setEmptyLines(Component.translatable("mapscribble.notebook.loading"), null);
        } else if (kind == NotebookPanel.Kind.LOAD_FAILED) {
            list.setEmptyLines(Component.translatable("mapscribble.notebook.load_failed.list"), null);
        } else if (kind == NotebookPanel.Kind.UNSPLIT && notebooks().isEmpty()) {
            list.setEmptyLines(Component.translatable("mapscribble.notebook.empty"),
                    Component.translatable("mapscribble.notebook.empty.hint"));
        } else {
            list.setEmptyLines(null, null);
        }

        if (renaming) {
            renameButton.setMessage(Component.translatable("mapscribble.notebook.rename.confirm"));
            newButton.setMessage(Component.translatable("gui.cancel"));
            renameButton.active = true;
            newButton.active = true;
            renameButton.setTooltip(null);
            newButton.setTooltip(null);
        } else {
            renameButton.setMessage(Component.translatable("mapscribble.notebook.rename"));
            newButton.setMessage(Component.translatable("mapscribble.notebook.new"));
            renameButton.active = NotebookPanel.renameEnabled(kind) && idle && inUse() != null;
            newButton.active = NotebookPanel.newEnabled(kind) && idle;
            renameButton.setTooltip(renameButton.active
                    ? Tooltip.create(Component.translatable("mapscribble.notebook.rename.desc")) : null);
            newButton.setTooltip(newButton.active
                    ? Tooltip.create(Component.translatable("mapscribble.notebook.new.desc")) : null);
        }

        refreshMoveButtons(kind, idle);
        doneButton.active = !busy;
    }

    private void refreshMoveButtons(NotebookPanel.Kind kind, boolean idle) {
        boolean split = NotebookPanel.isSplit(kind);
        boolean available = NotebookPanel.moveAvailable(kind) && inUse() != null;
        // ボタンの件数は、実際に移る件数（今のディメンションの分ける前のメモ）と同じ値
        int total = extras.legacyHere();
        int visible = extras.legacyVisible();

        boolean undoShown = movedCount > 0 && !moveUndone && extras.canUndoMove();
        if (undoShown) {
            moveAllButton.setMessage(Component.translatable("mapscribble.notebook.undo"));
            moveAllButton.active = !busy() && !renaming;
        } else if (kind == NotebookPanel.Kind.UNSPLIT) {
            moveAllButton.setMessage(Component.translatable("mapscribble.notebook.move_all.plain"));
            moveAllButton.active = false;
        } else {
            moveAllButton.setMessage(Component.translatable("mapscribble.notebook.move_all",
                    NotebookPanel.countLabel(total)));
            moveAllButton.active = available && idle && total > 0;
        }
        if (kind == NotebookPanel.Kind.UNSPLIT) {
            moveVisibleButton.setMessage(Component.translatable("mapscribble.notebook.move_visible.plain"));
            moveVisibleButton.active = false;
        } else {
            moveVisibleButton.setMessage(Component.translatable("mapscribble.notebook.move_visible",
                    NotebookPanel.countLabel(visible)));
            moveVisibleButton.active = available && idle && visible > 0;
        }

        Tooltip disabledOff = Tooltip.create(Component.translatable("mapscribble.notebook.move.disabled_off"));
        NotebookAccess.Info current = inUse();
        Tooltip movable = current == null ? null : Tooltip.create(Component.empty()
                .append(Component.translatable("mapscribble.notebook.move.desc",
                        NotebookNames.display(current.name())))
                .append(Component.literal("\n"))
                .append(Component.translatable("mapscribble.notebook.move.desc2")));
        moveAllButton.setTooltip(undoShown ? null : (kind == NotebookPanel.Kind.UNSPLIT ? disabledOff
                : (moveAllButton.active ? movable : null)));
        moveVisibleButton.setTooltip(kind == NotebookPanel.Kind.UNSPLIT ? disabledOff
                : (moveVisibleButton.active ? movable : null));
        if (!split) {
            moveAllButton.active = false;
            moveVisibleButton.active = false;
        }
    }

    private List<NotebookList.Row> listRows() {
        NotebookAccess.State s = state();
        NotebookPanel.Kind kind = kind();
        if (s == null || kind == NotebookPanel.Kind.LOADING || kind == NotebookPanel.Kind.LOAD_FAILED) {
            return List.of();
        }
        ZoneId zone = ZoneId.systemDefault();
        // 分けていない接続先では使用中の行を出さない（灰色の押せない行として並べるだけ）
        int inUseIndex = NotebookPanel.isSplit(kind) ? NotebookPanel.inUseIndex(s) : -1;
        List<NotebookList.Row> rows = new ArrayList<>();
        for (int i = 0; i < s.notebooks().size(); i++) {
            NotebookAccess.Info info = s.notebooks().get(i);
            int n = extras.noteCount(info.id());
            String count = n == 1 ? Component.translatable("mapscribble.notebook.count.one").getString()
                    : Component.translatable("mapscribble.notebook.count", NotebookPanel.countLabel(n)).getString();
            rows.add(new NotebookList.Row(info.id(), NotebookNames.display(info.name()), count,
                    NotebookPanel.dateLabel(info.created(), zone), i == inUseIndex,
                    NotebookNames.isBlank(info.name())));
        }
        return rows;
    }

    // ---- 操作 ----

    private void onSplitChanged(boolean split) {
        if (access == null) {
            refresh();
            return;
        }
        access.setSplit(split, result -> refresh());
        refresh();
    }

    private void onPickNotebook(String id) {
        if (access == null || busy() || renaming) {
            return;
        }
        NotebookAccess.State s = state();
        if (s != null && id.equals(s.currentNotebookId())) {
            return;
        }
        // 確認は出さない（元のメモ帳をもう一度押せば戻り、何も失わないため）
        access.select(id, true, result -> {
            if (result.ok()) {
                for (NotebookAccess.Info info : notebooks()) {
                    if (info.id().equals(id)) {
                        switchedTo = NotebookNames.display(info.name());
                    }
                }
            }
            refresh();
        });
    }

    private void onRenameButton() {
        if (renaming) {
            confirmRename(false);
        } else {
            renameStarter = renameButton;
            startRename();
        }
    }

    private void onNewButton() {
        if (renaming) {
            cancelRename(false);
            return;
        }
        if (access == null) {
            return;
        }
        renameStarter = newButton;
        String name = NotebookPanel.defaultName(notebooks(),
                n -> Component.translatable("mapscribble.notebook.default_name", n).getString());
        access.create(name, created -> {
            if (!created.ok()) {
                renameError = errorKey(created);
                refresh();
                return;
            }
            String newId = null;
            for (NotebookAccess.Info info : notebooks()) {
                if (info.name().equals(name)) {
                    newId = info.id();
                }
            }
            if (newId == null) {
                refresh();
                return;
            }
            final String id = newId;
            // このワールドで使うメモ帳にして、すぐ名前を変える状態に入る
            access.select(id, true, selected -> {
                switchedTo = name;
                refresh();
                if (selected.ok()) {
                    startRename();
                }
            });
        });
    }

    private void startRename() {
        NotebookAccess.Info current = inUse();
        if (current == null || renaming) {
            return;
        }
        renaming = true;
        renameError = null;
        list.scrollToInUse();
        refresh();
        int[] bounds = list.inUseRowBounds();
        if (bounds != null) {
            renameEdit.setX(bounds[0] + 2);
            renameEdit.setY(bounds[1]);
        }
        renameEdit.setValue(current.name());
        renameEdit.setHighlightPos(0);
        renameEdit.visible = true;
        renameEdit.active = true;
        setFocused(renameEdit);
        // ボタンのクリックの最中なら、バニラが子の mouseClicked の後に押したボタンへフォーカスを移し直すので、
        // クリックの処理が終わった後にもう一度入力欄へ移す
        if (inMouseClick) {
            focusRenameAfterClick = true;
        }
    }

    /** クリックの処理（バニラのフォーカスの移し直しを含む）が終わった後に、名前の入力欄へフォーカスを戻す。 */
    private void afterMouseClick() {
        inMouseClick = false;
        if (focusRenameAfterClick) {
            focusRenameAfterClick = false;
            if (renaming) {
                setFocused(renameEdit);
            }
        }
    }

    //? if >=1.21.11 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        inMouseClick = true;
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            afterMouseClick();
        }
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        inMouseClick = true;
        try {
            return super.mouseClicked(mouseX, mouseY, button);
        } finally {
            afterMouseClick();
        }
    }
    *///?}

    private void confirmRename(boolean byKey) {
        NotebookAccess.Info current = inUse();
        if (current == null || access == null) {
            cancelRename(byKey);
            return;
        }
        String error = NotebookPanel.validateName(renameEdit.getValue(), notebooks(), current.id());
        if (error != null) {
            // 入力欄と入力した文字は残す
            renameError = error;
            renameErrorName = "mapscribble.notebook.error.duplicate".equals(error)
                    ? NotebookPanel.conflictingName(renameEdit.getValue(), notebooks(), current.id()) : null;
            return;
        }
        renameErrorName = null;
        String name = NotebookPanel.normalizeName(renameEdit.getValue());
        access.rename(current.id(), name, result -> {
            if (result.ok()) {
                renameError = null;
                endRename(byKey);
            } else {
                renameError = errorKey(result);
                refresh();
            }
        });
    }

    private static String errorKey(NotebookAccess.Result result) {
        String key = result.messageKey();
        return key != null && key.startsWith("mapscribble.notebook.error.") ? key
                : "mapscribble.notebook.error.save_failed";
    }

    private void cancelRename(boolean byKey) {
        renameError = null;
        endRename(byKey);
    }

    /**
     * 名前の変更を終える。キーボード（Enter で決めた・Esc で取りやめた）で終えた時は、始めたボタンへフォーカスを戻す
     * （フォーカスを無くすと、次の Tab が分ける切替からやり直しになるため）。マウスで「決定」「キャンセル」を押した時は
     * バニラのまま（押したボタンにフォーカスが残る）なので、フォーカスを触らない。
     */
    private void endRename(boolean byKey) {
        renaming = false;
        renameEdit.visible = false;
        renameEdit.active = false;
        refresh();
        if (byKey && renameStarter != null) {
            setFocused(renameStarter);
        } else if (getFocused() == renameEdit) {
            setFocused(null);
        }
    }

    private void onMoveAllButton() {
        if (movedCount > 0 && !moveUndone && extras.canUndoMove()) {
            undoMove();
        } else {
            askMove(true);
        }
    }

    /** バニラの確認画面を出す。移したメモは他のサーバーに出なくなるので、利用者が確かめる場面を作る。 */
    private void askMove(boolean all) {
        NotebookAccess.Info current = inUse();
        if (current == null || busy() || renaming) {
            return;
        }
        int n = all ? extras.legacyHere() : extras.legacyVisible();
        if (n <= 0) {
            return;
        }
        String shownName = NotebookNames.display(current.name());
        Component message = Component.translatable(
                all ? "mapscribble.notebook.confirm.all" : "mapscribble.notebook.confirm.visible", n, shownName);
        ScreenCompat.setScreen(new ConfirmScreen(yes -> {
            ScreenCompat.setScreen(this);
            if (yes) {
                doMove(all, n, shownName);
            }
        }, Component.translatable("mapscribble.notebook.confirm.title"), message,
                Component.translatable("mapscribble.notebook.confirm.yes"), Component.translatable("gui.cancel")));
    }

    private void doMove(boolean all, int n, String name) {
        moving = true;
        moveFailed = false;
        refresh();
        extras.move(all ? null : extras.legacyVisibleIds(), result -> {
            moving = false;
            if (result.ok()) {
                movedCount = n;
                movedName = name;
                moveUndone = false;
                moveFailed = false;
            } else {
                moveFailed = true;
            }
            refresh();
        });
    }

    private void undoMove() {
        moving = true;
        refresh();
        extras.undoMove(result -> {
            moving = false;
            if (result.ok()) {
                moveUndone = true;
                moveFailed = false;
            } else {
                moveFailed = true;
            }
            refresh();
        });
    }

    // ---- 閉じる ----

    private void close() {
        if (renaming) {
            cancelRename(false);
        }
        if (callbacks != null) {
            callbacks.onClosed(new Outcome(switchedTo, moveUndone ? 0 : movedCount, moveUndone, moveFailed));
        }
        ScreenCompat.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (renaming) {
            cancelRename(false);
            return;
        }
        if (busy()) {
            return;
        }
        close();
    }

    //? if >=1.21.11 {
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (handleRenameKey(event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleRenameKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}

    /** 名前を変えている間だけ、Enter で決め、Esc で取りやめる（画面は閉じない）。 */
    private boolean handleRenameKey(int keyCode) {
        if (!renaming) {
            return false;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            confirmRename(true);
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            cancelRename(true);
            return true;
        }
        return false;
    }

    //? if >=1.21.11 {
    @Override
    public void resize(int newWidth, int newHeight) {
        parent.resize(newWidth, newHeight);
        super.resize(newWidth, newHeight);
    }
    //?} else {
    /*@Override
    public void resize(Minecraft mc, int newWidth, int newHeight) {
        parent.resize(mc, newWidth, newHeight);
        super.resize(mc, newWidth, newHeight);
    }
    *///?}

    // ---- 描画 ----

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        renderCurtainAndPanelBg(Gfx.of(g), partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        renderPanelForeground(Gfx.of(g), mouseX, mouseY);
    }
    //?} elif >=1.20.5 {
    /*@Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderCurtainAndPanelBg(Gfx.of(g), partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderPanelForeground(Gfx.of(g), mouseX, mouseY);
    }
    *///?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        renderCurtainAndPanelBg(Gfx.of(g), 0f);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderPanelForeground(Gfx.of(g), mouseX, mouseY);
    }
    *///?}

    private void renderCurtainAndPanelBg(Gfx g, float partialTick) {
        if (!renderParent(g, partialTick)) {
            //? if >=26.1 {
            super.extractBackground(g.raw(), -1, -1, partialTick);
            //?} elif >=1.20.5 {
            /*super.renderBackground(g.raw(), -1, -1, partialTick);
            *///?} else {
            /*super.renderBackground(g.raw());
            *///?}
        }
        GuiCompat.nextLayer(g);
        GuiCompat.fill(g, 0, 0, width, height, CURTAIN);
        int px = layout.panelX(width);
        int py = layout.panelY(height);
        GuiCompat.fill(g, px, py, px + NotebookLayout.PANEL_WIDTH, py + layout.panelHeight(), PANEL_BG);
        GuiCompat.outline(g, px, py, NotebookLayout.PANEL_WIDTH, layout.panelHeight(), PANEL_EDGE);
    }

    private boolean renderParent(Gfx g, float partialTick) {
        if (parentFailed) {
            return false;
        }
        try {
            //? if >=26.1 {
            parent.extractRenderState(g.raw(), -1, -1, partialTick);
            //?} else {
            /*parent.render(g.raw(), -1, -1, partialTick);
            *///?}
            return true;
        } catch (RuntimeException e) {
            parentFailed = true;
            MapScribble.LOGGER.warn("MapScribble: 親の全画面マップを背後に描けなかったため、バニラの背景に切り替えます", e);
            return false;
        }
    }

    private void renderPanelForeground(Gfx g, int mouseX, int mouseY) {
        truncated.clear();
        int px = layout.panelX(width);
        int py = layout.panelY(height);
        int left = px + NotebookLayout.MARGIN;
        NotebookPanel.Kind kind = kind();

        GuiCompat.centeredText(g, font, title, px + NotebookLayout.PANEL_WIDTH / 2, py + layout.titleY(), WHITE, false);

        String address = extras.address();
        if (address != null && !address.isEmpty()) {
            String full = Component.translatable("mapscribble.notebook.address", address).getString();
            String shown = NotebookPanel.ellipsizeMiddle(full, NotebookLayout.CONTENT_WIDTH, font::width);
            GuiCompat.centeredText(g, font, Component.literal(shown), px + NotebookLayout.PANEL_WIDTH / 2,
                    py + layout.addressY(), GRAY, false);
            if (!shown.equals(full)) {
                int w = font.width(shown);
                truncated.add(new Truncated(px + (NotebookLayout.PANEL_WIDTH - w) / 2, py + layout.addressY(), w, full));
            }
        }

        String descKey = NotebookPanel.descKey(kind);
        if (descKey != null) {
            drawLine(g, Component.translatable(descKey).getString(), left, py + layout.descY(),
                    0xFF000000 | NotebookPanel.descColor(kind));
        }

        GuiCompat.text(g, font, Component.translatable("mapscribble.notebook.list"), left, py + layout.labelY(), LABEL,
                false);
        if (!renaming && NotebookPanel.listEnabled(kind)) {
            String hint = Component.translatable("mapscribble.notebook.list.hint").getString();
            GuiCompat.text(g, font, Component.literal(hint), left + NotebookLayout.CONTENT_WIDTH - font.width(hint),
                    py + layout.labelY(), GRAY, false);
        } else if (kind == NotebookPanel.Kind.UNSPLIT && !notebooks().isEmpty()) {
            // 分けていない接続先に記録のメモ帳がある時は、押せない理由を灰色で示す
            String hint = Component.translatable("mapscribble.notebook.list.hint.off").getString();
            GuiCompat.text(g, font, Component.literal(hint), left + NotebookLayout.CONTENT_WIDTH - font.width(hint),
                    py + layout.labelY(), GRAY, false);
        }

        if (renameError != null) {
            drawLine(g, renameErrorText(), left, py + layout.errorY(), 0xFF000000 | HintResolver.COLOR_FAILURE);
        }

        drawLegacyLine(g, kind, left, py + layout.legacyY());

        // 「…」で切った行・長い名前のホバー（全文をバニラのツールチップで出す）
        for (Truncated t : truncated) {
            if (mouseX >= t.x() && mouseX < t.x() + t.w() && mouseY >= t.y() - 1 && mouseY < t.y() + 9) {
                GuiCompat.drawTooltip(g, font, List.of(Component.literal(t.full())), mouseX, mouseY);
                return;
            }
        }
        String fullName = list.truncatedNameAt(mouseX, mouseY);
        if (fullName != null) {
            GuiCompat.drawTooltip(g, font, List.of(Component.literal(fullName)), mouseX, mouseY);
        }
    }

    private void drawLegacyLine(Gfx g, NotebookPanel.Kind kind, int x, int y) {
        if (moving || extras.isMoving()) {
            drawLine(g, Component.translatable("mapscribble.notebook.moving").getString(), x, y, GRAY);
            return;
        }
        if (moveFailed) {
            drawLine(g, Component.translatable("mapscribble.notebook.move_failed").getString(), x, y,
                    0xFF000000 | HintResolver.COLOR_FAILURE);
            return;
        }
        if (movedCount > 0 && !moveUndone) {
            drawLine(g, Component.translatable("mapscribble.notebook.moved", movedCount, movedName).getString(), x, y,
                    WHITE);
            return;
        }
        String key = NotebookPanel.legacyKey(kind, extras.legacyTotal(), extras.legacyOtherDimensions());
        if (key == null) {
            return;
        }
        String text;
        if ("mapscribble.notebook.presplit.dims".equals(key)) {
            text = Component.translatable(key, NotebookPanel.countLabel(extras.legacyHere()),
                    NotebookPanel.countLabel(extras.legacyOtherDimensions())).getString();
        } else {
            // ほかのディメンションに無い時は、合計 = 今のディメンションの件数
            text = Component.translatable(key, NotebookPanel.countLabel(extras.legacyTotal())).getString();
        }
        drawLine(g, text, x, y, NotebookPanel.legacyGray(kind) ? GRAY : LABEL);
    }

    /** 名前を変える失敗の文。重なった既存の名前が分かる時は名前つき。行全体が 286 を超える時だけ名前の末尾を「…」で切る。 */
    private String renameErrorText() {
        if ("mapscribble.notebook.error.duplicate".equals(renameError) && renameErrorName != null) {
            String key = "mapscribble.notebook.error.duplicate.named";
            String full = Component.translatable(key, renameErrorName).getString();
            if (font.width(full) <= NotebookLayout.CONTENT_WIDTH) {
                return full;
            }
            String name = renameErrorName;
            while (!name.isEmpty()) {
                name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
                String candidate = Component.translatable(key, name + "…").getString();
                if (font.width(candidate) <= NotebookLayout.CONTENT_WIDTH) {
                    return candidate;
                }
            }
        }
        return Component.translatable(renameError).getString();
    }

    /** 1 行を幅 286 に収めて描く。収まらない時は「…」で切り、ホバーで全文を出す。 */
    private void drawLine(Gfx g, String text, int x, int y, int color) {
        String shown = NotebookPanel.ellipsize(text, NotebookLayout.CONTENT_WIDTH, font::width);
        GuiCompat.text(g, font, Component.literal(shown), x, y, color, false);
        if (!shown.equals(text)) {
            truncated.add(new Truncated(x, y, font.width(shown), text));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
