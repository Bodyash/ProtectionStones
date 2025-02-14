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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.conversion.Path;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

/**
 * Represents the global config (config.toml) settings.
 */

public class PSConfig {

    // config options
    // config.toml will be loaded into these fields
    // uses autoboxing for primitives to allow for null
    @Path("config_version")
    public int configVersion;
    @Path("uuidupdated")
    public Boolean uuidupdated;
    @Path("placing_cooldown")
    public int placingCooldown;
    @Path("allow_duplicate_region_names")
    public Boolean allowDuplicateRegionNames;
    @Path("async_load_uuid_cache")
    public Boolean asyncLoadUUIDCache;
    @Path("ps_view_cooldown")
    public Integer psViewCooldown;
    @Path("base_command")
    public String base_command;
    @Path("aliases")
    public List<String> aliases;


    static void initConfig() {

        // check if using config v1 or v2 (config.yml -> config.toml)
        if (new File(ProtectionStones.getInstance().getDataFolder() + "/config.yml").exists() && !ProtectionStones.configLocation.exists()) {
            LegacyUpgrade.upgradeFromV1V2();
        }

        // check if config files exist
        try {
            if (!ProtectionStones.getInstance().getDataFolder().exists()) {
                ProtectionStones.getInstance().getDataFolder().mkdir();
            }
            if (!ProtectionStones.blockDataFolder.exists()) {
                ProtectionStones.blockDataFolder.mkdir();
                Files.copy(PSConfig.class.getResourceAsStream("/block1.toml"), Paths.get(ProtectionStones.blockDataFolder.getAbsolutePath() + "/block1.toml"), StandardCopyOption.REPLACE_EXISTING);
            }
            if (!ProtectionStones.configLocation.exists()) {
                Files.copy(PSConfig.class.getResourceAsStream("/config.toml"), Paths.get(ProtectionStones.configLocation.toURI()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Logger.getLogger(ProtectionStones.class.getName()).log(Level.SEVERE, null, ex);
        }

        // keep in mind that there is /ps reload, so clear arrays before adding config options!
        // clear data (for /ps reload)
        ProtectionStones.protectionStonesOptions.clear();

        // create config object
        if (ProtectionStones.config == null) {
            ProtectionStones.config = CommentedFileConfig.builder(ProtectionStones.configLocation).sync().build();
        }

        // loop upgrades until the config has been updated to the latest version
        do {
            ProtectionStones.config.load(); // load latest settings

            // load config into configOptions object
            ProtectionStones.getInstance().setConfigOptions(new ObjectConverter().toObject(ProtectionStones.config, PSConfig::new));

            // upgrade config if need be (v3+)
            boolean leaveLoop = doConfigUpgrades();
            if (leaveLoop) break; // leave loop if config version is correct

            // save config if upgrading
            ProtectionStones.config.save();
        } while (true);

        // load protection stones to options map
        if (ProtectionStones.blockDataFolder.listFiles().length == 0) {
            Bukkit.getLogger().info("The blocks folder is empty! You do not have any protection blocks configured!");
        } else {

            // temp file to load in default ps block config
            File tempFile;
            try {
                tempFile = File.createTempFile("psconfigtemp", ".toml");
                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    IOUtils.copy(PSConfig.class.getResourceAsStream("/block1.toml"), out);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            CommentedFileConfig template = CommentedFileConfig.of(tempFile);
            template.load();

            // iterate over block files and load into map
            Bukkit.getLogger().info("Protection Stone Blocks:");
            for (File file : ProtectionStones.blockDataFolder.listFiles()) {

                CommentedFileConfig c = CommentedFileConfig.builder(file).sync().build();
                c.load();

                // check to make sure all options are not null
                boolean updated = false;
                for (String str : template.valueMap().keySet()) {
                    if (c.get(str) == null) {
                        c.set(str, template.get(str));
                        c.setComment(str, template.getComment(str));
                        updated = true;
                    } else if (c.get(str) instanceof CommentedConfig) {
                        // no DFS for now (since there's only 2 layers of config)
                        CommentedConfig template2 = template.get(str);
                        CommentedConfig c2 = c.get(str);
                        for (String str2 : template2.valueMap().keySet()) {
                            if (c2.get(str2) == null) {
                                c2.add(str2, template2.get(str2));
                                c2.setComment(str2, template2.getComment(str2));
                                updated = true;
                            }
                        }
                    }
                }
                if (updated) c.save();

                // convert toml data into object
                PSProtectBlock b = new ObjectConverter().toObject(c, PSProtectBlock::new);

                if (Material.getMaterial(b.type) == null) {
                    Bukkit.getLogger().info("Unrecognized material: " + b.type);
                    Bukkit.getLogger().info("Block will not be added. Please fix this in your config.");
                    continue;
                }

                Bukkit.getLogger().info("- " + b.type + " (" + b.alias + ")");
                FlagHandler.initDefaultFlagsForBlock(b); // process flags for block and set regionFlags field
                if(b.alias.startsWith("itemsAdder_")) {
                	ProtectionStones.itemsAdderStonesOption.put(b.alias, b);
                }else {
                	ProtectionStones.protectionStonesOptions.put(b.type, b); // add block
                }

                // add custom recipes to Bukkit
                if (b.allowCraftWithCustomRecipe) {
                    setupRecipe(b);
                }
            }

            // cleanup temp file
            template.close();
            tempFile.delete();
        }

    }

    private static void setupRecipe(PSProtectBlock b) {
        // remove previous protectionstones recipes (/ps reload)
        List<Recipe> recipes = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe r = iter.next();
            if (r instanceof ShapedRecipe && (((ShapedRecipe) r).getKey().getNamespace().equalsIgnoreCase("protectionstones"))) {
                continue;
            }
            recipes.add(r);
        }
        // restore recipes without protectionstones ones
        Bukkit.clearRecipes();
        for (Recipe r : recipes) Bukkit.addRecipe(r);

        // create item
        ItemStack item = b.createItem();
        item.setAmount(b.recipeAmount);

        // create recipe
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(ProtectionStones.getInstance(), b.alias), item);
        HashMap<String, Character> items = new HashMap<>();
        List<String> recipeLine = new ArrayList<>();
        char id = 'a';
        for (int i = 0; i < b.customRecipe.size(); i++) {
            recipeLine.add("");
            for (String mat : b.customRecipe.get(i)) {
                if (mat.equals("")) {
                    recipeLine.set(i, recipeLine.get(i) + " ");
                } else {
                    if (items.get(mat) == null) {
                        items.put(mat, id++);
                    }
                    recipeLine.set(i, recipeLine.get(i) + items.get(mat));
                }
            }
        }

        // recipe
        recipe.shape(recipeLine.toArray(new String[0]));
        for (String mat : items.keySet()) {
            recipe.setIngredient(items.get(mat), Material.matchMaterial(mat));
        }
        Bukkit.addRecipe(recipe);
    }

    // Upgrade the config one version up (ex. 3 -> 4)
    private static boolean doConfigUpgrades() {
        boolean leaveLoop = false;
        switch (ProtectionStones.getInstance().getConfigOptions().configVersion) {
            case 3:
                ProtectionStones.config.set("config_version", 4);
                ProtectionStones.config.set("base_command", "ps");
                ProtectionStones.config.set("aliases", Arrays.asList("pstone", "protectionstones", "protectionstone"));
                break;
            case 4:
                ProtectionStones.config.set("config_version", 5);
                ProtectionStones.config.set("async_load_uuid_cache", false);
                ProtectionStones.config.set("ps_view_cooldown", 20);
                break;
            case 5:
                ProtectionStones.config.set("config_version", 6);
                ProtectionStones.config.set("allow_duplicate_region_names", false);
                break;
            case ProtectionStones.CONFIG_VERSION:
                leaveLoop = true;
                break;
            default:
                Bukkit.getLogger().info("Invalid config version! The plugin may not load correctly!");
                leaveLoop = true;
                break;
        }
        return leaveLoop;
    }
}
