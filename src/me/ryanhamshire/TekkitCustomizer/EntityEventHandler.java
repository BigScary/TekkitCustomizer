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

import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

public class EntityEventHandler implements Listener
{
	//when an entity (includes both dynamite and creepers) explodes...
	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent explodeEvent)
	{		
		if(!TekkitCustomizer.instance.config_enforcementWorlds.contains(explodeEvent.getLocation().getWorld())) return;
		
		if(TekkitCustomizer.instance.config_protectSurfaceFromExplosions)
		{
			List<Block> blocks = explodeEvent.blockList();
			for(int i = 0; i < blocks.size(); i++)
			{
				Block block = blocks.get(i);
				if(	block.getY() >= explodeEvent.getLocation().getWorld().getSeaLevel() - 5 &&
					block.getTypeId() != 161 && block.getTypeId() != 246)
				{
					blocks.remove(i--); 
				}
			}
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onEntityDamage (EntityDamageEvent event)
	{
		if(!(event instanceof EntityDamageByEntityEvent)) return;
		
		EntityDamageByEntityEvent subEvent = (EntityDamageByEntityEvent) event;
		
		Player player = null;
		Entity damageSource = subEvent.getDamager();
		if(damageSource instanceof Player)
		{
			player = (Player)damageSource;
		}
		else if(damageSource instanceof Arrow)
		{
			Arrow arrow = (Arrow)damageSource;
			if(arrow.getShooter() instanceof Player)
			{
				player = (Player)arrow.getShooter();
			}
		}
		else if(damageSource instanceof ThrownPotion)
		{
			ThrownPotion potion = (ThrownPotion)damageSource;
			if(potion.getShooter() instanceof Player)
			{
				player = (Player)potion.getShooter();
			}
		}
		
		if(player != null)
		{
			MaterialInfo bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Ownership, player, player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getLocation());
			if(bannedInfo != null)
			{
				event.setCancelled(true);
				player.getInventory().setItemInHand(new ItemStack(Material.AIR));
				TekkitCustomizer.AddLogEntry("Confiscated " + bannedInfo.toString() + " from " + player.getName() + ".");
				player.sendMessage("Sorry, that item is banned.  Reason: " + bannedInfo.reason);
				return;
			}
			
			bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Usage, player, player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getLocation());
			if(bannedInfo != null)
			{
				event.setCancelled(true);
				player.sendMessage("Sorry, usage of that item is banned.  Reason: " + bannedInfo.reason);				
			}
		}
	}
}
