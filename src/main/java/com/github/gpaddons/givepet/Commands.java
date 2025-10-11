package com.github.gpaddons.givepet;

import com.github.gpaddons.givepet.lang.Messages;
import com.github.gpaddons.util.lang.Lang;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Single;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.util.RayTraceResult;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public class Commands {
  private final GiftManager giftManager;

  private static final Single ACCEPTPET = Placeholder.component("acceptpet",
      Lang.getCommandComponent("/acceptpet", "/acceptpet"));
  private static final Single DECLINEPET = Placeholder.component("declinepet",
      Lang.getCommandComponent("/declinepet", "/declinepet"));

  public Commands(io.papermc.paper.command.brigadier.Commands commands, GiftManager manager) {
    this.giftManager = manager;

    LiteralCommandNode<CommandSourceStack> giveCommand = literal("givepet")
        .requires(source -> source.getSender().hasPermission("givepet.give"))
        .then(argument("player", ArgumentTypes.player())
            .executes(this::givePet)).build();

    LiteralCommandNode<CommandSourceStack> acceptCommand = literal("acceptpet")
        .requires(source -> source.getSender().hasPermission("givepet.receive"))
        .executes(this::acceptPet).build();

    LiteralCommandNode<CommandSourceStack> declineCommand = literal("declinepet")
        .requires(source -> source.getSender().hasPermission("givepet.receive"))
        .executes(this::declinePet).build();

    commands.register(giveCommand, "Give someone a pet animal of yours!");
    commands.register(acceptCommand, "Accept a gifted pet!");
    commands.register(declineCommand, "Decline a gifted pet.");
  }

  private int givePet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    CommandSender sender = ctx.getSource().getSender();

    if (!(sender instanceof Player senderPlayer)) {
      return Command.SINGLE_SUCCESS;
    }

    // Check for pending gifts from sender.
    Gift pendingGift = giftManager.getActiveFrom(senderPlayer.getUniqueId());
    if (pendingGift != null) {
      Lang.send(
          sender,
          Messages.SEND_PENDING_FROM,
          Placeholder.unparsed("recipient_id", String.valueOf(pendingGift.to().getId())),
          Placeholder.unparsed("recipient", Lang.getName(pendingGift.to())));
      return Command.SINGLE_SUCCESS;
    }

    // Try to locate transfer target.
    Location eyeLocation = senderPlayer.getEyeLocation();
    RayTraceResult traceResult = senderPlayer.getWorld().rayTraceEntities(
        eyeLocation,
        eyeLocation.getDirection(),
        5.0,
        Tameable.class::isInstance);

    if (traceResult == null
            || !(traceResult.getHitEntity() instanceof Tameable tameable)
            || !tameable.isTamed()) {
      Lang.send(sender, Messages.SEND_TARGET_PET);
      return Command.SINGLE_SUCCESS;
    }

    PlayerData senderData = GriefPrevention.instance.dataStore.getPlayerData(
        senderPlayer.getUniqueId());
    if (!senderData.ignoreClaims && !senderPlayer.equals(tameable.getOwner())) {
      Lang.send(sender, Messages.SEND_TARGET_PET);
      return Command.SINGLE_SUCCESS;
    }

    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
    Player recipient = resolver.resolve(ctx.getSource()).getFirst();

    if (!senderPlayer.canSee(recipient)) {
      senderPlayer.sendMessage(Component.translatable("argument.entity.notfound.player").color(
          NamedTextColor.RED));
      return Command.SINGLE_SUCCESS;
    }

    if (senderPlayer.equals(recipient)
        || senderData.ignoredPlayers.containsKey(recipient.getUniqueId())
        || !recipient.hasPermission("givepet.receive")
        || GriefPrevention.instance.dataStore.getPlayerData(
            recipient.getUniqueId()).ignoredPlayers.containsKey(senderPlayer.getUniqueId())) {
        Lang.send(sender, Messages.SEND_NO_RECIPIENT);
        return Command.SINGLE_SUCCESS;
    }

    // Check for active pending gifts to recipient. If an expired gift exists, we will clobber it.
    pendingGift = giftManager.getActiveTo(recipient.getUniqueId());
    if (pendingGift != null) {
      Lang.send(
          sender,
          Messages.SEND_PENDING_TO,
          Placeholder.unparsed("recipient_id", String.valueOf(recipient.getPlayerProfile().getId())),
          Placeholder.unparsed("recipient", Lang.getName(recipient.getPlayerProfile())));
      return Command.SINGLE_SUCCESS;
    }

    giftManager.addGift(
        senderPlayer.getPlayerProfile(),
        recipient.getPlayerProfile(),
        tameable.getUniqueId());

    // Remove the gift's target, if any, and make it sit to keep it safer.
    tameable.setTarget(null);

    if (tameable instanceof Sittable sittable) {
      sittable.setSitting(true);
    }

    Single petPlaceholder = Placeholder.component("pet", Lang.getPetComponent(tameable));

    Lang.send(
        recipient,
        Messages.SEND_OFFER,
        Placeholder.unparsed("owner_id", String.valueOf(senderPlayer.getPlayerProfile().getId())),
        Placeholder.unparsed("owner", Lang.getName(senderPlayer.getPlayerProfile())),
        petPlaceholder,
        ACCEPTPET,
        DECLINEPET);
    Lang.send(
        sender,
        Messages.SEND_OFFERED,
        Placeholder.unparsed("recipient_id", String.valueOf(recipient.getPlayerProfile().getId())),
        Placeholder.unparsed("recipient", Lang.getName(recipient.getPlayerProfile())),
        petPlaceholder);

    return Command.SINGLE_SUCCESS;
  }

  private int acceptPet(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();

    if (!(sender instanceof Player recipient)) {
      return Command.SINGLE_SUCCESS;
    }

    // Check pending.
    Gift gift = giftManager.removeTo(recipient.getUniqueId());
    if (gift == null || giftManager.isExpired(gift)) {
      Lang.send(sender, Messages.RECEIVE_NO_PENDING);
      return Command.SINGLE_SUCCESS;
    }

    // From UUID is never null - profile is from a previously online player and is complete.
    Player from = Bukkit.getPlayer(Objects.requireNonNull(gift.from().getId()));

    // Check entity.
    Entity entity = Bukkit.getEntity(gift.pet());
    if (!(entity instanceof Tameable tameable)) {
      if (from != null) {
        Lang.send(
            from,
            Messages.RECEIVE_NOT_FOUND_SENDER,
            Placeholder.unparsed("recipient_id",
                String.valueOf(recipient.getPlayerProfile().getId())),
            Placeholder.unparsed("recipient", Lang.getName(recipient.getPlayerProfile())));
      }
      Lang.send(recipient, Messages.RECEIVE_NOT_FOUND_RECIPIENT,
          Placeholder.unparsed("owner_id", String.valueOf(gift.from().getId())),
          Placeholder.unparsed("owner", Lang.getName(gift.from())));
      return Command.SINGLE_SUCCESS;
    }

    // Transfer, unsit, and untarget entity.
    tameable.setOwner(recipient);
    tameable.setTarget(null);

    if (tameable instanceof Sittable sittable) {
      sittable.setSitting(false);
    }

    Lang.send(
        recipient,
        Messages.RECEIVE_ACCEPT_RECIPIENT,
        Placeholder.unparsed("owner_id", String.valueOf(gift.from().getId())),
        Placeholder.unparsed("owner", Lang.getName(gift.from())),
        Placeholder.component("pet", Lang.getPetComponent(tameable)));
    if (from != null) {
      Lang.send(
          from,
          Messages.RECEIVE_ACCEPT_SENDER,
          Placeholder.unparsed("recipient_id", String.valueOf(gift.to().getId())),
          Placeholder.unparsed("recipient", Lang.getName(gift.to())),
          Placeholder.component("pet", Lang.getPetComponent(tameable)));
    }

    return Command.SINGLE_SUCCESS;
  }

  private int declinePet(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();

    if (!(sender instanceof Player recipient)) {
      return Command.SINGLE_SUCCESS;
    }

    // Check pending.
    Gift gift = giftManager.removeTo(recipient.getUniqueId());
    if (gift == null) {
      Lang.send(sender, Messages.RECEIVE_NO_PENDING);
      return Command.SINGLE_SUCCESS;
    }

    Lang.send(
        sender,
        Messages.RECEIVE_DECLINE_RECIPIENT,
        Placeholder.unparsed("owner_id", String.valueOf(gift.from().getId())),
        Placeholder.unparsed("owner", Lang.getName(gift.from())),
        Placeholder.component("ignore", Lang.getCommandComponent("/ignore",
            "/griefprevention:ignore " + gift.from().getName())));

    if (!giftManager.isExpired(gift)) {
      Player from = Bukkit.getPlayer(Objects.requireNonNull(gift.from().getId()));
      if (from != null) {
        Lang.send(from, Messages.RECEIVE_DECLINE_SENDER,
            Placeholder.unparsed("recipient_id", String.valueOf(gift.to().getId())),
            Placeholder.unparsed("recipient", Lang.getName(gift.to())));
      }
    }

    return Command.SINGLE_SUCCESS;
  }
}
