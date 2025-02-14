/*
 * Copyright 2019 ProtectionStones team and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.espi.ProtectionStones;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.ProtectionStones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class LegacyUpgrade {
    // check that all of the PS custom flags are in ps regions and upgrade if not
    public static void upgradeRegions() {

        YamlConfiguration hideFile = null;
        if (new File(ProtectionStones.getInstance().getDataFolder() + "/hiddenpstones.yml").exists()) {
            hideFile = YamlConfiguration.loadConfiguration(new File(ProtectionStones.getInstance().getDataFolder() + "/hiddenpstones.yml"));
        }
        for (World world : Bukkit.getWorlds()) {
            RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            for (String regionName : rm.getRegions().keySet()) {
                if (regionName.startsWith("ps")) {
                    try {
                        PSLocation psl = WGUtils.parsePSRegionToLocation(regionName);
                        ProtectedRegion r = rm.getRegion(regionName);

                        // get material of ps
                        String entry = psl.x + "x" + psl.y + "y" + psl.z + "z", material;
                        if (hideFile != null && hideFile.contains(entry)) {
                            material = hideFile.getString(entry);
                        } else {
                            material = world.getBlockAt(psl.x, psl.y, psl.z).getType().toString();
                        }

                        if (r.getFlag(FlagHandler.PS_BLOCK_MATERIAL) == null) {
                            r.setFlag(FlagHandler.PS_BLOCK_MATERIAL, material);
                        }

                        if (r.getFlag(FlagHandler.PS_HOME) == null) {
                            if (ProtectionStones.isProtectBlockType(material)) {
                                PSProtectBlock cpb = ProtectionStones.getBlockOptions(material);
                                r.setFlag(FlagHandler.PS_HOME, (psl.x + cpb.homeXOffset) + " " + (psl.y + cpb.homeYOffset) + " " + (psl.z + cpb.homeZOffset));
                            } else {
                                r.setFlag(FlagHandler.PS_HOME, psl.x + " " + psl.y + " " + psl.z);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            try {
                rm.save();
            } catch (Exception e) {
                Bukkit.getLogger().severe("[ProtectionStones] WorldGuard Error [" + e + "] during Region File Save");
            }
        }
    }

    // convert regions to use UUIDs instead of player names
    static void convertToUUID() {
        Bukkit.getLogger().info("Updating PS regions to UUIDs...");
        for (World world : Bukkit.getWorlds()) {
            RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));

            // iterate over regions in world
            for (String regionName : rm.getRegions().keySet()) {
                if (regionName.startsWith("ps")) {
                    ProtectedRegion region = rm.getRegion(regionName);

                    // convert owners with player names to UUIDs
                    List<String> owners, members;
                    owners = new ArrayList<>(region.getOwners().getPlayers());
                    members = new ArrayList<>(region.getMembers().getPlayers());

                    // convert
                    for (String owner : owners) {
                        UUID uuid = Bukkit.getOfflinePlayer(owner).getUniqueId();
                        region.getOwners().removePlayer(owner);
                        region.getOwners().addPlayer(uuid);
                    }
                    for (String member : members) {
                        UUID uuid = Bukkit.getOfflinePlayer(member).getUniqueId();
                        region.getMembers().removePlayer(member);
                        region.getMembers().addPlayer(uuid);
                    }
                }
            }

            try {
                rm.save();
            } catch (Exception e) {
                Bukkit.getLogger().severe("[ProtectionStones] WorldGuard Error [" + e + "] during Region File Save");
            }
        }

        // update config to mark that uuid upgrade has been done
        ProtectionStones.config.set("uuidupdated", true);
        ProtectionStones.config.save();
        Bukkit.getLogger().info("Done!");
    }

    // upgrade from config < v2.0.0
    static void upgradeFromV1V2() {
        Bukkit.getLogger().info(ChatColor.AQUA + "Upgrading configs from v1.x to v2.0+...");

        try {
            ProtectionStones.blockDataFolder.mkdir();
            Files.copy(PSConfig.class.getResourceAsStream("/config.toml"), Paths.get(ProtectionStones.configLocation.toURI()), StandardCopyOption.REPLACE_EXISTING);

            FileConfig fc = FileConfig.builder(ProtectionStones.configLocation).build();
            fc.load();

            File oldConfig = new File(ProtectionStones.getInstance().getDataFolder() + "/config.yml");
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(oldConfig);

            fc.set("uuidupdated", (yml.get("UUIDUpdated") != null) && yml.getBoolean("UUIDUpdated"));
            fc.set("placing_cooldown", (yml.getBoolean("cooldown.enable")) ? yml.getInt("cooldown.cooldown") : -1);

            // options from global scope
            List<String> worldsDenied = yml.getStringList("Worlds Denied");
            List<String> flags = yml.getStringList("Flags");
            List<String> allowedFlags = new ArrayList<>(Arrays.asList(yml.getString("Allowed Flags").split(",")));

            // upgrade blocks
            for (String type : yml.getConfigurationSection("Region").getKeys(false)) {
                File file = new File(ProtectionStones.blockDataFolder.getAbsolutePath() + "/" + type + ".toml");
                Files.copy(PSConfig.class.getResourceAsStream("/block1.toml"), Paths.get(file.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
                FileConfig b = FileConfig.builder(file).build();
                b.load();

                b.set("type", type);
                b.set("alias", type);
                b.set("restrict_obtaining", false);
                b.set("world_list_type", "blacklist");
                b.set("worlds", worldsDenied);
                b.set("region.x_radius", yml.getInt("Region." + type + ".X Radius"));
                b.set("region.y_radius", yml.getInt("Region." + type + ".Y Radius"));
                b.set("region.z_radius", yml.getInt("Region." + type + ".Z Radius"));
                b.set("region.flags", flags);
                b.set("region.allowed_flags", allowedFlags);
                b.set("region.priority", yml.getInt("Region." + type + ".Priority"));
                b.set("block_data.display_name", "");
                b.set("block_data.lore", Arrays.asList());
                b.set("behaviour.auto_hide", yml.getBoolean("Region." + type + ".Auto Hide"));
                b.set("behaviour.no_drop", yml.getBoolean("Region." + type + ".No Drop"));
                b.set("behaviour.prevent_piston_push", yml.getBoolean("Region." + type + ".Block Piston"));
                // ignore silk touch option
                b.set("player.prevent_teleport_in", yml.getBoolean("Teleport To PVP.Block Teleport"));

                b.save();
                b.close();
            }

            fc.save();
            fc.close();

            oldConfig.renameTo(new File(ProtectionStones.getInstance().getDataFolder() + "/config.yml.old"));

        } catch (IOException e) {
            e.printStackTrace();
        }

        Bukkit.getLogger().info(ChatColor.GREEN + "Done!");
        Bukkit.getLogger().info(ChatColor.GREEN + "Please be sure to double check your configs with the new options!");

        Bukkit.getLogger().info(ChatColor.AQUA + "Updating PS Regions to new format...");
        LegacyUpgrade.upgradeRegions();
        Bukkit.getLogger().info(ChatColor.GREEN + "Done!");
    }
}
