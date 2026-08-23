package ace.actually.pirates.worldgen;

import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Associates loaded Pirates ship templates with their resource identifiers. */
public final class PirateShipTemplateRegistry {
    private static final Map<StructureTemplate, Identifier> SHIP_TEMPLATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PirateShipTemplateRegistry() {
    }

    public static boolean isPirateShip(Identifier id) {
        if (id == null || !id.getPath().startsWith("ship/")) return false;
        return id.getNamespace().equals("pirates_eureka")
                || id.getNamespace().equals("pirates_sails")
                || id.getNamespace().equals("pirates_sky");
    }

    public static void mark(StructureTemplate template, Identifier id) {
        if (template != null && isPirateShip(id)) SHIP_TEMPLATES.put(template, id);
    }

    public static Optional<Identifier> idOf(StructureTemplate template) {
        return Optional.ofNullable(SHIP_TEMPLATES.get(template));
    }
}