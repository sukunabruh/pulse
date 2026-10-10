package com.example.pulse.module;

import com.example.pulse.event.EventBus;
import com.example.pulse.event.HudEvent;
import com.example.pulse.event.TickEvent;
import com.example.pulse.module.impl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager(EventBus bus) {
        modules.add(new SprintModule());
        modules.add(new AutoWalkModule());
        modules.add(new FreecamModule());
        modules.add(new AutoToolModule());
        modules.add(new FullbrightModule());
        modules.add(new ZoomModule());
        modules.add(new HudModule(this::getModules));
        modules.add(new FakeStatsModule());
        modules.add(new BlockEspModule());
        modules.add(new EntityEspModule());
        modules.add(new SpawnerFinderModule());
        modules.add(new SusChunkFinderModule());
        modules.add(new BaseFinderModule());
        modules.add(new WaypointsModule());

        bus.subscribe(TickEvent.class, e -> {
            for (Module m : modules) {
                m.pollToggleKey();
                if (m.isEnabled()) m.onTick();
            }
        });

        bus.subscribe(HudEvent.class, e -> {
            float tp = e.tickCounter().getTickProgress(false);
            for (Module m : modules) {
                if (m.isEnabled()) m.onHud(e.context(), tp);
            }
        });
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) if (type.isInstance(m)) return type.cast(m);
        return null;
    }

    public List<Module> getModules() { return Collections.unmodifiableList(modules); }

    public List<Module> getByCategory(Category category) {
        return modules.stream().filter(m -> m.category == category).toList();
    }

    /** Undo side effects (FOV, status effects, camera, held keys) without changing saved flags. */
    public void shutdown() {
        for (Module m : modules) {
            if (m.isEnabled()) m.onDisable();
        }
    }
}
