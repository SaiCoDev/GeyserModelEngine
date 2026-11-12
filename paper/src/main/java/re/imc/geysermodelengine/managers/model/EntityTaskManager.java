package re.imc.geysermodelengine.managers.model;

import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.model.ActiveModel;
import me.zimzaza4.geyserutils.spigot.api.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.events.GeyserModelEngineEntitySpawnEvent;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.propertyhandler.BetterModelPropertyHandler;
import re.imc.geysermodelengine.managers.model.propertyhandler.ModelEnginePropertyHandler;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;
import re.imc.geysermodelengine.managers.model.entity.ModelEngineEntityData;
import re.imc.geysermodelengine.managers.model.taskshandler.TaskHandler;
import re.imc.geysermodelengine.packet.entity.PacketEntity;
import re.imc.geysermodelengine.util.BedrockUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class EntityTaskManager {

    private final GeyserModelEngine plugin;

    private PropertyHandler propertyHandler;

    public EntityTaskManager(GeyserModelEngine plugin) {
        this.plugin = plugin;

        if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
            this.propertyHandler = new ModelEnginePropertyHandler(plugin);
            plugin.getLogger().info("Using ModelEngine property handler!");
        } else if (Bukkit.getPluginManager().getPlugin("BetterModel") != null) {
            this.propertyHandler = new BetterModelPropertyHandler(plugin);
            plugin.getLogger().info("Using BetterModel property handler!");
        } else {
            plugin.getLogger().severe("No supported model engine found!");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    public void checkViewers(EntityData model, Set<Player> viewers) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!BedrockUtils.isBedrockPlayer(onlinePlayer)) continue;

            if (canSee(onlinePlayer, model.getEntity(), model.getModelInstance())) {
                if (!viewers.contains(onlinePlayer)) {
                    viewers.add(onlinePlayer);
                    sendSpawnPacket(model, onlinePlayer);
                }
            } else {
                if (viewers.remove(onlinePlayer)) destroyFor(model, onlinePlayer);
            }
        }
    }

    public void dropHiddenViewers(EntityData model) {
        for (Player viewer : List.copyOf(model.getViewers())) {
            if (canSee(viewer, model.getEntity(), model.getModelInstance())) continue;
            model.getViewers().remove(viewer);
            if (viewer.isOnline()) destroyFor(model, viewer);
        }
    }

    public boolean isViewing(EntityData model, Player player) {
        return !model.getEntity().isDead() && model.getViewers().contains(player);
    }

    public void destroyFor(EntityData model, Player player) {
        model.getEntity().sendEntityDestroyPacket(Collections.singletonList(player));
        plugin.getSchedulerPool().schedule(() -> {
            if (!player.isOnline() || isViewing(model, player)) return;
            model.getEntity().sendEntityDestroyPacket(Collections.singletonList(player));
        }, staleSpawnWindow(), TimeUnit.MILLISECONDS);
    }

    public void removeEntity(EntityData model) {
        List<Player> viewers = List.copyOf(model.getViewers());
        model.getEntity().remove();
        plugin.getSchedulerPool().schedule(() -> model.getEntity().sendEntityDestroyPacket(viewers.stream().filter(Player::isOnline).toList()), staleSpawnWindow(), TimeUnit.MILLISECONDS);
    }

    private long staleSpawnWindow() {
        long requestedDelay = Math.max(plugin.getConfigManager().getConfig().getInt("models.join-send-delay", 20), plugin.getConfigManager().getConfig().getInt("models.spawn-send-delay", 1));
        return Math.max(requestedDelay, plugin.getConfigManager().getConfig().getInt("models.custom-entity-sync-delay", 150)) + 100L;
    }

    private void sendSpawnPacket(EntityData model, Player onlinePlayer) {
        TaskHandler task = model.getEntityTask();
        boolean firstJoined = !plugin.getModelManager().getPlayerJoinedCache().contains(onlinePlayer.getUniqueId());

        if (firstJoined) {
            task.sendEntityData(model, onlinePlayer, plugin.getConfigManager().getConfig().getInt("models.join-send-delay", 20));
        } else {
            task.sendEntityData(model, onlinePlayer, plugin.getConfigManager().getConfig().getInt("models.spawn-send-delay", 1));
        }

        Bukkit.getPluginManager().callEvent(new GeyserModelEngineEntitySpawnEvent(model, firstJoined));

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.spawn")) plugin.getLogger().info("Sending spawn Packet on spawn entity ID: " + model.getEntity().getEntityId());
    }

    public boolean canSee(Player player, PacketEntity entity, Object model) {
        if (!player.isOnline()) return false;
        if (!plugin.getModelManager().getPlayerJoinedCache().contains(player.getUniqueId())) return false;

        Location playerLocation = player.getLocation().clone();
        Location entityLocation = entity.getLocation().clone();
        playerLocation.setY(0);
        entityLocation.setY(0);

        if (playerLocation.getWorld() != entityLocation.getWorld()) return false;
        if (!plugin.getModelManager().getModelHandler().canSee(player, model)) return false;
        if (playerLocation.distanceSquared(entityLocation) > player.getSendViewDistance() * player.getSendViewDistance() * 48) return false;

        return true;
    }

    public void sendHitBoxToAll(EntityData model) {
        EntityUtils.sendCustomHitBox(model.getViewers(), model.getEntity().getEntityId(), 0.01f, 0.01f);
    }

    //TODO move this
    public boolean hasAnimation(ModelEngineEntityData model, String animation) {
        ActiveModel activeModel = model.getActiveModel();
        BlueprintAnimation animationProperty = activeModel.getBlueprint().getAnimations().get(animation);
        return !(animationProperty == null);
    }

    public PropertyHandler getPropertyHandler() {
        return propertyHandler;
    }
}
