package yagen.waitmydawn.mkb_export.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 由物理客户端分支加载，专用服务器不解析 Minecraft 客户端类型。
 */
public final class ForgeClientEntrypoint {
    private ForgeClientEntrypoint() {
    }

    public static void initialize() {
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEntrypoint::commands);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEntrypoint::frame);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEntrypoint::serverReload);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEntrypoint::serverReady);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEntrypoint::dataReady);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeClientEntrypoint::reloadListeners);
    }

    private static void commands(RegisterClientCommandsEvent event) {
        ExportController.instance().register(event.getDispatcher());
    }

    private static void serverReload(net.minecraftforge.event.AddReloadListenerEvent event) {
        WorldCapture.reloadStarted();
    }

    private static void serverReady(net.minecraftforge.event.server.ServerStartedEvent event) {
        WorldCapture.ready();
    }

    private static void dataReady(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) WorldCapture.ready();
    }

    private static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) ExportController.instance().frame();
    }

    private static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ExportController.instance().cancel("resource reload"));
    }
}
