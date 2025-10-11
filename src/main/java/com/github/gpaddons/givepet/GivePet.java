package com.github.gpaddons.givepet;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class GivePet extends JavaPlugin {

  @Override
  public void onEnable() {
    LifecycleEventManager<@NotNull Plugin> manager = getLifecycleManager();
    manager.registerEventHandler(LifecycleEvents.COMMANDS,
        event -> new Commands(event.registrar(), new GiftManager()));
  }
}
