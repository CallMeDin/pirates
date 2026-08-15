package ace.actually.pirates.util;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigUtils {

    public static Map<String,String> config = new HashMap<>();


    public static Map<String,String> loadConfigs()
    {
        File file = new File(FabricLoader.getInstance().getConfigDir().toString() + "/pirates/config.acfg");
        try {
            List<String> lines = FileUtils.readLines(file,"utf-8");
            lines.forEach(line->
            {
                line = line.replaceAll("#.*", "");
                if(!line.isEmpty())
                {
                    String noSpace = line.replace(" ","");
                    String[] entry = noSpace.split("=");
                    config.put(entry[0],entry[1]);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    public static void generateConfigs(List<String> input)
    {
        File file = new File(FabricLoader.getInstance().getConfigDirectory().getPath() + "/pirates/config.acfg");

        try {
            FileUtils.writeLines(file,input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String,String> checkConfigs()
    {
        if(new File(FabricLoader.getInstance().getConfigDirectory().getPath() + "/pirates/config.acfg").exists())
        {
            return loadConfigs();
        }
        generateConfigs(makeDefaults());
        return loadConfigs();
    }

    private static List<String> makeDefaults()
    {
        List<String> defaults = new ArrayList<>();

        defaults.add("#General config for Valkyrien Pirates\n");
        defaults.add("");
        defaults.add("#The minimum rate the pirates will fire their cannons at");
        defaults.add("cannon-firing-pause=40");
        defaults.add("");
        defaults.add("#The speed the cannonballs will fire at, default=3.2 (about 80 blocks on level ground)");
        defaults.add("cannon-range=3.2");
        defaults.add("");
        defaults.add("#Maximum distance in blocks that NPC cannons scan ahead before firing; current legacy default is 80");
        defaults.add("cannon-can-fire-range=80");
        defaults.add("");
        defaults.add("#Ticks between hostile target and operational cannon scans");
        defaults.add("naval-combat-target-update-ticks=40");
        defaults.add("naval-combat-steering-update-ticks=5");
        defaults.add("naval-combat-range-hysteresis=8");
        defaults.add("naval-combat-broadside-tolerance-degrees=10");
        defaults.add("#Heading error ignored to prevent left-right rudder oscillation");
        defaults.add("naval-combat-steering-deadzone-degrees=8");
        defaults.add("#Maximum automatic rudder input from 0.1 to 1.0");
        defaults.add("naval-combat-max-rudder-impulse=0.7");
        defaults.add("naval-combat-patrol-radius=200");
        defaults.add("naval-combat-patrol-arrival-distance=15");
        defaults.add("");
        defaults.add("#The max amount of blocks for the new ship builder, set to -1 to use the Eureka/VS version");
        defaults.add("max-ship-blocks=-1");
        defaults.add("");
        defaults.add("#How many ticks should it take for an NPC controlled ship to change its target position, default 50");
        defaults.add("controlled-ship-updates=100");
        defaults.add("");
        defaults.add("#Base power for cannonball shot entity, default=2.2");
        defaults.add("base-shot-power=2.2");
        defaults.add("");
        defaults.add("#Based squared distance to trigger NPC ship pursuit default=10000");
        defaults.add("pursuit-distance=10000");
        defaults.add("");
        defaults.add("#Planned for removal: max size of ship in blocks to spawn as a pirate ship");
        defaults.add("max-ship-blocks=5000");
        defaults.add("");
        defaults.add("#Custom crew from config");
        defaults.add("custom-crew-entity-0=minecraft:zombie");
        defaults.add("custom-crew-entity-1=minecraft:skeleton");
        defaults.add("custom-crew-entity-2=minecraft:creeper");
        defaults.add("custom-crew-entity-3=minecraft:stray");
        defaults.add("");
        defaults.add("#Custom crew equipments- mainhand,offhand,helmet,chest,leggings,boots");
        defaults.add("custom-crew-equipment-0=0,0,0,0,0,0");
        defaults.add("custom-crew-equipment-1=0,0,0,0,0,0");
        defaults.add("custom-crew-equipment-2=0,0,0,0,0,0");
        defaults.add("custom-crew-equipment-3=0,0,0,0,0,0");
        defaults.add("");
        defaults.add("#Whether flying pirates should spawn in the world or not (default true) BETA!");
        defaults.add("should-enable-flying-pirates=false");
        defaults.add("");
        defaults.add("#what item should be used to recruit friendly pirates in the format \"minecraft:golden_apple,1\" to mean 1 golden apple");
        defaults.add("recruit-cost=minecraft:golden_apple,1");
        defaults.add("");
        defaults.add("#what item should be used to recruit doctors from jobless villagers");
        defaults.add("doctor-recruit-cost=minecraft:emerald,1");
        defaults.add("");
        defaults.add("#what item should be used to recruit boatswains from jobless villagers");
        defaults.add("boatswain-recruit-cost=minecraft:emerald,10");
        defaults.add("");
        defaults.add("#gold ingots charged for one full eligible ship repair run");
        defaults.add("ship-repair-gold-cost=64");
        defaults.add("");
        defaults.add("#comma-separated block IDs never entered into a repair queue");
        defaults.add("ship-repair-excluded-blocks=");

        return defaults;
    }

}
