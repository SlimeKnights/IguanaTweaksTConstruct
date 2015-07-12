package iguanaman.iguanatweakstconstruct.replacing;

import iguanaman.iguanatweakstconstruct.IguanaTweaksTConstruct;
import iguanaman.iguanatweakstconstruct.leveling.LevelingLogic;
import iguanaman.iguanatweakstconstruct.leveling.modifiers.ModXpAwareRedstone;
import iguanaman.iguanatweakstconstruct.reference.Config;
import iguanaman.iguanatweakstconstruct.reference.Reference;
import iguanaman.iguanatweakstconstruct.util.HarvestLevels;
import iguanaman.iguanatweakstconstruct.util.Log;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import java.util.Arrays;

import tconstruct.TConstruct;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.ModifyBuilder;
import tconstruct.library.crafting.ToolBuilder;
import tconstruct.library.crafting.ToolRecipe;
import tconstruct.library.modifier.ItemModifier;
import tconstruct.library.tools.DualMaterialToolPart;
import tconstruct.library.tools.ToolCore;
import tconstruct.library.tools.ToolMaterial;
import tconstruct.library.weaponry.IAmmo;
import tconstruct.modifiers.tools.ModAttack;
import tconstruct.modifiers.tools.ModButtertouch;
import tconstruct.modifiers.tools.ModRedstone;
import tconstruct.tools.TinkerTools;
import tconstruct.tools.logic.ToolStationLogic;
import tconstruct.util.config.PHConstruct;
import tconstruct.weaponry.TinkerWeaponry;

import static iguanaman.iguanatweakstconstruct.replacing.ReplacementLogic.PartTypes.*;

public final class ReplacementLogic {
    private ReplacementLogic() {} // non-instantiable

    public static void exchangeToolPart(ToolCore tool, NBTTagCompound tags, PartTypes type, ItemStack partStack, ItemStack toolStack)
    {
        // create a new tool that'd be the old one with the new part exchanged
        ItemStack headStack = null;
        ItemStack handleStack = null;
        ItemStack accessoryStack = null;
        ItemStack extraStack = null;

        if(getPart(tool, HEAD) != null)
            headStack = new ItemStack(getPart(tool, HEAD), 1, getToolPartMaterial(tags, HEAD));
        if(getPart(tool, HANDLE) != null)
            handleStack = new ItemStack(getPart(tool, HANDLE), 1, getToolPartMaterial(tags, HANDLE));
        if(getPart(tool, ACCESSORY) != null)
            accessoryStack = new ItemStack(getPart(tool, ACCESSORY), 1, getToolPartMaterial(tags, ACCESSORY));
        if(getPart(tool, EXTRA) != null)
            extraStack = new ItemStack(getPart(tool, EXTRA), 1, getToolPartMaterial(tags, EXTRA));

        // extra hack for bolt crafting..
        if(tool == TinkerWeaponry.boltAmmo)
        {
            headStack = DualMaterialToolPart.createDualMaterial(TinkerWeaponry.partBolt, getToolPartMaterial(tags, HANDLE), getToolPartMaterial(tags, HEAD));
            handleStack = accessoryStack;
            accessoryStack = null;
            if(type == ACCESSORY)
                type = HANDLE;
            else
                type = HEAD;
        }

        ItemStack originalTool = ToolBuilder.instance.buildTool(headStack, handleStack, accessoryStack, extraStack, "Original Tool");
        if(originalTool == null) {
            Log.error("Tool to modify is impossible?");
            return;
        }

        if(type == HEAD && headStack != null)
            headStack = partStack;
        if(type == HANDLE && handleStack != null)
            handleStack = partStack;
        if(type == ACCESSORY && accessoryStack != null)
            accessoryStack = partStack;
        if(type == EXTRA && extraStack != null)
            extraStack = partStack;

        ItemStack newTool = ToolBuilder.instance.buildTool(headStack, handleStack, accessoryStack, extraStack, "Modified Tool");
        if(newTool == null) {
            Log.debug("External source prevents creation of tool with replaced parts.");
            return;
        }
        NBTTagCompound newTags = newTool.getTagCompound().getCompoundTag("InfiTool");

        int partMaterialId = getToolPartMaterial(newTags, type);
        int oldMaterialId = getToolPartMaterial(tags, type);

        // Things that can change from replacing a part:
        // - durability
        // - mining speed
        // - mining level
        // - attack
        // - reinforced
        // - shoddy/stonebound
        // - Material Traits/Abilities that are not reinforced/stonebound (mostly from other mods)
        // - XP

        // save unmodified old required xp for later use
        long oldRequiredXp = LevelingLogic.getRequiredXp(toolStack, tags);
        long oldRequiredBoostXp = LevelingLogic.getRequiredBoostXp(toolStack);

        // update part materials and rendering
        updateTag(newTags, tags, "Head");
        updateTag(newTags, tags, "RenderHead");
        updateTag(newTags, tags, "Handle");
        updateTag(newTags, tags, "RenderHandle");
        updateTag(newTags, tags, "Accessory");
        updateTag(newTags, tags, "RenderAccessory");
        updateTag(newTags, tags, "Extra");
        updateTag(newTags, tags, "RenderExtra");

        // update durability
        int base = newTags.getInteger("BaseDurability");
        int bonus = tags.getInteger("BonusDurability"); // not changed through material, modifier
        float modDur = tags.getFloat("ModDurability"); // not changed through material, modifier

        int total = Math.max((int) ((base + bonus) * (modDur + 1f)), 1);
        tags.setInteger("BaseDurability", base);
        tags.setInteger("TotalDurability", total);

        // update damage
        updateTag(newTags, tags, "Attack");

        // update mining speed
        updateTag(newTags, tags, "MiningSpeed");
        updateTag(newTags, tags, "MiningSpeed2");
        updateTag(newTags, tags, "MiningSpeedHandle");
        updateTag(newTags, tags, "MiningSpeedExtra");

        // update harvest level (boosting/xp will be applied later)
        updateTag(newTags, tags, "HarvestLevel");
        updateTag(newTags, tags, "HarvestLevel2");
        updateTag(newTags, tags, "HarvestLevelHandle");
        updateTag(newTags, tags, "HarvestLevelExtra");

        // ranged weapons have have additional tags to consider
        updateTag(newTags, tags, "DrawSpeed");
        updateTag(newTags, tags, "BaseDrawSpeed");
        updateTag(newTags, tags, "FlightSpeed");
        updateTag(newTags, tags, "Mass");
        updateTag(newTags, tags, "BreakChance");
        updateTag(newTags, tags, "Accuracy");

        // stonebound. Shoddy is always present and never changed, we can simply update it.
        updateTag(newTags, tags, "Shoddy");

        // update ammo
        if(tool instanceof IAmmo)
        {
            IAmmo ammo = (IAmmo)tool;
            if(ammo.getAmmoCount(toolStack) > ammo.getMaxAmmo(toolStack))
                ammo.setAmmo(ammo.getMaxAmmo(toolStack), toolStack);
        }

        // reinforced is kinda complicated, since the actual level you get out of the materials is complicated
        // simply calculate difference between current and newly built tool to know how much has been added afterwards
        int currentReinforced = tags.getInteger("Unbreaking");
        int oldReinforced = originalTool.getTagCompound().getCompoundTag("InfiTool").getInteger("Unbreaking");
        int newReinforced = newTags.getInteger("Unbreaking");
        newReinforced += currentReinforced - oldReinforced;
        tags.setInteger("Unbreaking", newReinforced);
        // reinforced tooltip is handled internally by tcon automagically

        // now for the scary part... handle material traits >_<
        handleMaterialTraits(tags, oldMaterialId, partMaterialId);
        // fiery blaze arrows has to be handled separately.. meh
        if(tool == TinkerWeaponry.arrowAmmo && type==HANDLE)
            if(oldMaterialId == 3 && partMaterialId != 3) {
                // remove fiery
                if(tags.getInteger("Fiery") > 5)
                    tags.setInteger("Fiery", tags.getInteger("Fiery")-5);
                else
                    tags.removeTag("Fiery");
            }
            else if(partMaterialId == 3 && oldMaterialId != 3) {
                // add fiery
                tags.setInteger("Fiery", 5);
            }

        // material tooltips are handled by tcon internally

        // redstone modifier
        reapplyRedstone(tags, toolStack);
        // quartz/attack modifier
        reapplyAttack(tags, toolStack);
        // silktouch
        reapplySilktouch(tags, toolStack);

        if(Config.removeMobHeadOnPartReplacement && type == HEAD)
            removeMobHeadModifier(tags);

        // if boosted, remove boost tag
        if(tags.hasKey("GemBoost"))
            tags.removeTag("GemBoost");

        // if boosted by vanilla diamond modifier, then we have to respect that if vanilla tcon allows harvestlevel change
        // check is either modifier-changed OR harvestlevels, because the changeDiamondModifier is basically false if the pulse isn't loaded
        if(PHConstruct.miningLevelIncrease && (!Config.changeDiamondModifier || !TConstruct.pulsar.isPulseLoaded(Reference.PULSE_HARVESTTWEAKS)))
        {
            int min = 0;
            if(tags.getBoolean("Diamond")) {
                min = 3;
                if(TConstruct.pulsar.isPulseLoaded(Reference.PULSE_HARVESTTWEAKS))
                    min = HarvestLevels._6_obsidian;
            }
            else if(tags.getBoolean("Emerald")) {
                min = 2;
                if(TConstruct.pulsar.isPulseLoaded(Reference.PULSE_HARVESTTWEAKS))
                    min = HarvestLevels._5_diamond;
            }

            if(tags.getInteger("HarvestLevel") < min)
                tags.setInteger("HarvestLevel", min);
        }

        // handle Leveling/xp (has to be done first before we change the stats so we get the correct old values)
        if(LevelingLogic.hasXp(tags))
        {
            float newRequiredXp = LevelingLogic.getRequiredXp(toolStack, tags);
            float xp = LevelingLogic.getXp(tags);

            // apply transition
            xp *= newRequiredXp / (float) oldRequiredXp;

            // xp penality?
            if(Config.partReplacementXpPenality > 0)
            {
                xp *= (100.0f-Config.partReplacementXpPenality)/100.0f;
            }

            tags.setInteger(LevelingLogic.TAG_EXP, Math.round(xp));
        }

        // handle boost leveling/xp
        if(LevelingLogic.hasBoostXp(newTags) && Config.levelingPickaxeBoost)
        {
            float newRequiredBoostXp = LevelingLogic.getRequiredBoostXp(toolStack);
            float xp = LevelingLogic.getBoostXp(tags);

            float percentage = xp / oldRequiredBoostXp;

            // apply transition
            xp *= newRequiredBoostXp / (float) oldRequiredBoostXp;

            // xp penality?
            if(Config.partReplacementBoostXpPenality > 0 && type == HEAD)
            {
                if(LevelingLogic.isBoosted(tags)) {
                    tags.setBoolean(LevelingLogic.TAG_IS_BOOSTED, false);
                    xp = newRequiredBoostXp;
                }

                xp *= (100.0f-Config.partReplacementBoostXpPenality)/100.0f;
            }

            tags.setInteger(LevelingLogic.TAG_BOOST_EXP, Math.round(xp));

            // already full xp?
            if(LevelingLogic.isBoosted(tags))
                tags.setInteger("HarvestLevel", tags.getInteger("HarvestLevel") + 1);
        }
        // add boost xp if its missing. Additional checks are done in the function
        else if(IguanaTweaksTConstruct.pulsar.isPulseLoaded(Reference.PULSE_LEVELING)) {
            LevelingLogic.addBoostTags(tags, tool);
        }

        // Update the tool name if we replaced the head and it was a automagic name
        if(type == HEAD) {
            String defaultName = ToolBuilder.defaultToolName(TConstructRegistry.getMaterial(oldMaterialId), tool);
            if (toolStack.getDisplayName().endsWith(defaultName)) {
                defaultName = ToolBuilder.defaultToolName(TConstructRegistry.getMaterial(partMaterialId), tool);
                toolStack.setStackDisplayName("\u00a7r" + defaultName);
            }
        }
    }

    // update tag if it already exists.
    // this saves us lots of hasFoo code,since there should be no general situation where a tag doesn't exist on the target
    private static void updateTag(NBTTagCompound from, NBTTagCompound to, String tag)
    {
        if(to.hasKey(tag))
            to.setTag(tag, from.getTag(tag));
    }


    // List of known material traits:
    // - Writeable
    // - Thaumic
    private static void handleMaterialTraits(NBTTagCompound tags, int oldMaterialId, int newMaterialId)
    {
        // nothing to do fi they're the same :)
        if(oldMaterialId == newMaterialId)
            return;

        ToolMaterial oldMat = TConstructRegistry.getMaterial(oldMaterialId);
        ToolMaterial newMat = TConstructRegistry.getMaterial(newMaterialId);

        // stonebound/jagged has to be handeled separately, see exchangeToolPart

        /************ first add the new traits *************/
        // todo: rewrite this to use material IDs.
        String ability = newMat.ability();
        // writeable & thaumic (equal since we only exchange 1 part)
        if(newMaterialId == TinkerTools.MaterialID.Paper || newMaterialId == TinkerTools.MaterialID.Thaumium) {
            tags.setInteger("Modifiers", tags.getInteger("Modifiers") + 1);
        }

        /************ then remove the old traits *************/
        ability = oldMat.ability();
        // writeable & thaumic (equal since we only exchange 1 part)
        if(oldMaterialId == TinkerTools.MaterialID.Paper || oldMaterialId == TinkerTools.MaterialID.Thaumium) {
            int newMods = tags.getInteger("Modifiers") - 1;
            // theoretically this should never happen, but.. rather be safe than sorry
            if(newMods < 0) newMods = 0;
            tags.setInteger("Modifiers", newMods);
        }
        // tasty - handled per ActiveToolMod, no NBT required
    }

    // strips the redstone modifier off a tool and reapplies it (to get the correct mining level modification)
    private static void reapplyRedstone(NBTTagCompound tags, ItemStack itemStack)
    {
        // only do if we actually have redstone
        if(!tags.hasKey("Redstone"))
            return;

        int modifiers = tags.getInteger("Modifiers"); // backup modifiers

        // find the redstone modifier
        for(ItemModifier mod : ModifyBuilder.instance.itemModifiers)
            if(mod instanceof ModRedstone)
            {
                ModRedstone modRedstone = (ModRedstone)mod;
                if(modRedstone instanceof ModXpAwareRedstone)
                    modRedstone = ((ModXpAwareRedstone)modRedstone).originalModifier;
                
                if(!modRedstone.stacks.contains(new ItemStack(Items.redstone)))
                	continue;
                
                int[] keyPair = tags.getIntArray("Redstone");
                // get amount of redstone applied
                int rLvl = keyPair[0];
                // reset redstone modifier
                tags.removeTag("Redstone");

                // remove the old tooltip
                int tipIndex = keyPair[2];
                tags.removeTag("Tooltip" + tipIndex);
                tags.removeTag("ModifierTip" + tipIndex);


                // reapply redstone
                while(rLvl-- > 0)
                    modRedstone.modify(new ItemStack[]{new ItemStack(Items.redstone)}, itemStack); // tags belong to oldTool
            }

        // restore modifiers
        tags.setInteger("Modifiers", modifiers);
    }

    // same as reapplyRedstone but for Attack modifier
    private static void reapplyAttack(NBTTagCompound tags, ItemStack itemStack)
    {
        // only do if we actually have attack modifier
        if(!tags.hasKey("ModAttack"))
            return;

        int modifiers = tags.getInteger("Modifiers"); // backup modifiers

        ModAttack modAttack = TinkerTools.modAttack;
        // ammo weapons have their own modifier
        if(itemStack.getItem() instanceof ToolCore && TinkerWeaponry.modAttack != null && Arrays.asList(((ToolCore) itemStack.getItem()).getTraits()).contains("ammo"))
            modAttack = TinkerWeaponry.modAttack;

        int[] keyPair = tags.getIntArray("ModAttack");
        // get amount of redstone applied
        int qLvl = keyPair[0];
        // reset redstone modifier
        tags.removeTag("ModAttack");

        // remove the old tooltip
        int tipIndex = keyPair[2];
        tags.removeTag("Tooltip" + tipIndex);
        tags.removeTag("ModifierTip" + tipIndex);


        // reapply redstone
        while(qLvl-- > 0)
            modAttack.modify(new ItemStack[]{new ItemStack(Items.quartz)}, itemStack); // tags belong to oldTool

        // restore modifiers
        tags.setInteger("Modifiers", modifiers);
    }

  // Reapplies the changes to stats done by the silktouch modifier
  private static void reapplySilktouch(NBTTagCompound tags, ItemStack itemStack)
  {
    // only do if we actually have attack modifier
    if(!tags.hasKey("Silk Touch"))
      return;

    int attack = tags.getInteger("Attack");
    attack -= 3;
    if (attack < 0)
      attack = 0;
    tags.setInteger("Attack", attack);

    int miningSpeed = tags.getInteger("MiningSpeed");
    miningSpeed -= 300;
    if (miningSpeed < 0)
      miningSpeed = 0;
    tags.setInteger("MiningSpeed", miningSpeed);

    if (tags.hasKey("MiningSpeed2"))
    {
      int miningSpeed2 = tags.getInteger("MiningSpeed2");
      miningSpeed2 -= 300;
      if (miningSpeed2 < 0)
        miningSpeed2 = 0;
      tags.setInteger("MiningSpeed2", miningSpeed2);
    }
  }

    // removes the mobhead modifier and rendering
    private static void removeMobHeadModifier(NBTTagCompound tags)
    {
        // only if we actually are boosted by the modifier
        if(!tags.hasKey("Mining Level Boost"))
            return;

        // remove the modifier
        tags.removeTag("Mining Level Boost");

        // remove the rendering effect
        int i = 1;
        boolean removed = false;
        while(true)
        {
            String effect = "Effect" + i;
            i++;

            // we iterated over all effects
            if(!tags.hasKey(effect))
                break;

            // if we removed the tag, but there are other effects left, we have to fill the gap
            if(removed)
            {
                tags.setInteger("Effect" + (i-2), tags.getInteger(effect));
                tags.removeTag(effect);
                continue;
            }

            int effectId = tags.getInteger(effect);
            // mobheads occupy id's 20 to 26. see IguanaToolLeveling.registerBoostModifiers
            if(effectId >= 20 && effectId <= 26 ) {
                tags.removeTag(effect);
                removed = true;
            }
        }

        // find the tooltip
        i = 1;
        removed = false;
        while(true)
        {
            String modTip = "ModifierTip" + i;
            String toolTip = "Tooltip" + i;
            i++;

            if(!tags.hasKey(modTip))
                break;

            // same deal as above, fill the gap
            if(removed)
            {
                tags.setString("ModifierTip" + (i-2), tags.getString(modTip));
                tags.setString("Tooltip" + (i-2), tags.getString(toolTip));

                tags.removeTag(modTip);
                tags.removeTag(toolTip);
                continue;
            }

            if(tags.getString(modTip).endsWith("Mining Level Boost"))
            {
                tags.removeTag(modTip);
                tags.removeTag(toolTip);
                removed = true;
            }
        }

        // add a modifier for the removed modifier ;o
        if(Config.mobHeadRequiresModifier)
            tags.setInteger("Modifiers", tags.getInteger("Modifiers")+1);
    }

    /**
     * Checks if the material has an extra modifier.
     */
    public static boolean hasExtraModifier(int materialId)
    {
        // paper = 9, thaumium = 31
        return materialId == 9 || materialId == 31;
    }

    /**
     * Returns the type-part of the tool.
     */
    public static Item getPart(ToolCore tool, PartTypes type)
    {
        switch (type) {
            case HEAD: return tool.getHeadItem();
            case HANDLE: return tool.getHandleItem();
            case ACCESSORY: return tool.getAccessoryItem();
            case EXTRA: return tool.getExtraItem();
        }

        return null;
    }

    /**
     * Returns the material id the tool-component is made of.
     * @param tags InfiTool Tagcompount of the tool
     */
    public static int getToolPartMaterial(NBTTagCompound tags, PartTypes type)
    {
        int mat = -1;
        switch (type) {
            case HEAD: mat = tags.getInteger("Head"); break;
            case HANDLE: mat = tags.getInteger("Handle"); break;
            case ACCESSORY: mat = tags.getInteger("Accessory"); break;
            case EXTRA: mat = tags.getInteger("Extra"); break;
        }

        return mat;
    }

    /**
     * Check if another part type could be employed. basically slot-id = part-placement.
     * @param recipe Recipe that should be checked
     * @param part The new part that replaces an old one.
     * @param currentType Where the part would currently be replaced. Call multiple times with the previous result to check all.
     * @return The new position that's applicable, or the same if nothing new has been detected.
     */
    public static PartTypes detectAdditionalPartType(ToolRecipe recipe, Item part, PartTypes currentType)
    {
        PartTypes tmpType = currentType;
        if(tmpType == null)
        {
            if(recipe.validHead(part)) return HEAD;
            tmpType = HEAD;
        }
        if(tmpType == HEAD)
        {
            if(recipe.validHandle(part)) return HANDLE;
            tmpType = HANDLE;
        }
        if(tmpType == HANDLE)
        {
            if(recipe.validAccessory(part)) return ACCESSORY;
            tmpType = ACCESSORY;
        }
        if(tmpType == ACCESSORY)
        {
            if(recipe.validExtra(part)) return EXTRA;
        }

        return currentType;
    }

    public enum PartTypes {
        HEAD,
        HANDLE,
        ACCESSORY,
        EXTRA
    }
}
