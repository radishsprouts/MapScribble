package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

/**
 * 編集ダイアログの色を選ぶ行。
 */
public final class ColorSwatchRow extends AbstractWidget {
    private static final int FOCUS_RING = 0xFFFFFFFF;
    private static final int SELECTED_BORDER = 0xFF000000;
    private static final int HOVER_BORDER = 0xFFA0A0A0;
    private static final int NORMAL_BORDER = 0xFF303030;

    private int selectedRgb;
    private final Consumer<Integer> onChange;

    public ColorSwatchRow(int x, int y, int initialRgb, Consumer<Integer> onChange) {
        super(x, y, EditLayout.SWATCH_STEP * NoteColor.values().length, EditLayout.SWATCH_SIZE,
                Component.translatable("mapscribble.screen.swatch.narration"));
        this.selectedRgb = initialRgb;
        this.onChange = onChange;
    }

    public int selectedRgb() {
        return selectedRgb;
    }

    private int indexAt(double mouseX) {
        int rel = (int) Math.round(mouseX) - getX();
        if (rel < 0) {
            return -1;
        }
        int i = rel / EditLayout.SWATCH_STEP;
        int rem = rel % EditLayout.SWATCH_STEP;
        if (i < 0 || i >= NoteColor.values().length || rem >= EditLayout.SWATCH_SIZE) {
            return -1;
        }
        return i;
    }

    private void select(int index) {
        selectedRgb = NoteColor.values()[index].rgb();
        onChange.accept(selectedRgb);
    }

    //? if >=26.1 {
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        renderSwatches(g, mouseX, mouseY);
    }
    //?} else {
    /*@Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderSwatches(g, mouseX, mouseY);
    }
    *///?}

    //? if >=26.1 {
    private void renderSwatches(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    //?} else {
    /*private void renderSwatches(GuiGraphics g, int mouseX, int mouseY) {
    *///?}
        int hovered = isHovered() ? indexAt(mouseX) : -1;
        List<NoteColor> colors = NoteColor.list();
        for (int i = 0; i < colors.size(); i++) {
            int x = getX() + i * EditLayout.SWATCH_STEP;
            int y = getY();
            int size = EditLayout.SWATCH_SIZE;
            boolean selected = colors.get(i).rgb() == selectedRgb;
            if (isFocused() && selected) {
                GuiCompat.outline(g, x - 1, y - 1, size + 2, size + 2, FOCUS_RING);
            }
            int border = selected ? SELECTED_BORDER : (i == hovered ? HOVER_BORDER : NORMAL_BORDER);
            GuiCompat.fill(g, x, y, x + size, y + size, 0xFF000000 | colors.get(i).rgb());
            GuiCompat.outline(g, x, y, size, size, border);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && indexAt(mouseX) >= 0 && mouseY >= getY() && mouseY < getY() + EditLayout.SWATCH_SIZE;
    }

    //? if >=1.21.11 {
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int i = indexAt(event.x());
        if (i >= 0) {
            select(i);
        }
    }
    //?} else {
    /*@Override
    public void onClick(double mouseX, double mouseY) {
        int i = indexAt(mouseX);
        if (i >= 0) {
            select(i);
        }
    }
    *///?}

    //? if >=1.21.11 {
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
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        if (keyCode == 263) { // GLFW_KEY_LEFT
            select((current + colors.length - 1) % colors.length);
            return true;
        }
        if (keyCode == 262) { // GLFW_KEY_RIGHT
            select((current + 1) % colors.length);
            return true;
        }
        return false;
    }
    *///?}
}
