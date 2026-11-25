package re.imc.geysermodelengine.managers.model.entity;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.google.common.collect.Sets;
import kr.toxicity.model.api.bukkit.platform.BukkitLocation;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.Tracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.taskshandler.BetterModelTaskHandler;
import re.imc.geysermodelengine.packet.entity.PacketEntity;

import java.util.Set;

/**
 * A BetterModel model shown to Bedrock players. Its tracker is either an EntityTracker, which follows a real entity
 * (pets, mobs), or a DummyTracker, which stands at a fixed location with no entity behind it (CraftEngine furniture).
 */
public class BetterModelEntityData implements EntityData {

    private final GeyserModelEngine plugin;

    private final PacketEntity entity;
    private final Set<Player> viewers = Sets.newConcurrentHashSet();

    // Null for a DummyTracker.
    private final @Nullable BaseEntity entitySource;
    private final Tracker tracker;

    private BetterModelTaskHandler entityTask;

    private boolean hurt;

    public BetterModelEntityData(GeyserModelEngine plugin, @Nullable BaseEntity entitySource, Tracker tracker) {
        this.plugin = plugin;

        this.entitySource = entitySource;
        this.tracker = tracker;

        this.entity = new PacketEntity(EntityTypes.PIG, viewers, modelLocation());

        runEntityTask();
    }

    @Override
    public void teleportToModel() {
        Location location = modelLocation();
        entity.teleport(location);

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.location")) plugin.getLogger().info(tracker.name() + " " + location);
    }

    private Location modelLocation() {
        if (entitySource != null) return ((BukkitLocation) entitySource.location()).source();
        return ((BukkitLocation) tracker.location()).source();
    }

    /**
     * True once the model should be taken off Bedrock clients: its entity died, or its tracker was removed or closed.
     * CraftEngine closes a furniture's DummyTracker when the furniture is broken or its chunk unloads.
     */
    public boolean isGone() {
        if (entitySource != null && entitySource.dead()) return true;
        return tracker.forRemoval() || tracker.isClosed();
    }

    /**
     * The key this model is cached under in the ModelManager: the real entity's id, or for a DummyTracker (no entity)
     * the packet entity's own id, which is drawn from a range real entity ids don't reach.
     */
    public int getCacheKey() {
        return entitySource != null ? entitySource.id() : entity.getEntityId();
    }

    public void runEntityTask() {
        entityTask = new BetterModelTaskHandler(plugin, this);
    }

    @Override
    public PacketEntity getEntity() {
        return entity;
    }

    @Override
    public Set<Player> getViewers() {
        return viewers;
    }

    @Override
    public BetterModelTaskHandler getEntityTask() {
        return entityTask;
    }

    @Override
    public Object getModelInstance() {
        return tracker;
    }

    public void setHurt(boolean hurt) {
        this.hurt = hurt;
    }

    public @Nullable BaseEntity getEntitySource() {
        return entitySource;
    }

    public Tracker getTracker() {
        return tracker;
    }

    public boolean isHurt() {
        return hurt;
    }
}
