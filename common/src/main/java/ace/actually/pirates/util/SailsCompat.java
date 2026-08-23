package ace.actually.pirates.util;

import com.quintonc.vs_sails.blocks.HelmBlock;
import com.quintonc.vs_sails.blocks.entity.BaseHelmBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;

public class SailsCompat {

    public static void stopMotion(LoadedServerShip ship)
    {
        SeatedControllingPlayer seatedControllingPlayer = ship.getAttachment(SeatedControllingPlayer.class);
        if (seatedControllingPlayer == null) return;
        seatedControllingPlayer.setLeftImpulse(0);
        ship.setAttachment(SeatedControllingPlayer.class, seatedControllingPlayer);
    }

    /** Converts a desired rudder amount into incremental Sails helm-wheel input. */
    public static void steerHelm(World world, BlockPos helmPos, LoadedServerShip ship,
                                 SeatedControllingPlayer controls, float desiredRudder) {
        if (!(world.getBlockEntity(helmPos) instanceof BaseHelmBlockEntity helm)) {
            controls.setLeftImpulse(0.0f);
            ship.setAttachment(SeatedControllingPlayer.class, controls);
            return;
        }
        int center = BaseHelmBlockEntity.maxAngle / 2;
        int target = center + Math.round(Math.max(-1.0f, Math.min(1.0f, desiredRudder)) * center);
        int tolerance = Math.max(1, BaseHelmBlockEntity.wheelInterval / 2);
        float pulse = helm.getWheelAngle() < target - tolerance ? 1.0f
                : helm.getWheelAngle() > target + tolerance ? -1.0f : 0.0f;
        controls.setLeftImpulse(pulse);
        ship.setAttachment(SeatedControllingPlayer.class, controls);
    }
    private static double vdis(double x, double xto) {
        return Math.abs(x-xto);
    }

    public static boolean checkHelm(World world, BlockPos pos) {
        return world.getBlockState(pos.up()).getBlock() instanceof HelmBlock;
    }
}
