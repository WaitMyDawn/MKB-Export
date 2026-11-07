package example.mkb;

import yagen.waitmydawn.mkb_export.api.EntityAppearanceAdapter;
import net.minecraft.world.entity.*;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Independent provider fixture; the additional sheep is an artificial composition.
 */
public final class PigAppearanceAdapter implements EntityAppearanceAdapter {
    public String id() {
        return "mkb_example:pig";
    }

    public String version() {
        return "1";
    }

    public boolean supports(String id) {
        return id.equals("minecraft:pig");
    }

    public Capture capture(Entity source, boolean observed) {
        if (Boolean.getBoolean("mkb_export.example.fail"))
            throw new IllegalStateException("Deliberate provider failure");
        var sheep = EntityType.SHEEP.create(source.level());
        if (sheep == null) throw new IllegalStateException("Missing fixture factory");
        sheep.setPos(source.getX() + 1.5, source.getY(), source.getZ());
        var extra = new JsonObject();
        extra.addProperty("fixture", true);
        return new Capture(extra, List.of(new Part("companion", "root", sheep, "INDEPENDENT")), true, "");
    }

    public List<Variant> variants(Entity source) {
        var data = new net.minecraft.nbt.CompoundTag();
        source.saveWithoutId(data);
        data.putBoolean("Saddle", true);
        return List.of(new Variant("example_saddled", data));
    }
}
