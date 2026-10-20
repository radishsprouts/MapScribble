package io.github.radishsprouts.mapscribble.jm;

import com.mojang.blaze3d.platform.Window;
import io.github.radishsprouts.mapscribble.geom.Projection;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import net.minecraft.client.Minecraft;

/**
 * JourneyMap の全画面マップから、画面座標とワールド座標の変換（{@link Projection}）を作る。
 * 変換の式そのものは geom/Projection にあり、ここは材料を JourneyMap から取るだけ。
 *
 * <p>材料: 中心 = {@code getCenterBlockX/Z(true)}、1 ブロックの大きさ = {@code UIState.blockSize}、
 * 領域 = ウィンドウの物理ピクセル。イベントの {@code getMousePosition()} も物理ピクセル。
 */
final class MapProjection {
    private MapProjection() {
    }

    static Projection of(IFullscreen fullscreen) {
        Window window = Minecraft.getInstance().getWindow();
        return new Projection(
                fullscreen.getCenterBlockX(true),
                fullscreen.getCenterBlockZ(true),
                fullscreen.getUiState().blockSize,
                window.getWidth(),
                window.getHeight());
    }

    /** GUI 倍率（物理ピクセル / GUI ピクセル）。 */
    static int guiScale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }
}
