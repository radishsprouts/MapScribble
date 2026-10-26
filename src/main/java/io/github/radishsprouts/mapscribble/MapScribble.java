package io.github.radishsprouts.mapscribble;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.map.MinimapHudRenderer;
import io.github.radishsprouts.mapscribble.map.ScreenMapHost;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import io.github.radishsprouts.mapscribble.ui.ScribbleKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
    //? if >=26.1 {
    import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
    import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
    //?} else {
    /*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
    *///?}
    //? if >=1.21.11 {
    import net.minecraft.resources.Identifier;
    //?} else {
    /*import net.minecraft.resources.ResourceLocation;
    *///?}
//?} elif neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
    //? if >=1.21.11 {
    import net.minecraft.resources.Identifier;
    //?} else {
    import net.minecraft.resources.ResourceLocation;
    //?}
*///?} elif forge {
/*import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
    //? if >=1.21 {
    import net.minecraft.resources.Identifier;
    import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
    import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
    //?} elif >=1.20.5 {
    import net.minecraft.resources.ResourceLocation;
    import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
    import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
    import net.minecraftforge.common.MinecraftForge;
    import net.minecraftforge.eventbus.api.IEventBus;
    //?} else {
    import net.minecraft.resources.ResourceLocation;
    import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
    import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
    import net.minecraftforge.common.MinecraftForge;
    import net.minecraftforge.eventbus.api.IEventBus;
    import net.minecraftforge.fml.ModLoadingContext;
    import net.minecraftforge.network.NetworkConstants;
    //?}
*///?}

//? if neoforge {
/*@Mod(value = MapScribble.MOD_ID, dist = Dist.CLIENT)
*///?} elif forge {
/*@Mod(MapScribble.MOD_ID)
*///?}
public class MapScribble //? if fabric {
implements ClientModInitializer
//?}
{
    public static final String MOD_ID = "mapscribble";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static void initCommon() {
        DrawingController controller = ScribbleSession.install(StoreSession::repository);
        ScreenMapHost.init(controller);
        MinimapHudRenderer.init(controller);
    }

    //? if fabric {
    @Override
    public void onInitializeClient() {
        initCommon();
        ScribbleKeys.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.level != null) {
                StoreSession.updateDimension(client.level.dimension());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StoreSession.updateDimension(null));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                StoreSession.updateDimension(client.level.dimension());
            }
            ScreenMapHost.tick();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenMapHost.onScreenInit(screen);
            ScreenEvents.remove(screen).register(ScreenMapHost::onScreenClose);

            //? if >=26.1 {
            ScreenEvents.afterExtract(screen).register((drawn, gfx, mouseX, mouseY, tickDelta) ->
                    ScreenMapHost.onScreenRender(gfx, drawn, mouseX, mouseY, tickDelta));
            //?} else {
            /*ScreenEvents.afterRender(screen).register((drawn, gfx, mouseX, mouseY, tickDelta) ->
                    ScreenMapHost.onScreenRender(gfx, drawn, mouseX, mouseY, tickDelta));
            *///?}

            //? if >=1.21.11 {
            ScreenMouseEvents.allowMouseClick(screen).register((scr, event) ->
                    !ScreenMapHost.onMouseClicked(scr, event.x(), event.y(), event.button()));
            ScreenMouseEvents.afterMouseRelease(screen).register((scr, event, consumed) ->
                    ScreenMapHost.onMouseReleased(scr, event.x(), event.y(), event.button()));
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) ->
                    !ScreenMapHost.onKeyPressed(scr, event));
            //?} else {
            /*ScreenMouseEvents.allowMouseClick(screen).register((scr, mouseX, mouseY, button) ->
                    !ScreenMapHost.onMouseClicked(scr, mouseX, mouseY, button));
            ScreenMouseEvents.afterMouseRelease(screen).register((scr, mouseX, mouseY, button) ->
                    ScreenMapHost.onMouseReleased(scr, mouseX, mouseY, button));
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyCode, scanCode, modifiers) ->
                    !ScreenMapHost.onKeyPressed(scr, keyCode, scanCode, modifiers));
            *///?}
        });

        //? if >=26.1 {
        HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP,
                Identifier.fromNamespaceAndPath(MOD_ID, "minimap_overlay"),
                (graphics, deltaTracker) -> MinimapHudRenderer.render(graphics));
        //?} else {
        /*HudRenderCallback.EVENT.register((graphics, tickDelta) -> MinimapHudRenderer.render(graphics));
        *///?}

        LOGGER.info("MapScribble initialized (Fabric)");
    }
    //?} elif neoforge {
    /*public MapScribble(IEventBus modEventBus) {
        initCommon();
        ScribbleKeys.register();

        modEventBus.addListener((RegisterKeyMappingsEvent event) -> {
            for (KeyMapping k : ScribbleKeys.allKeys()) {
                event.register(k);
            }
        });

        modEventBus.addListener((RegisterGuiLayersEvent event) -> {
            //? if >=26.1 {
            event.registerAbove(VanillaGuiLayers.SLEEP_OVERLAY,
                    Identifier.fromNamespaceAndPath(MOD_ID, "minimap_overlay"),
                    (graphics, deltaTracker) -> MinimapHudRenderer.render(graphics));
            //?} elif >=1.21.11 {
            event.registerAbove(VanillaGuiLayers.SLEEP_OVERLAY,
                    Identifier.fromNamespaceAndPath(MOD_ID, "minimap_overlay"),
                    (graphics, partialTick) -> MinimapHudRenderer.render(graphics));
            //?} else {
            event.registerAbove(VanillaGuiLayers.SLEEP_OVERLAY,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "minimap_overlay"),
                    (graphics, partialTick) -> MinimapHudRenderer.render(graphics));
            //?}
        });

        IEventBus forgeBus = NeoForge.EVENT_BUS;
        forgeBus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                StoreSession.updateDimension(mc.level.dimension());
            }
        });
        forgeBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> StoreSession.updateDimension(null));

        forgeBus.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                StoreSession.updateDimension(mc.level.dimension());
            }
            ScreenMapHost.tick();
        });

        forgeBus.addListener((ScreenEvent.Init.Post event) -> ScreenMapHost.onScreenInit(event.getScreen()));
        forgeBus.addListener((ScreenEvent.Closing event) -> ScreenMapHost.onScreenClose(event.getScreen()));

        //? if >=26.1 {
        forgeBus.addListener((ScreenEvent.Render.Post event) ->
                ScreenMapHost.onScreenRender(event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY(), 0));
        //?} else {
        forgeBus.addListener((ScreenEvent.Render.Post event) ->
                ScreenMapHost.onScreenRender(event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));
        //?}

        forgeBus.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            if (ScreenMapHost.onMouseClicked(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        });

        forgeBus.addListener((ScreenEvent.MouseButtonReleased.Post event) ->
                ScreenMapHost.onMouseReleased(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton()));

        //? if >=26.1 {
        forgeBus.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            if (ScreenMapHost.onKeyPressed(event.getScreen(), event.getKeyEvent())) {
                event.setCanceled(true);
            }
        });
        //?} else {
        forgeBus.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            if (ScreenMapHost.onKeyPressed(event.getScreen(), event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
                event.setCanceled(true);
            }
        });
        //?}

        LOGGER.info("MapScribble initialized (NeoForge)");
    }
    *///?} elif forge {
    /*// Forge: コンストラクタの形とイベントの登録方法が系統ごとに違う（1.20.1 / 1.20.6 / 1.21 以降）
    //? if >=1.20.5 {
    public MapScribble(FMLJavaModLoadingContext context) {
    //?} else {
    public MapScribble() {
    //?}
        initCommon();
        ScribbleKeys.register();

        // サーバー側に同じ MOD が無くても接続できるようにする（クライアント専用の宣言）
        //? if >=1.20.5 {
        context.registerDisplayTest(IExtensionPoint.DisplayTest.IGNORE_ALL_VERSION);
        //?} else {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
        //?}

        //? if >=1.21 {
        // Forge 61 以降は EventBus 7: IEventBus が無く、各イベント型が持つ静的な BUS へ登録する。
        // キャンセル可能なイベントのリスナーは Predicate で、true を返すとイベントを取り消す。
        // Consumer との曖昧さを避けるため、値を返すラムダはブロックで書く。
        RegisterKeyMappingsEvent.BUS.addListener((RegisterKeyMappingsEvent event) -> {
            for (KeyMapping k : ScribbleKeys.allKeys()) {
                event.register(k);
            }
        });

        // HUD は POST_SLEEP_STACK の中の字幕の上へ描く。レイヤーのメソッドは 1.21.11 が render、26.1 以降が extract だが、
        // ラムダの引数の型は推論されるので書き方は同じ。
        AddGuiOverlayLayersEvent.BUS.addListener((AddGuiOverlayLayersEvent event) -> {
            event.getLayeredDraw().addAbove(ForgeLayeredDraw.POST_SLEEP_STACK,
                    Identifier.fromNamespaceAndPath(MOD_ID, "minimap_overlay"),
                    ForgeLayeredDraw.SUBTITLE_OVERLAY,
                    (graphics, deltaTracker) -> MinimapHudRenderer.render(graphics));
        });

        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                StoreSession.updateDimension(mc.level.dimension());
            }
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                StoreSession.updateDimension(null));

        // Post だけなので phase の判定は要らない
        TickEvent.ClientTickEvent.Post.BUS.addListener((TickEvent.ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                StoreSession.updateDimension(mc.level.dimension());
            }
            ScreenMapHost.tick();
        });

        ScreenEvent.Init.Post.BUS.addListener((ScreenEvent.Init.Post event) -> ScreenMapHost.onScreenInit(event.getScreen()));
        ScreenEvent.Closing.BUS.addListener((ScreenEvent.Closing event) -> ScreenMapHost.onScreenClose(event.getScreen()));
        ScreenEvent.Render.Post.BUS.addListener((ScreenEvent.Render.Post event) ->
                ScreenMapHost.onScreenRender(event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));

        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            return ScreenMapHost.onMouseClicked(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton());
        });

        ScreenEvent.MouseButtonReleased.Post.BUS.addListener((ScreenEvent.MouseButtonReleased.Post event) ->
                ScreenMapHost.onMouseReleased(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton()));

        ScreenEvent.KeyPressed.Pre.BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            return ScreenMapHost.onKeyPressed(event.getScreen(), event.getInfo());
        });
        //?} else {
        // 1.20.1 / 1.20.6 は従来どおり IEventBus へ登録する。mod バスの取得方法だけが違う。
        //? if >=1.20.5 {
        IEventBus modBus = context.getModEventBus();
        //?} else {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        //?}
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            for (KeyMapping k : ScribbleKeys.allKeys()) {
                event.register(k);
            }
        });

        //? if >=1.20.5 {
        // 1.20.6: AddGuiOverlayLayersEvent は mod バスのイベント。POST_SLEEP_STACK の中の字幕の上へ描く。
        modBus.addListener((AddGuiOverlayLayersEvent event) -> {
            event.getLayeredDraw().addAbove(ForgeLayeredDraw.POST_SLEEP_STACK,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "minimap_overlay"),
                    ForgeLayeredDraw.SUBTITLE_OVERLAY,
                    (graphics, partialTick) -> MinimapHudRenderer.render(graphics));
        });
        //?} else {
        modBus.addListener((RegisterGuiOverlaysEvent event) -> {
            event.registerAbove(VanillaGuiOverlay.SLEEP_FADE.id(), "minimap_overlay",
                    (gui, graphics, partialTick, width, height) -> MinimapHudRenderer.render(graphics));
        });
        //?}

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                StoreSession.updateDimension(mc.level.dimension());
            }
        });
        forgeBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> StoreSession.updateDimension(null));

        forgeBus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    StoreSession.updateDimension(mc.level.dimension());
                }
                ScreenMapHost.tick();
            }
        });

        forgeBus.addListener((ScreenEvent.Init.Post event) -> ScreenMapHost.onScreenInit(event.getScreen()));
        forgeBus.addListener((ScreenEvent.Closing event) -> ScreenMapHost.onScreenClose(event.getScreen()));
        forgeBus.addListener((ScreenEvent.Render.Post event) ->
                ScreenMapHost.onScreenRender(event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));

        forgeBus.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            if (ScreenMapHost.onMouseClicked(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        });

        forgeBus.addListener((ScreenEvent.MouseButtonReleased.Post event) ->
                ScreenMapHost.onMouseReleased(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton()));

        forgeBus.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            if (ScreenMapHost.onKeyPressed(event.getScreen(), event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
                event.setCanceled(true);
            }
        });
        //?}

        LOGGER.info("MapScribble initialized (Forge)");
    }
    *///?}
}
