package io.github.radishsprouts.mapscribble.ui;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * 文字入力の画面の色の見本（画面設計書 3.2）。16×16 が 10 個、間隔 2。
 * Tab の止まり場所は 10 個で 1 つで、フォーカス中は ←→ で色を選ぶ。
 */
final class ColorSwatchRow extends AbstractWidget {
    private static final int FOCUS_RING = 0xFFFFFF55;
    private static final int HOVER_RING = 0xFFA0A0A0;

    private final IntConsumer onChange;
    private int selectedRgb;

    ColorSwatchRow(int x, int y, int selectedRgb, IntConsumer onChange) {
        super(x, y, EditLayout.SWATCH_STEP * NoteColor.values().length - 2, EditLayout.SWATCH_SIZE,
                Component.translatable("mapscribble.screen.label.color", ""));
        this.selectedRgb = selectedRgb & 0xFFFFFF;
        this.onChange = onChange;
    }

    int selectedRgb() {
        return selectedRgb;
    }

    private int indexAt(double mouseX) {
        int i = (int) ((mouseX - getX()) / EditLayout.SWATCH_STEP);
        int within = (int) (mouseX - getX()) - i * EditLayout.SWATCH_STEP;
        if (i < 0 || i >= NoteColor.values().length || within >= EditLayout.SWATCH_SIZE) {
            return -1;
        }
        return i;
    }

    private void select(int index) {
        selectedRgb = NoteColor.values()[index].rgb();
        onChange.accept(selectedRgb);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int hovered = isHovered() ? indexAt(mouseX) : -1;
        List<NoteColor> colors = NoteColor.list();
        for (int i = 0; i < colors.size(); i++) {
            int x = getX() + i * EditLayout.SWATCH_STEP;
            int y = getY();
            int size = EditLayout.SWATCH_SIZE;
            boolean selected = colors.get(i).rgb() == selectedRgb;
            if (isFocused() && selected) {
                // フォーカス中は外側 1px を黄色にする
                g.outline(x - 1, y - 1, size + 2, size + 2, FOCUS_RING);
            } else if (selected) {
                g.outline(x - 1, y - 1, size + 2, size + 2, ToolBand.WHITE);
            } else if (i == hovered) {
                g.outline(x - 1, y - 1, size + 2, size + 2, HOVER_RING);
            }
            g.fill(x, y, x + size, y + size, 0xFF000000 | colors.get(i).rgb());
            g.outline(x, y, size, size, selected ? 0xFF000000 : 0x99000000);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && indexAt(mouseX) >= 0 && mouseY >= getY() && mouseY < getY() + EditLayout.SWATCH_SIZE;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int i = indexAt(event.x());
        if (i >= 0) {
            select(i);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused()) {
            return false;
        }
        NoteColor[] colors = NoteColor.values();
        int current = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i].rgb() == selectedRgb) {
                current = i;
            }
        }
        if (event.isLeft()) {
            select((current + colors.length - 1) % colors.length);
            return true;
        }
        if (event.isRight()) {
            select((current + 1) % colors.length);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
