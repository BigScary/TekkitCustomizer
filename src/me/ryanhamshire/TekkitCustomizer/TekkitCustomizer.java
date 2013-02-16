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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;


public class TekkitCustomizer extends JavaPlugin
{
	//for convenience, a reference to the instance of this plugin
	public static TekkitCustomizer instance;
	
	//for logging to the console and log file
	private static Logger log = Logger.getLogger("Minecraft");
		
	//where configuration data is kept
	private final static String dataLayerFolderPath = "plugins" + File.separator + "TekkitCustomizerData";
	public final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
	
	//user configuration, loaded/saved from a config.yml
	ArrayList<World> config_enforcementWorlds = new ArrayList<World>();
	MaterialCollection config_usageBanned = new MaterialCollection();
	MaterialCollection config_ownershipBanned = new MaterialCollection();
	MaterialCollection config_placementBanned = new MaterialCollection();
	MaterialCollection config_worldBanned = new MaterialCollection();
	MaterialCollection config_craftingBanned = new MaterialCollection();
	MaterialCollection config_recipesBanned = new MaterialCollection();

	boolean config_protectSurfaceFromExplosions;
	boolean config_removeUUMatterToNonRenewableRecipes;
	
	public synchronized static void AddLogEntry(String entry)
	{
		log.info("TekkitCustomizer: " + entry);
	}
	
	//initializes well...   everything
	public void onEnable()
	{ 		
		AddLogEntry("TekkitCustomizer enabled.");		
		
		instance = this;
		
		//register for events
		PluginManager pluginManager = this.getServer().getPluginManager();
		
		//player events
		PlayerEventHandler playerEventHandler = new PlayerEventHandler();
		pluginManager.registerEvents(playerEventHandler, this);
				
		//block events
		BlockEventHandler blockEventHandler = new BlockEventHandler();
		pluginManager.registerEvents(blockEventHandler, this);
		
		//entity events
		EntityEventHandler entityEventHandler = new EntityEventHandler();
		pluginManager.registerEvents(entityEventHandler, this);
		
		this.loadConfiguration();
		
		//start the repeating scan for banned items in player inventories and in the world
		//runs every minute and scans: 5% online players, 5% of loaded chunks
		Server server = this.getServer();
		ContrabandScannerTask task = new ContrabandScannerTask();
		server.getScheduler().scheduleSyncRepeatingTask(this, task, 20L * 60, 20L * 60);		
	}
	
	private void loadConfiguration()
	{
		//load the config if it exists
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(configFilePath));
		
		//read configuration settings
		
		//explosion protection for world surface
		this.config_protectSurfaceFromExplosions = config.getBoolean("TekkitCustomizer.ProtectSurfaceFromExplosives", true);
		config.set("TekkitCustomizer.ProtectSurfaceFromExplosives", this.config_protectSurfaceFromExplosions);
		
		//whether or not players can use UU matter to produce ore
		this.config_removeUUMatterToNonRenewableRecipes = config.getBoolean("TekkitCustomizer.RemoveUUMatterToNonRenewableItemRecipes", true);
		config.set("TekkitCustomizer.RemoveUUMatterToNonRenewableItemRecipes", this.config_removeUUMatterToNonRenewableRecipes);
		
		if(this.config_removeUUMatterToNonRenewableRecipes)
		{
			Server server = this.getServer();
			Iterator<Recipe> iterator = server.recipeIterator();
			while(iterator.hasNext())
			{
				Recipe recipe = iterator.next();
				if(recipe instanceof ShapedRecipe)
				{
					ShapedRecipe shapedRecipe = (ShapedRecipe)recipe;
					
					Map<Character, ItemStack> ingredients = shapedRecipe.getIngredientMap();
					Iterator<ItemStack> ingredientsIterator = ingredients.values().iterator();
					while(ingredientsIterator.hasNext())
					{
						ItemStack ingredient = ingredientsIterator.next();
						if(ingredient != null && ingredient.getTypeId() == 30188) //uu-matter ID
						{
							ItemStack result = shapedRecipe.getResult();
							if(	result.getType() == Material.DIAMOND ||
								result.getType() == Material.COAL ||
								result.getType() == Material.IRON_ORE ||
								result.getType() == Material.GOLD_ORE ||
								result.getType() == Material.REDSTONE_ORE ||
								result.getType() == Material.OBSIDIAN ||
								result.getType() == Material.MYCEL ||
								result.getType() == Material.GRASS ||
								result.getType() == Material.REDSTONE ||
								result.getType() == Material.SULPHUR ||  		//gun powder, from creepers
								result.getTypeId() == 140 ||					//various tekkit ores
								result.getTypeId() == 30217 )					//sticky resin
							{
								iterator.remove();
								break;
							}
						}
					}
				}
			}
		}
		
		//default for worlds list
		ArrayList<String> defaultWorldNames = new ArrayList<String>();
		List<World> worlds = this.getServer().getWorlds(); 
		for(int i = 0; i < worlds.size(); i++)
		{
			defaultWorldNames.add(worlds.get(i).getName());
		}
		
		//get world names from the config file
		List<String> worldNames = config.getStringList("TekkitCustomizer.EnforcementWorlds");
		if(worldNames == null || worldNames.size() == 0)
		{			
			worldNames = defaultWorldNames;
		}
		
		//validate that list
		this.config_enforcementWorlds = new ArrayList<World>();
		for(int i = 0; i < worldNames.size(); i++)
		{
			String worldName = worldNames.get(i);
			World world = this.getServer().getWorld(worldName);
			if(world == null)
			{
				AddLogEntry("Error: There's no world named \"" + worldName + "\".  Please update your config.yml.");
			}
			else
			{
				this.config_enforcementWorlds.add(world);
			}
		}
		
		config.set("TekkitCustomizer.EnforcementWorlds", worldNames);
		
		//
		//USAGE BANS - players can have these, but they can't use their right-click ability
		//
		List<String> dontUseStrings = config.getStringList("TekkitCustomizer.Bans.UsageBanned");
		
		//default values
		if(dontUseStrings == null || dontUseStrings.size() == 0)
		{
			dontUseStrings.add(new MaterialInfo(27585, (byte)2 ,"Divining Rod III", "Makes mining trivial, undermining the server economy.").toString());
			dontUseStrings.add(new MaterialInfo(27526, "Philosopher's Stone", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
		}
		
		//parse the strings from the config file
		this.parseMaterialListFromConfig(dontUseStrings, this.config_usageBanned);
		
		//write it back to the config entry for later saving
		config.set("TekkitCustomizer.Bans.UsageBanned", dontUseStrings);
		
		//
		//OWNERSHIP BANS - players can't have these at all (crafting is also blocked in this case)
		//
		List<String> dontOwnStrings = config.getStringList("TekkitCustomizer.Bans.OwnershipBanned");
		
		//default values
		if(dontOwnStrings == null || dontOwnStrings.size() == 0)
		{
			//these can potentially cause massive devastation, and/or totally ignore bukkit plugins (bypass anti grief and change logging)
			//also, the weapons and many rings/armors can injure players even when pvp is turned off
			dontOwnStrings.add(new MaterialInfo(27556, "Catalytic Lens", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27527, "Destruction Catalyst", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27530, "Evertide Amulet", "Bypasses anti-grief to place water in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27579, "Infernal Armor", "Bypasses anti-grief to destroy blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27580, "Abyss Helmet", "Calls lightning to injure players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(27537, "Harvest Goddess Band", "Bypasses anti-grief to grow and harvest in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27532, "Black Hole Band", "Bypasses anti-grief to remove water in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27533, "Ring of Ignition", "Bypasses anti-grief to injure claimed animals and set fires in protected areas without permission.  May injure players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(27593, "Void Ring", "Key ingredient in an item duplication exploit.").toString());
			dontOwnStrings.add(new MaterialInfo(27583, "Mercurial Eye", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27584, "Ring of Arcana", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27536, "Swiftwolf's Ring", "Calls lightning to injure players even when PvP is off, and the knockback effect enables animal theft.").toString());
			dontOwnStrings.add(new MaterialInfo(27538, "Watch of Flowing Time", "Can be used to sabotage another player's automated processes by altering speed of components.").toString());
			dontOwnStrings.add(new MaterialInfo(27531, "Volcanite Amulet", "Bypasses anti-grief to place lava in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27546, "Dark Matter Sword", "Power attack injures players when PvP is off, and protected animals without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27543, "Dark Matter Pickaxe", "Bypasses anti-grief to collect ore in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27544, "Dark Matter Shovel", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27545, "Dark Matter Hoe", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27547, "Dark Matter Axe", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27555, "Dark Matter Hammer", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27564, "Red Matter Pickaxe", "Bypasses anti-grief to collect ores in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27565, "Red Matter Shovel", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27566, "Red Matter Hoe", "Bypasses anti-grief to change blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27567, "Red Matter Sword", "Power attack injuures players when PvP is off, and protected animals without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27568, "Red Matter Axe", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27570, "Red Matter Hammer", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(27572, "Red Matter Katar", "Bypasses anti-grief to change blocks in protected areas without permission, and may injure players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(27573, "Red Matter Morning Star", "Bypasses anti-grief to change blocks in protected areas without permission, and may injure players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(126, (byte)4, "Red Matter Furnace", "Key ingredient in an item duplication exploit.").toString());
			dontOwnStrings.add(new MaterialInfo(26524, "Cannon", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(126, (byte)10, "Nova Catalyst", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(126, (byte)11, "Nova Cataclysm", "Bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(4095, "Dimensional Anchor", "Can easily crash the server by consuming all hard drive allocation.").toString());
			dontOwnStrings.add(new MaterialInfo(7312, "Tank Cart", "Key ingredient in an item duplication exploit.").toString());
			
			//ignores anti grief and block loggers, can place and break blocks ANYWHERE
			//further, even if it respected anti grief plugins, this could be used to make a giant mess hands free (even while offline)
			dontOwnStrings.add(new MaterialInfo(216, "Turtle", "Bypasses anti-grief to build in protected areas without permission.").toString());
			
			//bypasses anti grief and block loggers
			dontOwnStrings.add(new MaterialInfo(150, (byte)12, "Igniter", "Bypasses anti-grief to set fire in protected areas without permission.").toString());
			
			//performance issues because they make chunks stay in memory forever, even when players aren't around
			dontOwnStrings.add(new MaterialInfo(214, (byte)0, "World Anchor", "Consumes extra server memory, which may slow or crash the server.").toString());
			dontOwnStrings.add(new MaterialInfo(7303, "Anchor Cart", "Consumes extra server memory, which may slow or crash the server.").toString());
			
			//performance issues because left unattended, can rapidly breed animals and cause lag as a result of animal overload
			dontOwnStrings.add(new MaterialInfo(213, (byte)11, "Feed Station", "Unattended animal breeding can result in animal overload, severely slowing the server.").toString());
			
			//bypasses anti grief and block loggers, will destroy protected blocks
			//additionally, can change a big portion of the world even while left unattended, as it lays its own track
			dontOwnStrings.add(new MaterialInfo(7310, "Tunnel Bore", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(7308, "Iron Bore Head", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(7272, "Steel Bore Head", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.").toString());
			dontOwnStrings.add(new MaterialInfo(7314, "Diamond Bore Head", "The Tunnel Bore bypasses anti-grief to break blocks in protected areas without permission.").toString());
			
			//these allow players to craft items they don't have permission to craft or own
			dontOwnStrings.add(new MaterialInfo(173, (byte)3, "Project Table", "May craft banned items which would then be confiscated, wasting your ingredients.").toString());
			dontOwnStrings.add(new MaterialInfo(169, "Automatic Crafting Table", "May craft banned items which would then be confiscated, wasting your ingredients.").toString());
			dontOwnStrings.add(new MaterialInfo(194, (byte)1, "Automatic Crafting Table MkII", "May craft banned items which would then be confiscated, wasting your ingredients.").toString());
			dontOwnStrings.add(new MaterialInfo(7281, "Work Cart", "May craft banned items which would then be confiscated, wasting your ingredients.").toString());
			
			//loud sirens harass and annoy other players, and the source can be practically impossible to locate
			dontOwnStrings.add(new MaterialInfo(192, (byte)1, "Industrial Alarm", "May be turned up very loud and then hidden or protected to grief players.").toString());
			dontOwnStrings.add(new MaterialInfo(192, (byte)2, "Howler Alarm", "May be turned up very loud and then hidden or protected to grief players.").toString());
			
			//can be used to push unwanted blocks into a protected area without permission, and even steal blocks from that area
			dontOwnStrings.add(new MaterialInfo(150, (byte)7, "Frame Motor", "Bypasses anti-grief to move blocks into and out of protected areas without permission.").toString());
			
			//log file overload
			dontOwnStrings.add(new MaterialInfo(127, "Dark Matter Pedestal", "Fills the server log very quickly when activated, potentially running the server out of storage allocation and causing a crash.").toString());
			
			//economy protection
			dontOwnStrings.add(new MaterialInfo(126, (byte)0, "Energy Collector I", "Converting renewable energy sources to non-renewable ores undermines the server economy.").toString());
			dontOwnStrings.add(new MaterialInfo(126, (byte)1, "Energy Collector II", "Converting renewable energy sources to non-renewable ores undermines the server economy.").toString());
			dontOwnStrings.add(new MaterialInfo(126, (byte)2, "Energy Collector III", "Converting renewable energy sources to non-renewable ores undermines the server economy.").toString());
			
			//no-pvp workarounds
			dontOwnStrings.add(new MaterialInfo(30208, "Mining Laser", "May catch other players on fire even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(223, (byte)1, "Tesla Coil", "May kill other players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(26498, "Wooden Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(26499, "Stone Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(26500, "Iron Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(26501, "Diamond Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.").toString());
			dontOwnStrings.add(new MaterialInfo(26502, "Golden Hammer", "The power attack injures protected animals without permission, and players even when PvP is off.").toString());
		}
		
		//parse the strings from the config file
		this.parseMaterialListFromConfig(dontOwnStrings, this.config_ownershipBanned);
		
		//write it back to the config entry for later saving
		config.set("TekkitCustomizer.Bans.OwnershipBanned", dontOwnStrings);
		
		//
		//PLACEMENT BANS - players can't place these in the world (crafting is also blocked in this case)
		//
		List<String> dontPlaceStrings = config.getStringList("TekkitCustomizer.Bans.PlacementBanned");
		
		//default values
		if(dontPlaceStrings == null || dontPlaceStrings.size() == 0)
		{
			//no defaults yet
		}
		
		//parse the strings from the config file
		this.parseMaterialListFromConfig(dontPlaceStrings, this.config_placementBanned);
		
		//write it back to the config entry for later saving
		config.set("TekkitCustomizer.Bans.PlacementBanned", dontPlaceStrings);
		
		//
		//WORLD BANS - these aren't allowed anywhere in the enforcement worlds (see config variable) for any reason, and will be automatically removed
		//
		List<String> removeInWorldStrings = config.getStringList("TekkitCustomizer.Bans.WorldBanned");
		
		//default values
		if(removeInWorldStrings == null || removeInWorldStrings.size() == 0)
		{
			//no defaults yet
		}
		
		//parse the strings from the config file
		this.parseMaterialListFromConfig(removeInWorldStrings, this.config_worldBanned);
		
		//write it back to the config entry for later saving
		config.set("TekkitCustomizer.Bans.WorldBanned", removeInWorldStrings);
		
		//
		//CRAFTING BANS - players aren't allowed to craft these items (only collect them from the world, or get through other means like admin gifts)
		//
		List<String> dontCraftStrings = config.getStringList("TekkitCustomizer.Bans.CraftingBanned");
		
		//default values
		if(dontCraftStrings == null || dontCraftStrings.size() == 0)
		{
			//no defaults yet
		}
		
		//parse the strings from the config file
		this.parseMaterialListFromConfig(dontCraftStrings, this.config_craftingBanned);
		
		//write it back to the config entry for later saving
		config.set("TekkitCustomizer.Bans.CraftingBanned", dontCraftStrings);		
		
		//write all config data back to the config file
		try
		{
			config.save(configFilePath);
		}
		catch(IOException exception)
		{
			AddLogEntry("Unable to write to the configuration file at \"" + configFilePath + "\"");
		}
	}

	private void parseMaterialListFromConfig(List<String> stringsToParse, MaterialCollection materialCollection)
	{
		materialCollection.clear();
		
		//for each string in the list
		for(int i = 0; i < stringsToParse.size(); i++)
		{
			//try to parse the string value into a material info
			MaterialInfo materialInfo = MaterialInfo.fromString(stringsToParse.get(i));
			
			//null value returned indicates an error parsing the string from the config file
			if(materialInfo == null)
			{
				//show error in log
				TekkitCustomizer.AddLogEntry("ERROR: Unable to read a material entry from the config file.  Please update your config.yml.");
				
				//update string, which will go out to config file to help user find the error entry
				if(!stringsToParse.get(i).contains("can't"))
				{
					stringsToParse.set(i, stringsToParse.get(i) + "     <-- can't understand this entry, see BukkitDev documentation");
				}
			}
			
			//otherwise store the valid entry in config data
			else
			{
				materialCollection.Add(materialInfo);
			}
		}		
	}

	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		Player player = null;
		if (sender instanceof Player)
		{
			player = (Player) sender;
		}
		
		if(cmd.getName().equalsIgnoreCase("blockinfo") && player != null)
		{			
			boolean messageSent = false;
			
			//info about in-hand block, if any
			MaterialInfo inHand;
			ItemStack handStack = player.getItemInHand();
			if(handStack.getType() != Material.AIR)
			{
				inHand = new MaterialInfo(handStack.getTypeId(), handStack.getData().getData(), null, null);
				player.sendMessage("In Hand: " + inHand.toString());
				messageSent = true;
			}
			
			HashSet<Byte> transparentMaterials = new HashSet<Byte>();
			transparentMaterials.add((byte)Material.AIR.getId());
			Block targetBlock = player.getTargetBlock(transparentMaterials, 50);
			if(targetBlock != null && targetBlock.getType() != Material.AIR)
			{
				player.sendMessage("Targeted: " + new MaterialInfo(targetBlock.getTypeId(), targetBlock.getData(), null, null).toString());
				messageSent = true;
			}
			
			if(!messageSent)
			{
				player.sendMessage("To get information about a material, either hold it in your hand or move close and point at it with your crosshair.");
			}
			
			return true;
		}
		
		if(cmd.getName().equalsIgnoreCase("reloadbanneditems"))
		{			
			this.loadConfiguration();
			
			if(player != null)
			{
				player.sendMessage("Banned item configuration reloaded.");
			}
			else
			{
				TekkitCustomizer.AddLogEntry("Banned item configuration reloaded.");
			}
			
			return true;
		}

		return false;
	}
	
	
	
	public void onDisable()
	{
		AddLogEntry("TekkitCustomizer disabled.");
	}

	public MaterialInfo isBanned(ActionType actionType, Player player, int typeId, byte data, Location location) 
	{
		if(!this.config_enforcementWorlds.contains(location.getWorld())) return null;
		
		if(player.hasPermission("tekkitcustomizer.*")) return null;

		MaterialCollection collectionToSearch;
		String permissionNode;
		if(actionType == ActionType.Usage)
		{
			collectionToSearch = this.config_usageBanned;
			permissionNode = "use";
		}
		else if(actionType == ActionType.Placement)
		{
			collectionToSearch = this.config_placementBanned;
			permissionNode = "place";
		}
		else if(actionType == ActionType.Crafting)
		{
			collectionToSearch = this.config_craftingBanned;
			permissionNode = "craft";
		}
		else
		{
			collectionToSearch = this.config_ownershipBanned;
			permissionNode = "own";
		}
		
		//if the item is banned, check permissions
		MaterialInfo bannedInfo = collectionToSearch.Contains(new MaterialInfo(typeId, data, null, null));
		if(bannedInfo != null)
		{
			if(player.hasPermission("tekkitcustomizer." + typeId + ".*.*")) return null;
			if(player.hasPermission("tekkitcustomizer." + typeId + ".*." + permissionNode)) return null;
			if(player.hasPermission("tekkitcustomizer." + typeId + "." + data + "." + permissionNode)) return null;			
			if(player.hasPermission("tekkitcustomizer." + typeId + "." + data + ".*")) return null;
			
			return bannedInfo;
		}
				
		return null;
	}
	
	public static String getFriendlyLocationString(Location location) 
	{
		return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
	}
}