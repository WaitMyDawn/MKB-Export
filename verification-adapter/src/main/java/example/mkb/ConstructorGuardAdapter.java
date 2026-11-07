package example.mkb;

import yagen.waitmydawn.mkb_export.api.EntityAppearanceAdapter;
import net.minecraft.world.entity.Entity;

public final class ConstructorGuardAdapter implements EntityAppearanceAdapter {
    public ConstructorGuardAdapter() {
        if (Boolean.getBoolean("mkb_export.example.badProvider"))
            throw new IllegalStateException("Deliberate constructor failure");
    }

    public String id() {
        return "mkb_example:guard";
    }

    public String version() {
        return "1";
    }

    public boolean supports(String id) {
        return false;
    }

    public Capture capture(Entity source, boolean observed) {
        throw new UnsupportedOperationException();
    }
}
