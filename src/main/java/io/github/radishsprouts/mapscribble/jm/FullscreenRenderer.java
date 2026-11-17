package io.github.radishsprouts.mapscribble.jm;

import io.github.radishsprouts.mapscribble.compat.Gfx;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.MapCanvasRenderer;
import io.github.radishsprouts.mapscribble.geom.Projection;
import journeymap.api.v2.client.fullscreen.IFullscreen;

//? if >=26.1 {
import journeymap.api.v2.client.event.FullscreenRenderEvent;
//?}

final class FullscreenRenderer {
    private final MapCanvasRenderer renderer;

    FullscreenRenderer(DrawingController controller) {
        this.renderer = new MapCanvasRenderer(controller);
    }

    //? if >=26.1 {
    void render(FullscreenRenderEvent event) {
        IFullscreen fullscreen = event.getFullscreen();
        Projection projection = MapProjection.of(fullscreen);
        int guiScale = MapProjection.guiScale();
        renderer.render(Gfx.of(event.getGraphics()), projection, fullscreen.getUiState().dimension, guiScale);
    }
    //?} else {
    /*void render(Gfx gfx, IFullscreen fullscreen) {
        Projection projection = MapProjection.of(fullscreen);
        int guiScale = MapProjection.guiScale();
        renderer.render(gfx, projection, fullscreen.getUiState().dimension, guiScale);
    }
    *///?}
}
