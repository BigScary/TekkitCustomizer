/*
    TekkitCustomizer Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
 
package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

//helps rapidly scan items as they are held in hand
//when item held in hand, task is scheduled with slot number
//after short delay, if still on equipping same slot number, this task checks to see if the equipped item is contraband
class InHandContrabandScanTask implements Runnable 
{
	Player player;
	int slotNumber;
	
	public InHandContrabandScanTask(Player player, int slotNumber)
	{
		this.player = player;
		this.slotNumber = slotNumber;
	}
	
	@Override
	public void run()
	{
		//if he logged out, don't do anything
		if(!player.isOnline()) return;
		
		//if he's not holding the same item anymore, do nothing
		PlayerInventory inventory = player.getInventory();
		if(inventory.getHeldItemSlot() != slotNumber) return;
		
		ItemStack inHandStack = player.getItemInHand();
		if(inHandStack == null) return;
		
		MaterialInfo bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Ownership, player, inHandStack.getTypeId(), inHandStack.getData().getData(), player.getLocation());
		if(bannedInfo != null)
		{
			inventory.setItem(slotNumber, new ItemStack(Material.AIR));
			TekkitCustomizer.AddLogEntry("Confiscated " + bannedInfo.toString() + " from " + player.getName() + ".");
			player.sendMessage("Banned item confiscated.  Reason: " + bannedInfo.reason);
		}		
	}
}
