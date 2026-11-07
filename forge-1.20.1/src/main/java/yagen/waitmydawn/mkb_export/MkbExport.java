package yagen.waitmydawn.mkb_export;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import yagen.waitmydawn.mkb_export.client.ForgeClientEntrypoint;

@Mod("mkb_export")
public final class MkbExport {
    public MkbExport() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, yagen.waitmydawn.mkb_export.config.ExportConfig.SPEC, "mkb_export-common.toml");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::guardSamplingJoin);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::attributeTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::attributeStop);
        try {
            yagen.waitmydawn.mkb_export.core.EntityReversalConfig.load(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot initialize mkb_export_reversal.json", failure);
        }
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClientEntrypoint::initialize);
    }

    private void attributeTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
            yagen.waitmydawn.mkb_export.client.AttributeProbe.tick(event.getServer());
    }

    private void attributeStop(net.minecraftforge.event.server.ServerStoppingEvent event) {
        yagen.waitmydawn.mkb_export.client.AttributeProbe.stop(event.getServer());
    }

    private void guardSamplingJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (yagen.waitmydawn.mkb_export.core.EntitySamplingScope.rejectEntityJoin(event.getEntity()))
            event.setCanceled(true);
    }
}
