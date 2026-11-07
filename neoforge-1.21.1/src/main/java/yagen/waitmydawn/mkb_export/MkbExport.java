package yagen.waitmydawn.mkb_export;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import yagen.waitmydawn.mkb_export.client.ExportController;

@Mod(value = "mkb_export", dist = Dist.CLIENT)
public final class MkbExport {
    public MkbExport(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, yagen.waitmydawn.mkb_export.config.ExportConfig.SPEC, "mkb_export-common.toml");
        NeoForge.EVENT_BUS.addListener(this::guardSamplingJoin);
        NeoForge.EVENT_BUS.addListener(this::attributeTick);
        NeoForge.EVENT_BUS.addListener(this::attributeStop);
        try {
            yagen.waitmydawn.mkb_export.core.EntityReversalConfig.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot initialize mkb_export_reversal.json", failure);
        }
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::frame);
        NeoForge.EVENT_BUS.addListener(this::serverReload);
        NeoForge.EVENT_BUS.addListener(this::serverReady);
        NeoForge.EVENT_BUS.addListener(this::dataReady);
        modBus.addListener(this::reloadListeners);
    }

    private void commands(RegisterClientCommandsEvent event) {
        ExportController.instance().register(event.getDispatcher());
    }

    private void frame(RenderFrameEvent.Pre event) {
        ExportController.instance().frame();
    }

    private void serverReload(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        yagen.waitmydawn.mkb_export.client.WorldCapture.reloadStarted();
    }

    private void serverReady(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        yagen.waitmydawn.mkb_export.client.WorldCapture.ready();
    }

    private void dataReady(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) yagen.waitmydawn.mkb_export.client.WorldCapture.ready();
    }

    private void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ExportController.instance().cancel("resource reload"));
    }

    private void attributeTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        yagen.waitmydawn.mkb_export.client.AttributeProbe.tick(event.getServer());
    }

    private void attributeStop(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        yagen.waitmydawn.mkb_export.client.AttributeProbe.stop(event.getServer());
    }

    private void guardSamplingJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (yagen.waitmydawn.mkb_export.core.EntitySamplingScope.rejectEntityJoin(event.getEntity()))
            event.setCanceled(true);
    }
}
