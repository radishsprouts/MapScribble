package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.compat.Gfx;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * ツールの帯。手書きモード中だけ、全画面マップの描画イベントの中で描く。
 * 配置は {@link BandLayout} で、マウスドラッグにより画面内の任意の位置へ移動可能。
 */
public final class ToolBand {
    static final int BG = 0xCC101418;
    static final int EDGE = 0x33FFFFFF;
    static final int SEPARATOR = 0x40FFFFFF;
    static final int SELECTED_FACE = 0xFF2E7DD7;
    static final int WHITE = 0xFFFFFFFF;

    private static final int FACE_NORMAL = 0x26FFFFFF;
    private static final int FACE_HOVER = 0x4DFFFFFF;
    private static final int FACE_PRESSED = 0x73FFFFFF;
    private static final int FACE_DISABLED = 0x0FFFFFFF;
    private static final int BORDER_NORMAL = 0x73FFFFFF;
    private static final int BORDER_HOVER = 0xB3FFFFFF;
    private static final int BORDER_DISABLED = 0x26FFFFFF;
    private static final int ICON_NORMAL = 0xFFE6E6E6;
    private static final int ICON_DISABLED = 0x59E6E6E6;
    private static final int SWATCH_BORDER = 0x99000000;
    private static final int SWATCH_HOVER_RING = 0xFFA0A0A0;
    private static final int GRAY = 0xA0A0A0;
    /** 設定の無い道具の下段の灰色の1行。 */
    private static final int NO_SETTINGS_GRAY = 0xFFB4B4B4;
    /** 塗りのボタンで、選んでいる色が黒の時だけ使う縁と内側の色。 */
    private static final int FILL_BLACK_SUBSTITUTE = 0xE6E6E6;

    //? if >=1.21.11 {
    private static final Identifier[] TOOL_ICONS = {
            GuiCompat.texture("textures/gui/tool/enclose.png"),
            GuiCompat.texture("textures/gui/tool/line.png"),
            GuiCompat.texture("textures/gui/tool/text.png"),
            GuiCompat.texture("textures/gui/tool/eraser.png"),
            GuiCompat.texture("textures/gui/tool/edit.png")};
    private static final Identifier UNDO_ICON = GuiCompat.texture("textures/gui/tool/undo.png");
    private static final Identifier REDO_ICON = GuiCompat.texture("textures/gui/tool/redo.png");
    private static final Identifier TEXT_VISIBLE_ICON = GuiCompat.texture("textures/gui/tool/text_visible.png");
    private static final Identifier TEXT_HIDDEN_ICON = GuiCompat.texture("textures/gui/tool/text_hidden.png");
    private static final Identifier VISIBLE_ICON = GuiCompat.texture("textures/gui/tool/visible.png");
    private static final Identifier HIDDEN_ICON = GuiCompat.texture("textures/gui/tool/hidden.png");
    private static final Identifier EXIT_ICON = GuiCompat.texture("textures/gui/tool/exit.png");
    /** メニューのアイコン（素材 #15）。ファイルが無い間はバニラの欠けたテクスチャの絵になるだけで、落ちない。 */
    private static final Identifier MENU_ICON = GuiCompat.texture("textures/gui/tool/menu.png");
    //?} else {
    /*private static final ResourceLocation[] TOOL_ICONS = {
            GuiCompat.texture("textures/gui/tool/enclose.png"),
            GuiCompat.texture("textures/gui/tool/line.png"),
            GuiCompat.texture("textures/gui/tool/text.png"),
            GuiCompat.texture("textures/gui/tool/eraser.png"),
            GuiCompat.texture("textures/gui/tool/edit.png")};
    private static final ResourceLocation UNDO_ICON = GuiCompat.texture("textures/gui/tool/undo.png");
    private static final ResourceLocation REDO_ICON = GuiCompat.texture("textures/gui/tool/redo.png");
    private static final ResourceLocation TEXT_VISIBLE_ICON = GuiCompat.texture("textures/gui/tool/text_visible.png");
    private static final ResourceLocation TEXT_HIDDEN_ICON = GuiCompat.texture("textures/gui/tool/text_hidden.png");
    private static final ResourceLocation VISIBLE_ICON = GuiCompat.texture("textures/gui/tool/visible.png");
    private static final ResourceLocation HIDDEN_ICON = GuiCompat.texture("textures/gui/tool/hidden.png");
    private static final ResourceLocation EXIT_ICON = GuiCompat.texture("textures/gui/tool/exit.png");
    private static final ResourceLocation MENU_ICON = GuiCompat.texture("textures/gui/tool/menu.png");
    *///?}

    private final DrawingState state;
    private final DrawingController controller;

    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private BandLayout.Cell hover;
    private BandLayout.Cell pressed;

    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private BandLayout.Cell tooltipCell;
    private boolean tooltipUndo;
    private boolean tooltipRedo;
    private boolean tooltipVisible;
    private boolean tooltipTextVisible;
    private String tooltipKeyLabel;
    private List<Component> tooltip = List.of();

    /** メニューを開いているか。開いている間はメニューのボタンを選択中の見え方にし、帯のホバーとツールチップを止める。 */
    private boolean menuOpen;
    private Runnable menuButtonHandler = () -> {
    };

    // 描画のたびに同じ表を作り直さないための入れ物（帯の位置か道具が変わった時だけ作り直す）
    private int cachedBx = Integer.MIN_VALUE;
    private int cachedBy = Integer.MIN_VALUE;
    private Tool cachedTool;
    private List<BandLayout.Cell> cachedCells = List.of();
    private List<BandLayout.Separator> cachedSeparators = List.of();

    public ToolBand(DrawingState state, DrawingController controller) {
        this.state = state;
        this.controller = controller;
    }

    /** メニューのボタンが押された時に呼ぶ処理（メニューの開閉は呼び出し側が持つ）。 */
    public void setMenuButtonHandler(Runnable handler) {
        this.menuButtonHandler = handler;
    }

    public void setMenuOpen(boolean open) {
        this.menuOpen = open;
        if (open) {
            hover = null;
        }
        lastMouseX = Integer.MIN_VALUE;
    }

    private List<BandLayout.Cell> cells(int bx, int by) {
        refreshCache(bx, by);
        return cachedCells;
    }

    private void refreshCache(int bx, int by) {
        Tool tool = state.tool();
        if (bx != cachedBx || by != cachedBy || tool != cachedTool) {
            cachedBx = bx;
            cachedBy = by;
            cachedTool = tool;
            cachedCells = BandLayout.buildCells(bx, by, tool);
            cachedSeparators = BandLayout.buildSeparators(bx, by, tool);
        }
    }

    // ---- 入力 ----

    /**
     * 帯の上のクリック。帯の上なら true を返し、地図へ渡さない。左ボタンだけが操作になる。
     */
    public boolean click(double gx, double gy, boolean leftButton) {
        int bx = state.bandX();
        int by = state.bandY();
        if (!BandLayout.contains(gx, gy, bx, by)) {
            return false;
        }
        if (!leftButton) {
            return true;
        }
        BandLayout.Cell cell = null;
        for (BandLayout.Cell c : cells(bx, by)) {
            if (c.contains(gx, gy)) {
                cell = c;
                break;
            }
        }
        if (cell == null) {
            // ボタン以外の余白をクリックした場合はドラッグ移動を開始
            dragging = true;
            dragOffsetX = gx - bx;
            dragOffsetY = gy - by;
            return true;
        }
        if (!isEnabled(cell)) {
            return true;
        }
        pressed = cell;
        GuiCompat.playButtonClickSound();
        switch (cell.kind()) {
            case TOOL -> state.setTool(Tool.values()[cell.index()]);
            case UNDO -> controller.undo();
            case REDO -> controller.redo();
            case TEXT_VISIBILITY -> state.toggleTextVisible();
            case VISIBILITY -> state.setVisible(!state.isVisible());
            case EXIT -> {
                // 描きかけの線は地図に残さず捨ててから、手書きモードを終える
                controller.cancelStroke();
                state.toggleMode();
            }
            case MENU -> menuButtonHandler.run();
            case WIDTH -> state.setWidth(cell.index());
            case TEXT_SIZE -> state.setTextScale(TextSize.values()[cell.index()].scale());
            case FILL -> state.setFillOpacity(FillLevel.values()[cell.index()].opacity());
            case COLOR -> state.setColor(NoteColor.values()[cell.index()].rgb());
        }
        return true;
    }

    public void drag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!dragging) {
            return;
        }
        int newX = (int) Math.round(mouseX - dragOffsetX);
        int newY = (int) Math.round(mouseY - dragOffsetY);
        newX = Math.max(0, Math.min(Math.max(0, screenWidth - BandLayout.WIDTH), newX));
        newY = Math.max(0, Math.min(Math.max(0, screenHeight - BandLayout.HEIGHT), newY));
        state.setBandPosition(newX, newY);
    }

    public boolean isDragging() {
        return dragging;
    }

    /** マウスのボタンを離した。押下中・ドラッグ中の状態を解除。 */
    public void release() {
        pressed = null;
        dragging = false;
    }

    private boolean isEnabled(BandLayout.Cell cell) {
        return switch (cell.kind()) {
            case UNDO -> controller.canUndo();
            case REDO -> controller.canRedo();
            default -> true;
        };
    }

    // ---- 描画 ----

    public void render(Gfx g, Font font, int mouseX, int mouseY, HintResolver.Line hint,
                       float hintAlpha) {
        int bx = state.bandX();
        int by = state.bandY();
        List<BandLayout.Cell> cells = cells(bx, by);
        updateHover(cells, mouseX, mouseY);

        GuiCompat.fill(g, bx, by, bx + BandLayout.WIDTH, by + BandLayout.HEIGHT, BG);
        GuiCompat.outline(g, bx, by, BandLayout.WIDTH, BandLayout.HEIGHT, EDGE);
        for (BandLayout.Separator s : cachedSeparators) {
            GuiCompat.fill(g, s.x(), s.y(), s.x() + s.w(), s.y() + s.h(), SEPARATOR);
        }

        int rgb = 0xFF000000 | state.color();
        for (BandLayout.Cell cell : cells) {
            switch (cell.kind()) {
                case TOOL -> drawButton(g, cell, state.tool().ordinal() == cell.index(), true,
                        TOOL_ICONS[cell.index()]);
                case UNDO -> drawButton(g, cell, false, controller.canUndo(), UNDO_ICON);
                case REDO -> drawButton(g, cell, false, controller.canRedo(), REDO_ICON);
                case TEXT_VISIBILITY -> drawButton(g, cell, false, true,
                        state.isTextVisible() ? TEXT_VISIBLE_ICON : TEXT_HIDDEN_ICON);
                case VISIBILITY -> drawButton(g, cell, false, true, state.isVisible() ? VISIBLE_ICON : HIDDEN_ICON);
                // メニューを開いている間は選択中の見え方（どのボタンから開いたかの手掛かり）
                case MENU -> drawButton(g, cell, menuOpen, true, MENU_ICON);
                case EXIT -> drawButton(g, cell, false, true, EXIT_ICON);
                case WIDTH -> drawWidth(g, cell, rgb);
                case TEXT_SIZE -> drawTextSize(g, font, cell);
                case FILL -> drawFill(g, cell);
                case COLOR -> drawSwatch(g, cell);
            }
        }
        if (!BandLayout.hasSettings(state.tool())) {
            // 空白のままだと「壊れた／読み込み中」に見えるので、押せない灰色の1行で理由を示す
            GuiCompat.text(g, font, Component.translatable("mapscribble.band.no_settings"),
                    bx + BandLayout.NO_SETTINGS_X, by + BandLayout.NO_SETTINGS_Y, NO_SETTINGS_GRAY, false);
        }

        if (!menuOpen) {
            showTooltip(g, font, mouseX, mouseY, hint);
        }
    }

    /** 毎フレーム、いまのマウス位置のボタンを引き直す（道具の切替でボタンの表が変わるため）。メニュー中は無し。 */
    private void updateHover(List<BandLayout.Cell> cells, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hover = null;
        if (menuOpen) {
            return;
        }
        for (BandLayout.Cell c : cells) {
            if (c.contains(mouseX, mouseY)) {
                hover = c;
                return;
            }
        }
    }

    /** アイコンを置く y。アイコン 16×16 はボタンの左上から (1,1)。終了ボタンだけ縦長（35）なので上 9 の位置に置く。 */
    private static int iconY(BandLayout.Cell cell) {
        return cell.kind() == BandLayout.Kind.EXIT ? cell.y() + 9 : cell.y() + 1;
    }

    //? if >=1.21.11 {
    private void drawButton(Gfx g, BandLayout.Cell cell, boolean selected, boolean enabled,
                            Identifier icon) {
        int face = buttonFace(cell, selected, enabled);
        int border = buttonBorder(cell, selected, enabled);
        int tint = buttonIcon(cell, selected, enabled);
        GuiCompat.fill(g, cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        GuiCompat.outline(g, cell.x(), cell.y(), cell.w(), cell.h(), border);
        GuiCompat.blitIcon(g, icon, cell.x() + 1, iconY(cell), 16, tint);
    }
    //?} else {
    /*private void drawButton(Gfx g, BandLayout.Cell cell, boolean selected, boolean enabled,
                            ResourceLocation icon) {
        int face = buttonFace(cell, selected, enabled);
        int border = buttonBorder(cell, selected, enabled);
        int tint = buttonIcon(cell, selected, enabled);
        GuiCompat.fill(g, cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        GuiCompat.outline(g, cell.x(), cell.y(), cell.w(), cell.h(), border);
        GuiCompat.blitIcon(g, icon, cell.x() + 1, iconY(cell), 16, tint);
    }
    *///?}

    private void drawWidth(Gfx g, BandLayout.Cell cell, int rgb) {
        boolean selected = state.width() == cell.index();
        int face = buttonFace(cell, selected, true);
        int border = buttonBorder(cell, selected, true);
        GuiCompat.fill(g, cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        GuiCompat.outline(g, cell.x(), cell.y(), cell.w(), cell.h(), border);
        int thickness = cell.index();
        int top = cell.y() + (cell.h() - thickness) / 2;
        GuiCompat.fill(g, cell.x() + 2, top, cell.x() + cell.w() - 2, top + thickness, rgb);
    }

    private void drawTextSize(Gfx g, Font font, BandLayout.Cell cell) {
        TextSize size = TextSize.values()[cell.index()];
        boolean selected = TextSize.nearest(state.textScale()) == size;
        int face = buttonFace(cell, selected, true);
        int border = buttonBorder(cell, selected, true);
        GuiCompat.fill(g, cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        GuiCompat.outline(g, cell.x(), cell.y(), cell.w(), cell.h(), border);
        String label = I18n.get(size.translationKey());
        if (label.length() > 1 && !label.startsWith("小") && !label.startsWith("中") && !label.startsWith("大")) {
            label = label.substring(0, 1);
        }
        int textW = font.width(label);
        int textX = cell.x() + (cell.w() - textW) / 2;
        int textY = cell.y() + (cell.h() - font.lineHeight) / 2 + 1;
        int color = selected ? WHITE : (cell.equals(hover) ? WHITE : ICON_NORMAL);
        GuiCompat.text(g, font, Component.literal(label), textX, textY, color, true);
    }

    /** 塗りのボタン。左上から (2,2) に 12×9 の四角を、選んでいる色の縁と、塗りの濃さの内側で描く。 */
    private void drawFill(Gfx g, BandLayout.Cell cell) {
        FillLevel level = FillLevel.values()[cell.index()];
        boolean selected = FillLevel.nearest(state.fillOpacity()) == level;
        GuiCompat.fill(g, cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), buttonFace(cell, selected, true));
        GuiCompat.outline(g, cell.x(), cell.y(), cell.w(), cell.h(), buttonBorder(cell, selected, true));
        int rgb = state.color() & 0xFFFFFF;
        if (rgb == 0) {
            rgb = FILL_BLACK_SUBSTITUTE;
        }
        int x = cell.x() + 2;
        int y = cell.y() + 2;
        GuiCompat.outline(g, x, y, 12, 9, 0xFF000000 | rgb);
        int alpha = Math.round(level.opacity() * 255f);
        if (alpha > 0) {
            GuiCompat.fill(g, x + 1, y + 1, x + 11, y + 8, (alpha << 24) | rgb);
        }
    }

    private void drawSwatch(Gfx g, BandLayout.Cell cell) {
        NoteColor color = NoteColor.list().get(cell.index());
        boolean selected = (state.color() & 0xFFFFFF) == color.rgb();
        boolean hovered = cell.equals(hover);
        if (selected) {
            GuiCompat.outline(g, cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, WHITE);
        } else if (hovered) {
            GuiCompat.outline(g, cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, SWATCH_HOVER_RING);
        }
        GuiCompat.fill(g, cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), 0xFF000000 | color.rgb());
        GuiCompat.outline(g, cell.x(), cell.y(), cell.w(), cell.h(), selected ? 0xFF000000 : SWATCH_BORDER);
    }

    private int buttonFace(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return SELECTED_FACE;
        }
        if (!enabled) {
            return FACE_DISABLED;
        }
        if (cell.equals(pressed) && cell.equals(hover)) {
            return FACE_PRESSED;
        }
        return cell.equals(hover) ? FACE_HOVER : FACE_NORMAL;
    }

    private int buttonBorder(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return WHITE;
        }
        if (!enabled) {
            return BORDER_DISABLED;
        }
        if (cell.equals(pressed) && cell.equals(hover)) {
            return WHITE;
        }
        return cell.equals(hover) ? BORDER_HOVER : BORDER_NORMAL;
    }

    private int buttonIcon(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return WHITE;
        }
        if (!enabled) {
            return ICON_DISABLED;
        }
        return cell.equals(hover) ? WHITE : ICON_NORMAL;
    }

    // ---- ツールチップ ----

    private void showTooltip(Gfx g, Font font, int mouseX, int mouseY, HintResolver.Line hint) {
        BandLayout.Cell cell = hover;
        if (cell == null) {
            return;
        }
        boolean undo = controller.canUndo();
        boolean redo = controller.canRedo();
        boolean visible = state.isVisible();
        boolean textVisible = state.isTextVisible();
        String keyLabel = cell.kind() == BandLayout.Kind.TOOL ? ScribbleKeys.toolKeyLabel(Tool.values()[cell.index()]) : null;
        if (cell != tooltipCell || undo != tooltipUndo || redo != tooltipRedo || visible != tooltipVisible
                || textVisible != tooltipTextVisible
                || !java.util.Objects.equals(keyLabel, tooltipKeyLabel)) {
            tooltipKeyLabel = keyLabel;
            tooltipCell = cell;
            tooltipUndo = undo;
            tooltipRedo = redo;
            tooltipVisible = visible;
            tooltipTextVisible = textVisible;
            tooltip = buildTooltip(cell, undo, redo, visible, textVisible);
        }
        GuiCompat.drawTooltip(g, font, tooltip, mouseX, mouseY);
    }

    private static List<Component> buildTooltip(BandLayout.Cell cell, boolean canUndo, boolean canRedo,
                                                boolean visible, boolean textVisible) {
        List<Component> lines = new ArrayList<>(2);
        switch (cell.kind()) {
            case TOOL -> {
                Tool tool = Tool.values()[cell.index()];
                String key = HintResolver.toolKey(tool);
                String keyLabel = ScribbleKeys.toolKeyLabel(tool);
                lines.add(keyLabel == null ? Component.translatable(key)
                        : Component.translatable("mapscribble.tool.tooltip", Component.translatable(key), keyLabel));
                lines.add(gray(Component.translatable(key + ".desc")));
            }
            case UNDO -> {
                lines.add(Component.translatable("mapscribble.undo"));
                if (!canUndo) {
                    lines.add(gray(Component.translatable("mapscribble.undo.none")));
                }
            }
            case REDO -> {
                lines.add(Component.translatable("mapscribble.redo"));
                if (!canRedo) {
                    lines.add(gray(Component.translatable("mapscribble.redo.none")));
                }
            }
            case TEXT_VISIBILITY -> {
                String key = textVisible ? "mapscribble.text_visibility.hide" : "mapscribble.text_visibility.show";
                lines.add(Component.translatable(key));
                lines.add(gray(Component.translatable(key + ".desc")));
            }
            case VISIBILITY -> lines.add(Component.translatable(
                    visible ? "mapscribble.visibility.hide" : "mapscribble.visibility.show"));
            case MENU -> {
                lines.add(Component.translatable("mapscribble.menu"));
                lines.add(gray(Component.translatable("mapscribble.menu.desc")));
            }
            case EXIT -> {
                lines.add(Component.translatable("mapscribble.button.toggle.on"));
                lines.add(gray(Component.translatable("mapscribble.button.toggle.on.desc")));
            }
            case WIDTH -> lines.add(Component.translatable(switch (cell.index()) {
                case 1 -> "mapscribble.width.thin";
                case 2 -> "mapscribble.width.medium";
                default -> "mapscribble.width.thick";
            }));
            case TEXT_SIZE -> lines.add(Component.translatable("mapscribble.text_size.tooltip",
                    Component.translatable(TextSize.values()[cell.index()].translationKey())));
            case FILL -> lines.add(Component.translatable("mapscribble.fill.tooltip",
                    Component.translatable(FillLevel.values()[cell.index()].translationKey())));
            case COLOR -> lines.add(Component.translatable("mapscribble.color.tooltip",
                    Component.translatable(NoteColor.values()[cell.index()].translationKey())));
        }
        return lines;
    }

    private static Component gray(Component c) {
        return GuiCompat.withColor(c.copy(), GRAY);
    }
}
