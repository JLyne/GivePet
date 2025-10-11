package com.github.gpaddons.util.lang;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.gpaddons.util.lang.value.CommonValues;
import com.github.gpaddons.util.lang.value.ConfigMessage;
import com.github.gpaddons.util.lang.value.ConfigRequired;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A basic translation manager.
 */
public final class Lang {
  private static final String LANG_FILE = "lang.yml";
  private static final @NotNull YamlConfiguration LANG;
  private static final LoadingCache<UUID, PlayerProfile> PROFILES = CacheBuilder.newBuilder()
      .maximumSize(20)
      .build(CacheLoader.from(uuid -> Bukkit.getOfflinePlayer(uuid).getPlayerProfile()));

  static {
    Plugin plugin = JavaPlugin.getProvidingPlugin(Lang.class);
    File file = new File(plugin.getDataFolder(), LANG_FILE);

    if (!file.exists()) {
      write(plugin, file);
    }

    LANG = YamlConfiguration.loadConfiguration(file);
  }

  private Lang() {
  }

  private static void write(@NotNull Plugin plugin, @NotNull File file) {
    try (InputStream resource = plugin.getResource(LANG_FILE)) {
      if (resource == null) {
        plugin.getLogger().log(Level.WARNING, () -> "Unable to load resource " + LANG_FILE);
        return;
      }

      Path writeTo = file.toPath();

      Files.createDirectories(writeTo.getParent());

      Files.copy(resource, writeTo);
    } catch (IOException e) {
      plugin.getLogger().log(Level.WARNING, e.getMessage(), e);
    }
  }

  private static @Nullable String get(@NotNull ConfigMessage message) {
    String value = LANG.getString(message.getKey(), message.getDefault());
    return value.isBlank() ? null : value;
  }

  /**
   * Send a {@link ConfigMessage} to the specified CommandSender after applying any variable
   * replacements.
   *
   * <p>Messages that are configured to be null or blank are not sent.
   *
   * @param recipient     the recipient of the messagee
   * @param message       the Message to send
   * @param tagResolvers  tag resolvers
   */
  public static void send(
      @NotNull CommandSender recipient,
      @NotNull ConfigMessage message,
      @NotNull TagResolver @NotNull ...tagResolvers) {
    String value = get(message);

    if (value == null) {
      return;
    }

    recipient.sendRichMessage(value, tagResolvers);
  }

  /**
   * Get a name for a UUID.
   *
   * <p>If the UUID is null, the name is the configured administrator name message.
   *
   * @param uuid the UUID
   * @return the name for the UUID
   * @see #getName(PlayerProfile)
   */
  public static @NotNull String getName(@Nullable UUID uuid) {
    if (uuid == null) {
      return get(CommonValues.ADMIN);
    }

    return getName(PROFILES.getUnchecked(uuid));
  }

  /**
   * Get the value for a {@link ConfigRequired}.
   *
   * <p>Because the values are used in replacement, they are never null or empty.
   *
   * @param message the ComponentMessage
   * @return the value set or the default if unset
   */
  public static @NotNull String get(@NotNull ConfigRequired message) {
    String value = LANG.getString(message.getKey(), null);
    return value != null && !value.isBlank() ? value : message.getDefault();
  }

  /**
   * Get a name for an OfflinePlayer.
   *
   * <p>If the player has been removed from the user cache, the name will be the configured unnamed
   * player message.
   *
   * @param offlinePlayer the player
   * @return the name of the player
   */
  public static @NotNull String getName(@NotNull PlayerProfile offlinePlayer) {
    String name = offlinePlayer.getName();

    if (name != null) {
      return name;
    }

    return get(CommonValues.UNNAMED_PLAYER).replace("<uuid>",
        Objects.requireNonNull(offlinePlayer.getId()).toString());
  }

  /**
   * Get the component for a pet. The component will show their custom name, falling back to
   * the vanilla translation. A hover event showing more information is added.
   * @param pet The pet to populate the component with
   * @return The component
   */
  public static @NotNull Component getPetComponent(LivingEntity pet) {
    Component defaultName = Component.translatable(pet.getType().translationKey());
    Component customName = pet.customName();
    Component component = customName != null ? customName : defaultName;
    Component hover = customName != null ?
        Component.translatable("commands.list.nameAndId", customName, defaultName) :
        defaultName;

    if (pet instanceof Tameable tameable) {
      AnimalTamer owner = tameable.getOwner();
      if (owner instanceof OfflinePlayer player) {
        hover = hover.appendNewline().append(Component.text(Lang.getName(player.getPlayerProfile())));
      }
    } else if(pet instanceof CopperGolem golem) {
      UUID ownerUUID = golem.getSummoner();

      if (ownerUUID != null) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
        hover = hover.appendNewline().append(Component.text(Lang.getName(owner.getPlayerProfile())));
      }
    }

    return component.hoverEvent(HoverEvent.showText(hover));
  }

  /**
   * Get the clickable component for a command
   * @param label - Text to use
   * @param command - Command to run on click
   * @return The component
   */
  public static @NotNull Component getCommandComponent(String label, String command) {
    Component hover = Component.translatable("narration.button.usage.hovered")
            .append(Component.newline())
            .append(Component.text(command));
    HoverEvent<Component> hoverEvent = HoverEvent.showText(hover);
    ClickEvent clickEvent = ClickEvent.runCommand(command);

    return Component.text(label).hoverEvent(hoverEvent).clickEvent(clickEvent);
  }
}
