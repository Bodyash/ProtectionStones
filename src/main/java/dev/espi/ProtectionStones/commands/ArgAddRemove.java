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

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import dev.espi.ProtectionStones.PSL;
import dev.espi.ProtectionStones.PSRegion;
import dev.espi.ProtectionStones.ProtectionStones;
import dev.espi.ProtectionStones.utils.UUIDCache;
import dev.espi.ProtectionStones.utils.WGUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ArgAddRemove implements PSCommandArg {

    private static UUID checks(Player p, String args[], PSRegion r, RegionManager rgm, WorldGuardPlugin wg, String permType) {
        if (permType.equals("members") && !p.hasPermission("protectionstones.members")) {
            PSL.msg(p, PSL.NO_PERMISSION_MEMBERS.msg());
            return null;
        } else if (permType.equals("owners") && !p.hasPermission("protectionstones.owners")) {
            PSL.msg(p, PSL.NO_PERMISSION_OWNERS.msg());
            return null;
        } else if (r == null) {
            PSL.msg(p, PSL.NOT_IN_REGION.msg());
            return null;
        } else if (WGUtils.hasNoAccess(r.getWGRegion(), p, wg.wrapPlayer(p), false)) {
            PSL.msg(p, PSL.NO_ACCESS.msg());
            return null;
        } else if (args.length < 2) {
            PSL.msg(p, PSL.COMMAND_REQUIRES_PLAYER_NAME.msg());
            return null;
        }
        if (!UUIDCache.nameToUUID.containsKey(args[1])) {
            PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());
            return null;
        }
        return UUIDCache.nameToUUID.get(args[1]);
    }

    // Handles adding and removing players to region, both as members and owners
    // type:
    //   add: add member
    //   remove: remove member
    //   addowner: add owner
    //   removeowner: remove owner

    public static boolean template(Player p, String[] args, String type) {
        PSRegion r = ProtectionStones.toPSRegion(p.getLocation());

        WorldGuardPlugin wg = WorldGuardPlugin.inst();
        RegionManager rgm = WGUtils.getRegionManagerWithPlayer(p);
        UUID uuid = checks(p, args, r, rgm, wg, (type.equals("add") || type.equals("remove")) ? "members" : "owners"); // validate permissions and stuff
        if (uuid == null) return true;
        switch (type) {
            case "add":
                r.getWGRegion().getMembers().addPlayer(uuid);
                break;
            case "remove":
                r.getWGRegion().getMembers().removePlayer(uuid);
                r.getWGRegion().getMembers().removePlayer(uuid);
                break;
            case "addowner":
                r.getWGRegion().getOwners().addPlayer(uuid);
                break;
            case "removeowner":
                r.getWGRegion().getOwners().removePlayer(uuid);
                r.getWGRegion().getOwners().removePlayer(uuid);
                break;
        }

        if (type.equals("add") || type.equals("addowner")) {
            PSL.msg(p, PSL.ADDED_TO_REGION.msg().replace("%player%", args[1]));
        } else if (type.equals("remove") || type.equals("removeowner")) {
            PSL.msg(p, PSL.REMOVED_FROM_REGION.msg().replace("%player%", args[1]));
        }
        return true;
    }

    @Override
    public List<String> getNames() {
        return Arrays.asList("add", "remove", "addowner", "removeowner");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args) {
        template((Player) s, args, args[0]);
        return true;
    }

}
