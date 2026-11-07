package example.mkb;

import yagen.waitmydawn.mkb_export.api.EntityAppearanceAdapter;
import net.minecraft.world.entity.*;

import java.util.*;

import com.google.gson.JsonObject;

public final class EmptyPartAdapter implements EntityAppearanceAdapter {
    public String id() {
        return "mkb_example:empty_part";
    }

    public String version() {
        return "1";
    }

    public boolean supports(String id) {
        return id.equals("minecraft:chicken") && Boolean.getBoolean("mkb_export.example.fail");
    }

    public Capture capture(Entity source, boolean observed) {
        var part = EntityType.AREA_EFFECT_CLOUD.create(source.level());
        part.setPos(source.position());
        return new Capture(new JsonObject(), List.of(new Part("invisible_part", "root", part, "INDEPENDENT")), true, "");
    }
}
