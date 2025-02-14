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

package dev.espi.ProtectionStones.commands;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.ProtectionStones.PSL;
import dev.espi.ProtectionStones.ProtectionStones;
import dev.espi.ProtectionStones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

class ArgAdminFlag {
    static boolean argumentAdminFlag(CommandSender p, String[] args) {

        if (args.length < 5) {
            PSL.msg(p, PSL.ADMIN_FLAG_HELP.msg());
            return true;
        }

        String flag, value = "", gee = "";
        World w = Bukkit.getWorld(args[2]);
        if (args[3].equalsIgnoreCase("-g")) {
            flag = args[5];
            for (int i = 6; i < args.length; i++) value += args[i] + " ";
            gee = args[4];
        } else {
            flag = args[3];
            for (int i = 4; i < args.length; i++) value += args[i] + " ";
        }

        final String fValue = value, fGee = gee;
        Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> {
            RegionManager rgm = WGUtils.getRegionManagerWithWorld(w);
            for (ProtectedRegion r : rgm.getRegions().values()) {
                if (ProtectionStones.isPSRegion(r)) {
                    ArgFlag.setFlag(r, p, flag, fValue.trim(), fGee);
                }
            }
        });
        return true;
    }
}
