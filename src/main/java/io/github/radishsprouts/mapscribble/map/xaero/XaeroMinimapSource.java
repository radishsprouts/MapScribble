package io.github.radishsprouts.mapscribble.map.xaero;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.geom.MinimapGeometry;
import io.github.radishsprouts.mapscribble.geom.MinimapSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/**
 * Xaero's Minimap のミニマップの枠の幾何を、リフレクションで取る。
 *
 * <p>Xaero's Minimap はクラス名・メソッド名が難読化されていないので、名前で参照できる。
 * <strong>この MOD の中で Xaero の内部へ触るのはこのクラスだけ</strong>で、描画側は
 * {@link MinimapGeometry}（物理ピクセルに正規化した値）しか見ない。
 *
 * <p>解決（{@code Class} / {@code Method} / {@code Field} の探索）は 1 度だけ行ってフィールドに持ち、
 * 毎フレームは {@code invoke} / {@code get} だけを行う。1 つでも必須の解決に失敗したら、
 * 以後 {@link #geometry(float)} は即 {@code null} を返す（警告は 1 度だけ）。
 * Xaero が入っていない場合は異常ではないので、ログを出さずに黙って無効になる。
 *
 * <p>バージョン分岐（stonecutter）は要らない。26.x だけにある
 * {@code MinimapPipRenderState} は、取れたかどうかで実行時に分ける。
 */
public final class XaeroMinimapSource implements MinimapSource {

    /** 枠の縁の太さ（Xaero の定数。minimapScale を掛けて物理ピクセルにする）。 */
    private static final double FRAME_PADDING = 9.0;
    /** 設定サイズから出した半辺と、枠の実寸から出した半辺のずれを許す量（物理ピクセル）。 */
    private static final double INNER_HALF_TOLERANCE = 2.0;

    private boolean resolved;
    private boolean available;
    private boolean warned;

    // xaero.common.XaeroMinimapSession
    private Method getCurrentSession;
    private Method getMinimapProcessor;
    // xaero.hud.minimap.BuiltInHudModules.MINIMAP（xaero.hud.module.HudModule）
    private Object minimapHudModule;
    private Method getModuleCurrentSession;
    private Method getPushState;
    // xaero.hud.pushbox.PushboxHandler$State
    private Field pushStateX;
    private Field pushStateY;
    // xaero.hud.minimap.module.MinimapSession
    private Method sessionGetWidth;
    private Method getHideUnderF3;
    /** サーバーがミニマップを禁止したか。取れなければ null（判定を省く）。 */
    private Method getNoMinimapMessageReceived;
    // xaero.common.minimap.MinimapProcessor
    private Method getMinimapSize;
    private Method getMinimapZoom;
    private Method isEnlargedMap;
    private Method getLastMapDimension;
    // 26.x の xaero.hud.minimap.render.MinimapPipRenderState（1.21.x 以前には無いので null でよい）
    private Method getRenderState;
    private Method getEnlargedRenderState;
    private Field renderStateX;
    private Field renderStateY;
    private Field renderStateShouldRender;
    private Method renderStateBoxSize;
    private Method renderStateMinimapScale;
    // 設定
    private Object hudMod;
    private Method getHudConfigs;
    private Method getClientConfigManager;
    private Method getEffective;
    private Method getMinimapScale;
    private Object shapeOption;
    /** ミニマップを出す設定か。取れなければ null（判定を省く）。 */
    private Object displayOption;
    private Method getEffectiveNorthLocked;
    // xaero.hud.entity.EntityUtils
    private Method entityGetX;
    private Method entityGetZ;

    @Override
    public boolean isAvailable() {
        if (!resolved) {
            resolve();
        }
        return available;
    }

    @Override
    public MinimapGeometry geometry(float partialTicks) {
        if (!resolved) {
            resolve();
        }
        if (!available) {
            return null;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            Object cameraEntity = mc.getCameraEntity();
            if (cameraEntity == null) {
                return null;
            }
            Object session = getCurrentSession.invoke(null);
            if (session == null) {
                return null;
            }
            Object processor = getMinimapProcessor.invoke(session);
            if (processor == null) {
                return null;
            }
            Object moduleSession = getModuleCurrentSession.invoke(minimapHudModule);
            if (moduleSession == null) {
                return null;
            }

            // サーバーがミニマップを禁止している間は Xaero も描かないので、メモも描かない
            if (getNoMinimapMessageReceived != null
                    && Boolean.TRUE.equals(getNoMinimapMessageReceived.invoke(moduleSession))) {
                return null;
            }
            // F3 のデバッグ画面で隠す設定なら、メモも隠す
            if (getHideUnderF3 != null && Boolean.TRUE.equals(getHideUnderF3.invoke(moduleSession))
                    && GuiCompat.debugOverlayVisible()) {
                return null;
            }

            Object configManager = getClientConfigManager.invoke(getHudConfigs.invoke(hudMod));
            if (configManager == null) {
                return null;
            }
            // ミニマップそのものを無効にしている間は描かない
            if (displayOption != null
                    && Boolean.FALSE.equals(getEffective.invoke(configManager, displayOption))) {
                return null;
            }

            double guiScale = Math.max(1.0, mc.getWindow().getGuiScale());
            boolean enlarged = Boolean.TRUE.equals(isEnlargedMap.invoke(processor));

            // 枠の位置・大きさ・UI 倍率。26.x は実際に描いた状態から、それ以前は押し出し枠から取る
            double frameXGui;
            double frameYGui;
            double frameSideGui;
            double minimapScale;
            Object renderState = renderState(processor, enlarged);
            if (renderState != null) {
                if (renderStateShouldRender != null && !renderStateShouldRender.getBoolean(renderState)) {
                    // Xaero が今フレームはミニマップを描いていない
                    return null;
                }
                frameXGui = renderStateX.getInt(renderState);
                frameYGui = renderStateY.getInt(renderState);
                frameSideGui = ((Number) renderStateBoxSize.invoke(renderState)).doubleValue();
                minimapScale = ((Number) renderStateMinimapScale.invoke(renderState)).doubleValue();
            } else {
                Object pushState = getPushState.invoke(minimapHudModule);
                if (pushState == null) {
                    return null;
                }
                frameXGui = pushStateX.getInt(pushState);
                frameYGui = pushStateY.getInt(pushState);
                frameSideGui = ((Number) sessionGetWidth.invoke(moduleSession, guiScale)).doubleValue();
                minimapScale = ((Number) getMinimapScale.invoke(null, configManager)).doubleValue();
            }

            int minimapSize = ((Number) getMinimapSize.invoke(processor)).intValue();
            double zoom = ((Number) getMinimapZoom.invoke(processor)).doubleValue();
            int shape = ((Number) getEffective.invoke(configManager, shapeOption)).intValue();
            boolean northLocked = Boolean.TRUE.equals(
                    getEffectiveNorthLocked.invoke(null, minimapSize / 2, shape));

            double padPhys = FRAME_PADDING * minimapScale;
            // 設定サイズから出した半辺と、枠の実寸から出した半辺。食い違ったら枠の側を採る
            // （枠からはみ出して HUD へメモが漏れるより、見える範囲が少し狭い方が安全）
            double innerFromSize = minimapSize * minimapScale / 4.0;
            double innerFromFrame = (frameSideGui * guiScale - 2.0 * padPhys) / 2.0;
            double innerHalfPhys = Math.abs(innerFromSize - innerFromFrame) >= INNER_HALF_TOLERANCE
                    ? innerFromFrame : innerFromSize;

            double centerPhysX = frameXGui * guiScale + padPhys + innerHalfPhys;
            double centerPhysY = frameYGui * guiScale + padPhys + innerHalfPhys;
            double blockPxPhys = zoom * minimapScale / 2.0;
            double renderAngle = northLocked ? 90.0 : (-90.0 - GuiCompat.cameraYaw());

            double worldX = ((Number) entityGetX.invoke(null, cameraEntity, partialTicks)).doubleValue();
            double worldZ = ((Number) entityGetZ.invoke(null, cameraEntity, partialTicks)).doubleValue();

            MinimapGeometry geometry = new MinimapGeometry(centerPhysX, centerPhysY, innerHalfPhys,
                    blockPxPhys, renderAngle, shape != 0, worldX, worldZ,
                    getLastMapDimension.invoke(processor));
            return geometry.isUsable() ? geometry : null;
        } catch (Throwable t) {
            // 取得経路が変わった・改変版と併用している等。描かないだけで済ませ、ログは 1 度だけ
            available = false;
            warnOnce("Xaero's Minimap の値の取得に失敗しました。ミニマップへの手書きメモを無効にします", t);
            return null;
        }
    }

    /** 26.x の描画状態。拡大中は拡大用の方を使う。1.21.x 以前では null。 */
    private Object renderState(Object processor, boolean enlarged) throws Exception {
        if (renderStateX == null) {
            return null;
        }
        Method method = enlarged && getEnlargedRenderState != null ? getEnlargedRenderState : getRenderState;
        return method == null ? null : method.invoke(processor);
    }

    private void resolve() {
        resolved = true;
        available = false;
        if (!classExists("xaero.common.XaeroMinimapSession")) {
            // Xaero's Minimap が入っていない。異常ではないのでログは出さない
            return;
        }
        try {
            Class<?> sessionClass = Class.forName("xaero.common.XaeroMinimapSession");
            getCurrentSession = sessionClass.getMethod("getCurrentSession");
            getMinimapProcessor = sessionClass.getMethod("getMinimapProcessor");

            Class<?> hudModuleClass = Class.forName("xaero.hud.module.HudModule");
            getModuleCurrentSession = hudModuleClass.getMethod("getCurrentSession");
            getPushState = hudModuleClass.getMethod("getPushState");
            minimapHudModule = Class.forName("xaero.hud.minimap.BuiltInHudModules")
                    .getField("MINIMAP").get(null);

            Class<?> pushStateClass = Class.forName("xaero.hud.pushbox.PushboxHandler$State");
            pushStateX = pushStateClass.getField("x");
            pushStateY = pushStateClass.getField("y");

            Class<?> minimapSessionClass = Class.forName("xaero.hud.minimap.module.MinimapSession");
            sessionGetWidth = minimapSessionClass.getMethod("getWidth", double.class);
            getHideUnderF3 = optionalMethod(minimapSessionClass, "getHideMinimapUnderF3");
            getNoMinimapMessageReceived = optionalMethod(minimapSessionClass, "getNoMinimapMessageReceived");

            Class<?> processorClass = Class.forName("xaero.common.minimap.MinimapProcessor");
            getMinimapSize = processorClass.getMethod("getMinimapSize");
            getMinimapZoom = processorClass.getMethod("getMinimapZoom");
            isEnlargedMap = processorClass.getMethod("isEnlargedMap");
            getLastMapDimension = processorClass.getMethod("getLastMapDimension");
            getRenderState = optionalMethod(processorClass, "getRenderState");
            getEnlargedRenderState = optionalMethod(processorClass, "getEnlargedRenderState");
            resolveRenderState();

            Class<?> hudModClass = Class.forName("xaero.common.HudMod");
            hudMod = hudModClass.getField("INSTANCE").get(null);
            if (hudMod == null) {
                warnOnce("xaero.common.HudMod.INSTANCE が null でした", null);
                return;
            }
            getHudConfigs = hudModClass.getMethod("getHudConfigs");
            Object configChannel = getHudConfigs.invoke(hudMod);
            if (configChannel == null) {
                warnOnce("Xaero の設定チャネルが取得できませんでした", null);
                return;
            }
            // ここから先の型（xaero.lib.*）は jar に同梱されておらず Class.forName できないので、
            // 実体の getClass() からメソッド名と引数の個数で探す
            getClientConfigManager = configChannel.getClass().getMethod("getClientConfigManager");
            Object configManager = getClientConfigManager.invoke(configChannel);
            if (configManager == null) {
                warnOnce("Xaero の設定マネージャが取得できませんでした", null);
                return;
            }
            getEffective = findMethod(configManager.getClass(), "getEffective", 1);
            getMinimapScale = findMethod(Class.forName("xaero.hud.gui.util.GuiUtils"), "getMinimapScale", 1);

            Class<?> optionsClass =
                    Class.forName("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions");
            shapeOption = optionsClass.getField("SHAPE").get(null);
            displayOption = optionalStaticField(optionsClass, "DISPLAY_MINIMAP");

            getEffectiveNorthLocked =
                    Class.forName("xaero.hud.minimap.config.util.MinimapConfigClientUtils")
                            .getMethod("getEffectiveNorthLocked", int.class, int.class);

            Class<?> entityUtilsClass = Class.forName("xaero.hud.entity.EntityUtils");
            entityGetX = findMethod(entityUtilsClass, "getEntityX", 2);
            entityGetZ = findMethod(entityUtilsClass, "getEntityZ", 2);

            available = true;
        } catch (Throwable t) {
            available = false;
            warnOnce("Xaero's Minimap の内部へのアクセス経路が見つかりませんでした。"
                    + "ミニマップへの手書きメモを無効にします", t);
        }
    }

    /** 26.x の MinimapPipRenderState。1 つでも欠けたら「無い」ものとして扱い、押し出し枠の経路へ落ちる。 */
    private void resolveRenderState() {
        if (getRenderState == null) {
            return;
        }
        try {
            Class<?> stateClass = Class.forName("xaero.hud.minimap.render.MinimapPipRenderState");
            Field x = stateClass.getDeclaredField("x");
            Field y = stateClass.getDeclaredField("y");
            x.setAccessible(true);
            y.setAccessible(true);
            renderStateBoxSize = stateClass.getMethod("getBoxSize");
            renderStateMinimapScale = stateClass.getMethod("getMinimapScale");
            // shouldRender は private で getter が無い。読めなくても「描いている」とみなして続ける
            try {
                Field shouldRender = stateClass.getDeclaredField("shouldRender");
                shouldRender.setAccessible(true);
                renderStateShouldRender = shouldRender;
            } catch (Throwable ignored) {
                renderStateShouldRender = null;
            }
            renderStateX = x;
            renderStateY = y;
        } catch (Throwable ignored) {
            renderStateX = null;
            renderStateY = null;
            renderStateBoxSize = null;
            renderStateMinimapScale = null;
            renderStateShouldRender = null;
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method optionalMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object optionalStaticField(Class<?> owner, String name) {
        try {
            return owner.getField(name).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 引数の型が参照できないメソッドを、名前と引数の個数で探す。 */
    private static Method findMethod(Class<?> owner, String name, int parameterCount) throws NoSuchMethodException {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                return m;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name + "（引数 " + parameterCount + " 個）");
    }

    private void warnOnce(String message, Throwable cause) {
        if (warned) {
            return;
        }
        warned = true;
        if (cause == null) {
            MapScribble.LOGGER.warn("[MapScribble] {}", message);
        } else {
            MapScribble.LOGGER.warn("[MapScribble] {}: {}", message, cause.toString());
        }
    }
}
