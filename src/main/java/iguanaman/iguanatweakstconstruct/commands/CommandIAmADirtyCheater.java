package iguanaman.iguanatweakstconstruct.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import java.util.Arrays;

import iguanaman.iguanatweakstconstruct.leveling.IguanaToolLeveling;

public class CommandIAmADirtyCheater extends CommandBase {

  @Override
  public String getCommandName() {
    return "imadirtycheater";
  }

  @Override
  public String getCommandUsage(ICommandSender p_71518_1_) {
    if(isCheater(p_71518_1_)) {
      return "I heard you like chicken";
    }
    else {
      return "Why are you saying that you're a dirty cheater?";
    }
  }

  @Override
  public boolean canCommandSenderUseCommand(ICommandSender sender) {
    return sender instanceof EntityPlayerMP;
  }

  @Override
  public void processCommand(ICommandSender sender, String[] args) {
    if(!isCheater(sender)) {
      sender.addChatMessage(new ChatComponentText(EnumChatFormatting.ITALIC + "You don't look like a cheater to me"));
    } else {
      String cmd = getCommandName();
      String arg = StatCollector.translateToLocal("message.apology");

      StringBuilder sb = new StringBuilder();
      for(String s : args) {
        sb.append(s);
        sb.append(" ");
      }

      if(arg.toLowerCase().trim().equals(sb.toString().toLowerCase().trim())) {
        sender.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("message.apologyaccepted")));
        convertTool(sender, getCurrentItem(sender));
      } else {
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.ITALIC + "If you're really sorry, type:"));
        sender.addChatMessage(new ChatComponentText("/" + cmd + " " + arg));
      }
    }
  }

  public ItemStack getCurrentItem(ICommandSender sender) {
    EntityPlayerMP player = getCommandSenderAsPlayer(sender);

    return player.getCurrentEquippedItem();
  }

  public boolean isCheater(ICommandSender sender) {
    ItemStack stack = getCurrentItem(sender);
    return stack != null && stack.getItem() == IguanaToolLeveling.rubberChicken;
  }

  private void convertTool(ICommandSender sender, ItemStack stack) {
    if(stack.getItem() != IguanaToolLeveling.rubberChicken)
      return;

    if(!stack.hasTagCompound() || !stack.getTagCompound().getCompoundTag("InfiTool").hasKey("Original"))
      return;



    String unloc = stack.getTagCompound().getCompoundTag("InfiTool").getString("Original");
    Item.itemRegistry.containsKey(unloc);
    Item item = getItemByText(sender, unloc);
    if(item == null)
      return;

    stack.func_150996_a(item);
    stack.getTagCompound().getCompoundTag("InfiTool").setInteger("CheatyXP", 0);
  }
}
