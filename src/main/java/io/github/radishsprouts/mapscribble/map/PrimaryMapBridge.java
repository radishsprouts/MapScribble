package io.github.radishsprouts.mapscribble.map;

import io.github.radishsprouts.mapscribble.compat.Gfx;
import net.minecraft.client.gui.screens.Screen;

//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
//?}

/**
 * JourneyMap 公式プラグイン（ScribbleBridge）等のプライマリマップ連携用インターフェース。
 * このインターフェースは JourneyMap の API クラスに一切依存しないため、
 * JourneyMap が導入されていない環境（Xaero や FTB Chunks のみ）でも安全にロードできる。
 */
public interface PrimaryMapBridge {
    void tick();

    boolean handles(Screen screen);

    void onScreenInit(Screen screen);

    void onScreenClose(Screen screen);

    void onScreenRender(Gfx gfx, Screen screen, int mouseX, int mouseY);

    boolean onMouseClicked(Screen screen, double mouseX, double mouseY, int button);

    boolean onMouseReleased(Screen screen, double mouseX, double mouseY, int button);

    //? if >=1.21.11 {
    boolean onKeyPressed(Screen screen, KeyEvent event);
    boolean onKeyPressed(Screen screen, int keyCode, int scanCode, int modifiers);
    //?} else {
    /*boolean onKeyPressed(Screen screen, int keyCode, int scanCode, int modifiers);
    *///?}
}
