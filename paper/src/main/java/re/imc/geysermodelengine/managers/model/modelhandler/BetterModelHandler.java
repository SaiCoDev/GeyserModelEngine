package re.imc.geysermodelengine.managers.model.modelhandler;

import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.Tracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.listener.BetterModelListener;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.BetterModelModel;
import re.imc.geysermodelengine.managers.model.model.Model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BetterModelHandler implements ModelHandler {

    private final GeyserModelEngine plugin;

    public BetterModelHandler(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    /**
     * Takes an EntityTracker (a model on an entity) or a DummyTracker (a model at a fixed location with no entity,
     * like CraftEngine furniture).
     */
    @Override
    public void createModel(Object... objects) {
        Tracker tracker = (Tracker) objects[0];
        BaseEntity entitySource = tracker instanceof EntityTracker entityTracker ? entityTracker.sourceEntity() : null;

        // Checked before creating the entity data: creating it starts its task, which would spawn an untracked entity.
        // A tracker being removed doesn't count, so a model can be added again right after it was removed.
        // A DummyTracker has no entity to share, so it can't already be tracked.
        if (entitySource != null) {
            Map<Model, EntityData> entityDataCache = plugin.getModelManager().getEntitiesCache().get(entitySource.id());
            if (entityDataCache != null) {
                for (Model existing : entityDataCache.keySet()) {
                    if (!existing.getName().equals(tracker.name())) continue;
                    if (!(existing.getEntityData() instanceof BetterModelEntityData existingData)) continue;

                    if (!existingData.isGone()) return;
                }
            }
        }

        PropertyHandler propertyHandler = plugin.getEntityTaskManager().getPropertyHandler();
        BetterModelEntityData entityData = new BetterModelEntityData(plugin, entitySource, tracker);

        Model model = new BetterModelModel(tracker, this, entityData, propertyHandler);

        int entityID = entityData.getCacheKey();

        plugin.getModelManager().getModelEntitiesCache().put(entityID, model);
        plugin.getModelManager().getEntitiesCache().compute(entityID, (id, models) -> {
            Map<Model, EntityData> map = models != null ? models : new ConcurrentHashMap<>();
            map.put(model, entityData);
            return map;
        });

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.spawn")) plugin.getLogger().info("Creating model for " + model.getName());
    }

    @Override
    public void processEntities(Entity entity) {
//        if (plugin.getModelManager().getEntitiesCache().containsKey(entity.getEntityId())) return;
//
//        @NotNull Optional<EntityTrackerRegistry> modeledEntity = BetterModel.registry(entity);
//
//        modeledEntity.ifPresent(m -> createModel(modeledEntity.get().entity(), m.));
    }

    @Override
    public void loadListeners() {
        Bukkit.getPluginManager().registerEvents(new BetterModelListener(plugin), plugin);
    }

    @Override
    public boolean canSee(Player player, Object model) {
        Tracker tracker = (Tracker) model;
        return !tracker.getPipeline().isHide(BukkitAdapter.adapt(player));
    }
}
