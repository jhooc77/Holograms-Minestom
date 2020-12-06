package com.sainttx.holograms;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.extollit.tuple.Pair;
import com.sainttx.holograms.api.Hologram;
import com.sainttx.holograms.api.HologramManager;
import com.sainttx.holograms.api.HologramPlugin;
import com.sainttx.holograms.api.exception.HologramEntitySpawnException;
import com.sainttx.holograms.api.line.HologramLine;
import com.sainttx.holograms.api.line.UpdatingHologramLine;
import com.sainttx.holograms.util.LocationUtil;

import net.minestom.server.instance.Instance;
import net.minestom.server.storage.StorageLocation;
import net.minestom.server.utils.Position;

public class ManagerImpl implements HologramManager {

    private HologramPlugin plugin;
    private Configuration persistingHolograms;
    private StorageLocation storageLocation;
    private Map<String, Hologram> activeHolograms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Set<UpdatingHologramLine> trackedUpdatingLines = new HashSet<>();

    public ManagerImpl(HologramPlugin plugin) {
        this.plugin = plugin;
        this.reloadConfiguration();
    }

    /* Re-reads the holograms.yml file into memory */
    private void reloadConfiguration() {
        this.persistingHolograms = new Configuration(plugin, "holograms");
        this.storageLocation = persistingHolograms.getStorage();
    }

    @Override
    public void reload() {
        clear();
        reloadConfiguration();
        load();
    }

    /**
     * Loads all saved Holograms
     */
    public void load() {
        if (persistingHolograms == null || storageLocation == null) {
            this.reloadConfiguration();
        }

        // Load all the holograms
        if (storageLocation.get("holograms", String[].class) != null) {
            loadHolograms:
            for (String hologramName : storageLocation.get("holograms", String[].class)) {
                List<String> uncoloredLines = Arrays.asList(storageLocation.get("holograms." + hologramName + ".lines", String[].class));
                Pair<Position, Instance> locationd = LocationUtil.stringAsLocation(storageLocation.get("holograms." + hologramName + ".location", String.class));
                Position location = locationd.left;
                if (location == null) {
                    plugin.getLogger().info("Hologram \"" + hologramName + "\" has an invalid location");
                    continue;
                }

                Instance instance = locationd.right;
				// Create the Hologram
                Hologram hologram = new Hologram(hologramName, location, true, instance );
                // Add the lines
                for (String string : uncoloredLines) {
                    HologramLine line = plugin.parseLine(hologram, string);
                    try {
                        hologram.addLine(line);
                    } catch (HologramEntitySpawnException e) {
                        plugin.getLogger().info("Failed to spawn Hologram \"" + hologramName + "\"", e);
                        continue loadHolograms;
                    }
                }
                hologram.spawn();
                addActiveHologram(hologram);
                plugin.getLogger().info("Loaded hologram with \"" + hologram.getId() + "\" with " + hologram.getLines().size() + " lines");
            }
        } else {
            plugin.getLogger().info("holograms.yml file had no 'holograms' section defined, no holograms loaded");
        }
    }

    @Override
    public void saveHologram(Hologram hologram) {
        String hologramName = hologram.getId();
        Collection<HologramLine> holoLines = hologram.getLines();
        List<String> uncoloredLines = holoLines.stream()
                .map(HologramLine::getRaw)
                .collect(Collectors.toList());
        storageLocation.set("holograms." + hologramName + ".location", LocationUtil.locationAsString(hologram.getLocation(), hologram.getInstance()), String.class);
        storageLocation.set("holograms." + hologramName + ".lines", uncoloredLines.toArray(new String[uncoloredLines.size()]), String[].class);
        storageLocation.set("holograms", activeHolograms.keySet().toArray(new String[activeHolograms.size()]), String[].class);
    }

    @Override
    public void deleteHologram(Hologram hologram) {
        hologram.despawn();
        removeActiveHologram(hologram);
        storageLocation.delete("holograms." + hologram.getId() + ".location");
        storageLocation.delete("holograms." + hologram.getId() + ".lines");
        storageLocation.set("holograms", activeHolograms.keySet().toArray(new String[activeHolograms.size()]), String[].class);
    }

    @Override
    public Hologram getHologram(String name) {
        return activeHolograms.get(name);
    }

    @Override
    public Map<String, Hologram> getActiveHolograms() {
        return activeHolograms;
    }

    @Override
    public void addActiveHologram(Hologram hologram) {
        activeHolograms.put(hologram.getId(), hologram);
    }

    @Override
    public void removeActiveHologram(Hologram hologram) {
        activeHolograms.remove(hologram.getId());
    }

    @Override
    public void trackLine(UpdatingHologramLine line) {
        trackedUpdatingLines.add(line);
    }

    @Override
    public boolean untrackLine(UpdatingHologramLine line) {
        return trackedUpdatingLines.remove(line);
    }

    @Override
    public Collection<? extends UpdatingHologramLine> getTrackedLines() {
        return trackedUpdatingLines;
    }

    @Override
    public void clear() {
        getActiveHolograms().values().forEach(Hologram::despawn);
        getActiveHolograms().clear();
    }
}