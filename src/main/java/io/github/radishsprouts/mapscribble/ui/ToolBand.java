package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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

    private static final Identifier[] TOOL_ICONS = {
            GuiCompat.texture("textures/gui/tool/enclose.png"),
            GuiCompat.texture("textures/gui/tool/line.png"),
            GuiCompat.texture("textures/gui/tool/text.png"),
            GuiCompat.texture("textures/gui/tool/eraser.png"),
            GuiCompat.texture("textures/gui/tool/edit.png")};
    private static final Identifier UNDO_ICON = GuiCompat.texture("textures/gui/tool/undo.png");
    private static final Identifier REDO_ICON = GuiCompat.texture("textures/gui/tool/redo.png");
    private static final Identifier REGION_TEXT_VISIBLE_ICON = GuiCompat.texture("textures/gui/tool/region_text_visible.png");
    private static final Identifier REGION_TEXT_HIDDEN_ICON = GuiCompat.texture("textures/gui/tool/region_text_hidden.png");
    private static final Identifier VISIBLE_ICON = GuiCompat.texture("textures/gui/tool/visible.png");
    private static final Identifier HIDDEN_ICON = GuiCompat.texture("textures/gui/tool/hidden.png");

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
    private boolean tooltipRegionTextVisible;
    private String tooltipKeyLabel;
    private List<Component> tooltip = List.of();

    public ToolBand(DrawingState state, DrawingController controller) {
        this.state = state;
        this.controller = controller;
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
        BandLayout.Cell cell = BandLayout.cellAt(gx, gy, bx, by);
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
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
        switch (cell.kind()) {
            case TOOL -> state.setTool(Tool.values()[cell.index()]);
            case UNDO -> controller.undo();
            case REDO -> controller.redo();
            case REGION_TEXT_VISIBILITY -> state.toggleRegionTextVisible();
            case VISIBILITY -> state.setVisible(!state.isVisible());
            case WIDTH -> state.setWidth(cell.index());
            case TEXT_SIZE -> state.setTextScale(TextSize.values()[cell.index()].scale());
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

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, HintResolver.Line hint,
                       float hintAlpha) {
        int bx = state.bandX();
        int by = state.bandY();
        updateHover(mouseX, mouseY, bx, by);

        g.fill(bx, by, bx + BandLayout.WIDTH, by + BandLayout.HEIGHT, BG);
        g.outline(bx, by, BandLayout.WIDTH, BandLayout.HEIGHT, EDGE);
        for (BandLayout.Separator s : BandLayout.buildSeparators(bx, by)) {
            g.fill(s.x(), s.y(), s.x() + s.w(), s.y() + s.h(), SEPARATOR);
        }

        int rgb = 0xFF000000 | state.color();
        for (BandLayout.Cell cell : BandLayout.buildCells(bx, by)) {
            switch (cell.kind()) {
                case TOOL -> drawButton(g, cell, state.tool().ordinal() == cell.index(), true,
                        TOOL_ICONS[cell.index()]);
                case UNDO -> drawButton(g, cell, false, controller.canUndo(), UNDO_ICON);
                case REDO -> drawButton(g, cell, false, controller.canRedo(), REDO_ICON);
                case REGION_TEXT_VISIBILITY -> drawButton(g, cell, false, true,
                        state.isRegionTextVisible() ? REGION_TEXT_VISIBLE_ICON : REGION_TEXT_HIDDEN_ICON);
                case VISIBILITY -> drawButton(g, cell, false, true, state.isVisible() ? VISIBLE_ICON : HIDDEN_ICON);
                case WIDTH -> drawWidth(g, cell, rgb);
                case TEXT_SIZE -> drawTextSize(g, font, cell);
                case COLOR -> drawSwatch(g, cell);
            }
        }

        showTooltip(g, font, mouseX, mouseY, hint);
    }

    private void updateHover(int mouseX, int mouseY, int bx, int by) {
        if (mouseX == lastMouseX && mouseY == lastMouseY) {
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hover = BandLayout.cellAt(mouseX, mouseY, bx, by);
    }

    private void drawButton(GuiGraphicsExtractor g, BandLayout.Cell cell, boolean selected, boolean enabled,
                            Identifier icon) {
        int face = buttonFace(cell, selected, enabled);
        int border = buttonBorder(cell, selected, enabled);
        int tint = buttonIcon(cell, selected, enabled);
        g.fill(cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        g.outline(cell.x(), cell.y(), cell.w(), cell.h(), border);
        GuiCompat.blitIcon(g, icon, cell.x() + 2, cell.y() + 2, 16, tint);
    }

    private void drawWidth(GuiGraphicsExtractor g, BandLayout.Cell cell, int rgb) {
        boolean selected = state.width() == cell.index();
        int face = buttonFace(cell, selected, true);
        int border = buttonBorder(cell, selected, true);
        g.fill(cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        g.outline(cell.x(), cell.y(), cell.w(), cell.h(), border);
        int thickness = cell.index();
        int top = cell.y() + (cell.h() - thickness) / 2;
        g.fill(cell.x() + 2, top, cell.x() + cell.w() - 2, top + thickness, rgb);
    }

    private void drawTextSize(GuiGraphicsExtractor g, Font font, BandLayout.Cell cell) {
        TextSize size = TextSize.values()[cell.index()];
        boolean selected = TextSize.nearest(state.textScale()) == size;
        int face = buttonFace(cell, selected, true);
        int border = buttonBorder(cell, selected, true);
        g.fill(cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), face);
        g.outline(cell.x(), cell.y(), cell.w(), cell.h(), border);

        String label = I18n.get(size.translationKey());
        if (label.length() > 1 && !label.startsWith("小") && !label.startsWith("中") && !label.startsWith("大")) {
            label = label.substring(0, 1);
        }
        int textW = font.width(label);
        int textX = cell.x() + (cell.w() - textW) / 2;
        int textY = cell.y() + (cell.h() - font.lineHeight) / 2 + 1;
        int color = selected ? WHITE : (hover == cell ? WHITE : ICON_NORMAL);
        g.text(font, label, textX, textY, color, true);
    }

    private void drawSwatch(GuiGraphicsExtractor g, BandLayout.Cell cell) {
        NoteColor color = NoteColor.list().get(cell.index());
        boolean selected = (state.color() & 0xFFFFFF) == color.rgb();
        boolean hovered = hover == cell;
        if (selected) {
            g.outline(cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, WHITE);
        } else if (hovered) {
            g.outline(cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, SWATCH_HOVER_RING);
        }
        g.fill(cell.x(), cell.y(), cell.x() + cell.w(), cell.y() + cell.h(), 0xFF000000 | color.rgb());
        g.outline(cell.x(), cell.y(), cell.w(), cell.h(), selected ? 0xFF000000 : SWATCH_BORDER);
    }

    private int buttonFace(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return SELECTED_FACE;
        }
        if (!enabled) {
            return FACE_DISABLED;
        }
        if (pressed == cell && hover == cell) {
            return FACE_PRESSED;
        }
        return hover == cell ? FACE_HOVER : FACE_NORMAL;
    }

    private int buttonBorder(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return WHITE;
        }
        if (!enabled) {
            return BORDER_DISABLED;
        }
        if (pressed == cell && hover == cell) {
            return WHITE;
        }
        return hover == cell ? BORDER_HOVER : BORDER_NORMAL;
    }

    private int buttonIcon(BandLayout.Cell cell, boolean selected, boolean enabled) {
        if (selected) {
            return WHITE;
        }
        if (!enabled) {
            return ICON_DISABLED;
        }
        return hover == cell ? WHITE : ICON_NORMAL;
    }

    // ---- ツールチップ ----

    private void showTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, HintResolver.Line hint) {
        BandLayout.Cell cell = hover;
        if (cell == null) {
            return;
        }
        boolean undo = controller.canUndo();
        boolean redo = controller.canRedo();
        boolean visible = state.isVisible();
        boolean regionTextVisible = state.isRegionTextVisible();
        String keyLabel = cell.kind() == BandLayout.Kind.TOOL ? ScribbleKeys.toolKeyLabel(Tool.values()[cell.index()]) : null;
        if (cell != tooltipCell || undo != tooltipUndo || redo != tooltipRedo || visible != tooltipVisible
                || regionTextVisible != tooltipRegionTextVisible
                || !java.util.Objects.equals(keyLabel, tooltipKeyLabel)) {
            tooltipKeyLabel = keyLabel;
            tooltipCell = cell;
            tooltipUndo = undo;
            tooltipRedo = redo;
            tooltipVisible = visible;
            tooltipRegionTextVisible = regionTextVisible;
            tooltip = buildTooltip(cell, undo, redo, visible, regionTextVisible);
        }
        g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    private static List<Component> buildTooltip(BandLayout.Cell cell, boolean canUndo, boolean canRedo,
                                                boolean visible, boolean regionTextVisible) {
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
            case REGION_TEXT_VISIBILITY -> lines.add(Component.translatable(
                    regionTextVisible ? "mapscribble.region_text.hide" : "mapscribble.region_text.show"));
            case VISIBILITY -> lines.add(Component.translatable(
                    visible ? "mapscribble.visibility.hide" : "mapscribble.visibility.show"));
            case WIDTH -> lines.add(Component.translatable(switch (cell.index()) {
                case 1 -> "mapscribble.width.thin";
                case 2 -> "mapscribble.width.medium";
                default -> "mapscribble.width.thick";
            }));
            case TEXT_SIZE -> lines.add(Component.translatable("mapscribble.text_size.tooltip",
                    Component.translatable(TextSize.values()[cell.index()].translationKey())));
            case COLOR -> lines.add(Component.translatable("mapscribble.color.tooltip",
                    Component.translatable(NoteColor.values()[cell.index()].translationKey())));
        }
        return lines;
    }

    private static Component gray(Component c) {
        return c.copy().withColor(GRAY);
    }
}
