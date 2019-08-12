package com.massivecraft.factions.cmd;

import com.massivecraft.factions.*;
import com.massivecraft.factions.integration.Essentials;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.perms.Relation;
import com.massivecraft.factions.perms.Role;
import com.massivecraft.factions.util.SmokeUtil;
import com.massivecraft.factions.util.TL;
import com.massivecraft.factions.util.WarmUpUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;


public class CmdHome extends FCommand {

    public CmdHome() {
        super();
        this.aliases.add("home");

        this.requirements = new CommandRequirements.Builder(Permission.HOME)
                .memberOnly()
                .noDisableOnLock()
                .build();
    }

    @Override
    public void perform(final CommandContext context) {
        // TODO: Hide this command on help also.

        Faction targetFaction = context.argAsFaction(0, context.fPlayer == null ? null : context.faction);

        if (targetFaction != context.faction) {
            if (context.fPlayer.isAdminBypassing()) {
                if (targetFaction.hasHome()) {
                    context.player.teleport(targetFaction.getHome());
                } else {
                    context.fPlayer.msg(TL.COMMAND_HOME_NOHOME.toString());
                }
            } else {
                context.fPlayer.msg(TL.COMMAND_HOME_SELFONLY.toString());
            }
            return;
        }

        if (!FactionsPlugin.getInstance().conf().factions().homes().isEnabled()) {
            context.fPlayer.msg(TL.COMMAND_HOME_DISABLED);
            return;
        }

        if (!FactionsPlugin.getInstance().conf().factions().homes().isTeleportCommandEnabled()) {
            context.fPlayer.msg(TL.COMMAND_HOME_TELEPORTDISABLED);
            return;
        }

        if (!context.faction.hasHome()) {
            context.fPlayer.msg(TL.COMMAND_HOME_NOHOME.toString() + (context.fPlayer.getRole().value < Role.MODERATOR.value ? TL.GENERIC_ASKYOURLEADER.toString() : TL.GENERIC_YOUSHOULD.toString()));
            context.fPlayer.sendMessage(FCmdRoot.getInstance().cmdSethome.getUsageTemplate(context));
            return;
        }

        if (!FactionsPlugin.getInstance().conf().factions().homes().isTeleportAllowedFromEnemyTerritory() && context.fPlayer.isInEnemyTerritory()) {
            context.fPlayer.msg(TL.COMMAND_HOME_INENEMY);
            return;
        }

        if (!FactionsPlugin.getInstance().conf().factions().homes().isTeleportAllowedFromDifferentWorld() && context.player.getWorld().getUID() != context.faction.getHome().getWorld().getUID()) {
            context.fPlayer.msg(TL.COMMAND_HOME_WRONGWORLD);
            return;
        }

        Faction faction = Board.getInstance().getFactionAt(new FLocation(context.player.getLocation()));
        final Location loc = context.player.getLocation().clone();

        // if player is not in a safe zone or their own faction territory, only allow teleport if no enemies are nearby
        if (FactionsPlugin.getInstance().conf().factions().homes().getTeleportAllowedEnemyDistance() > 0 &&
                !faction.isSafeZone() &&
                (!context.fPlayer.isInOwnTerritory() || (context.fPlayer.isInOwnTerritory() && !FactionsPlugin.getInstance().conf().factions().homes().isTeleportIgnoreEnemiesIfInOwnTerritory()))) {
            World w = loc.getWorld();
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();

            for (Player p : context.player.getServer().getOnlinePlayers()) {
                if (p == null || !p.isOnline() || p.isDead() || p == context.player || p.getWorld() != w) {
                    continue;
                }

                FPlayer fp = FPlayers.getInstance().getByPlayer(p);
                if (context.fPlayer.getRelationTo(fp) != Relation.ENEMY || fp.isVanished()) {
                    continue;
                }

                Location l = p.getLocation();
                double dx = Math.abs(x - l.getX());
                double dy = Math.abs(y - l.getY());
                double dz = Math.abs(z - l.getZ());
                double max = FactionsPlugin.getInstance().conf().factions().homes().getTeleportAllowedEnemyDistance();

                // box-shaped distance check
                if (dx > max || dy > max || dz > max) {
                    continue;
                }

                context.fPlayer.msg(TL.COMMAND_HOME_ENEMYNEAR, String.valueOf(FactionsPlugin.getInstance().conf().factions().homes().getTeleportAllowedEnemyDistance()));
                return;
            }
        }

        // if economy is enabled, they're not on the bypass list, and this command has a cost set, make 'em pay
        if (!context.payForCommand(FactionsPlugin.getInstance().conf().economy().getCostHome(), TL.COMMAND_HOME_TOTELEPORT.toString(), TL.COMMAND_HOME_FORTELEPORT.toString())) {
            return;
        }

        // if Essentials teleport handling is enabled and available, pass the teleport off to it (for delay and cooldown)
        if (Essentials.handleTeleport(context.player, context.faction.getHome())) {
            return;
        }

        context.doWarmUp(WarmUpUtil.Warmup.HOME, TL.WARMUPS_NOTIFY_TELEPORT, "Home", () -> {
            // Create a smoke effect
            if (FactionsPlugin.getInstance().conf().factions().homes().isTeleportCommandSmokeEffectEnabled()) {
                List<Location> smokeLocations = new ArrayList<>();
                smokeLocations.add(loc);
                smokeLocations.add(loc.add(0, 1, 0));
                smokeLocations.add(context.faction.getHome());
                smokeLocations.add(context.faction.getHome().clone().add(0, 1, 0));
                SmokeUtil.spawnCloudRandom(smokeLocations, FactionsPlugin.getInstance().conf().factions().homes().getTeleportCommandSmokeEffectThickness());
            }

            context.player.teleport(context.faction.getHome());
        }, this.plugin.getConfig().getLong("warmups.f-home", 0));
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_HOME_DESCRIPTION;
    }

}
