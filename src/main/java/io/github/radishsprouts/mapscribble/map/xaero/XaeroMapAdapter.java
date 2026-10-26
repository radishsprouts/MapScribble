package io.github.radishsprouts.mapscribble.map.xaero;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.map.MapAdapter;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Xaero's World Map (GuiMap) との連携アダプター。
 * GuiMap の cameraX, cameraZ, scale から物理ピクセル基準の Projection を構築し、
 * 中ドラッグスクロールおよび手書き中のパンニング抑止を行う。
 */
public final class XaeroMapAdapter implements MapAdapter {
    private static final String GUI_MAP_CLASS = "xaero.map.gui.GuiMap";

    private Field cameraXField;
    private Field cameraZField;
    private Field scaleField;
    private Field dimensionField;
    private Field mouseDownPosXField;
    private Field mouseDownPosYField;
    private boolean reflectionInitialized;
    private boolean available;
    /** 取得の失敗を記録したか。currentProjection は 1 フレームに複数回呼ばれるので、毎回出さない。 */
    private boolean projectionWarned;
    private boolean scrollWarned;

    public XaeroMapAdapter() {
        // 遅延初期化のため、コンストラクタではクラスロードを行わない
    }

    private synchronized void initReflection(Class<?> clazz) {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        try {
            cameraXField = findField(clazz, "cameraX");
            cameraZField = findField(clazz, "cameraZ");
            scaleField = findField(clazz, "scale");
            dimensionField = findField(clazz, "lastNonNullViewedDimensionId");
            mouseDownPosXField = findField(clazz, "mouseDownPosX");
            mouseDownPosYField = findField(clazz, "mouseDownPosY");
            available = (cameraXField != null && cameraZField != null && scaleField != null);
        } catch (Throwable ignored) {
            available = false;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public boolean handles(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (!screen.getClass().getName().equals(GUI_MAP_CLASS)) {
            return false;
        }
        initReflection(screen.getClass());
        return available;
    }

    @Override
    public Projection currentProjection(Screen screen) {
        if (!handles(screen)) {
            return null;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            // Projection は物理ピクセル前提なので、フレームバッファの大きさを使う
            // （getScreenWidth / getScreenHeight は GUI 座標で、単位が合わない）
            int winW = mc.getWindow().getWidth();
            int winH = mc.getWindow().getHeight();
            double cx = cameraXField.getDouble(screen);
            double cz = cameraZField.getDouble(screen);
            double scale = scaleField.getDouble(screen);
            return new Projection(cx, cz, scale, winW, winH);
        } catch (Exception e) {
            if (!projectionWarned) {
                projectionWarned = true;
                MapScribble.LOGGER.warn("[MapScribble] Xaero's World Map の表示範囲を取得できませんでした: {}",
                        e.getMessage());
            }
            return null;
        }
    }

    @Override
    public Object currentDimension(Screen screen) {
        if (!handles(screen) || dimensionField == null) {
            return null;
        }
        try {
            return dimensionField.get(screen);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void scroll(Screen screen, double deltaPx, double deltaPy) {
        if (!handles(screen)) {
            return;
        }
        try {
            double scale = scaleField.getDouble(screen);
            if (scale <= 0.0) {
                return;
            }
            double deltaWorldX = -deltaPx / scale;
            double deltaWorldZ = -deltaPy / scale;
            double cx = cameraXField.getDouble(screen);
            double cz = cameraZField.getDouble(screen);
            cameraXField.setDouble(screen, cx + deltaWorldX);
            cameraZField.setDouble(screen, cz + deltaWorldZ);
        } catch (Exception e) {
            if (!scrollWarned) {
                scrollWarned = true;
                MapScribble.LOGGER.warn("[MapScribble] Xaero's World Map のスクロールに失敗しました: {}",
                        e.getMessage());
            }
        }
    }

    /** 手書き描画中に GuiMap 自体のドラッグ処理と慣性スクロールが動かないよう mouseDownPosX/Y をリセットする。 */
    @Override
    public void suppressMapDrag(Screen screen) {
        if (!handles(screen) || mouseDownPosXField == null || mouseDownPosYField == null) {
            return;
        }
        try {
            mouseDownPosXField.setInt(screen, -1);
            mouseDownPosYField.setInt(screen, -1);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onScreenInit(Screen screen) {
    }

    @Override
    public void onScreenClose(Screen screen) {
    }
}
