package ace.actually.pirates.items;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.CannonPrimingBlock;
import ace.actually.pirates.compat.MusketModCompat;
import ace.actually.pirates.entities.friendly_pirate.FriendlyPirateEntity;
import ace.actually.pirates.util.DisarmUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class ContractItem extends Item {
    Block jobsite;
    String jobname;
    public ContractItem(Block jobsite, String jobname) {
        super(new Settings());
        this.jobname=jobname;
        this.jobsite=jobsite;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        BlockPos pos = context.getBlockPos();
        if (!context.getWorld().getBlockState(pos).isOf(jobsite)) {
            return ActionResult.PASS;
        }

        if (context.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        if (context.getWorld() instanceof ServerWorld world) {
            FriendlyPirateEntity fpe = new FriendlyPirateEntity(world,pos);
            fpe.setPirateJob(jobname);
            if (jobname.equals("doctor")) {
                fpe.equipStack(EquipmentSlot.MAINHAND, new ItemStack(MusketModCompat.pistolOrCrossbow()));
                fpe.equipStack(EquipmentSlot.OFFHAND, PotionUtil.setPotion(new ItemStack(net.minecraft.item.Items.POTION), Potions.HEALING));
            }
            BlockState state = world.getBlockState(pos);
            DisarmUtils.rearm(world,pos);

            BlockPos spos;
            if(state.contains(Properties.FACING))
            {
                spos = pos.offset(state.get(Properties.FACING).getOpposite());
            }
            else
            {
                spos = pos.up();
            }
            fpe.refreshPositionAndAngles(spos.getX() + 0.5, spos.getY(), spos.getZ() + 0.5, 0, 0);
            fpe.genCustomName(world);
            world.spawnEntity(fpe);
            context.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
