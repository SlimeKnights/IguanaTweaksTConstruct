package iguanaman.iguanatweakstconstruct.tweaks.handlers;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.BlockEvent;

import java.util.ListIterator;

public class FlintHandler {

    @SubscribeEvent
    public void onBlockHarvested(BlockEvent.HarvestDropsEvent event)
    {
        // remove flint drop
        if(event.block != null && event.block == Blocks.gravel)
        {
            ListIterator<ItemStack> iter = event.drops.listIterator();
            boolean hasGravel = true;
            while(iter.hasNext())
            {
                Item item = iter.next().getItem();
                if(item == Items.flint)
                {
                    iter.remove();
                    hasGravel = false;
                    break;
                }
            }

            // ensure that gravel drops
            if(!hasGravel)
                event.drops.add(new ItemStack(Blocks.gravel));
        }
    }
}
