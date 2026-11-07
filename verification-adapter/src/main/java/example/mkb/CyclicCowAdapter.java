package example.mkb;

import yagen.waitmydawn.mkb_export.api.EntityAppearanceAdapter;
import net.minecraft.world.entity.*;

import java.util.*;

import com.google.gson.JsonObject;

public final class CyclicCowAdapter implements EntityAppearanceAdapter {
    public String id() {
        return "mkb_example:cycle";
    }

    public String version() {
        return "1";
    }

    public boolean supports(String id) {
        return id.equals("minecraft:cow") && Boolean.getBoolean("mkb_export.example.fail");
    }

    public Capture capture(Entity source, boolean observed) {
        var copy = EntityType.SHEEP.create(source.level());
        return new Capture(new JsonObject(), List.of(new Part("cycle", "cycle", copy, "INDEPENDENT")), true, "");
    }
}
