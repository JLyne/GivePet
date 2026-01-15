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
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SaddledMountInventory;
import org.bukkit.util.RayTraceResult;
import java.util.List;
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

    LiteralCommandNode<CommandSourceStack> cancelCommand = literal("cancelgive")
        .requires(source -> source.getSender().hasPermission("givepet.give"))
        .executes(this::cancelGive).build();

    LiteralCommandNode<CommandSourceStack> abandonCommand = literal("abandonpet")
        .requires(source -> source.getSender().hasPermission("givepet.abandon"))
        .executes(this::abandonPet).build();

    commands.register(giveCommand, "Transfer one of your pets to someone else.", List.of("transferpet"));
    commands.register(acceptCommand, "Accept a gifted pet.");
    commands.register(declineCommand, "Decline a gifted pet.");
    commands.register(cancelCommand, "Cancel a pending transfer.", List.of("canceltransfer"));
    commands.register(abandonCommand, "Abandon a pet.");
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

    Entity target = findTargetedPet(senderPlayer);

    if (target == null) {
      Lang.send(senderPlayer, Messages.SEND_TARGET_PET);
      return Command.SINGLE_SUCCESS;
    }

    Single petPlaceholder = Placeholder.component("pet", Lang.getPetComponent((LivingEntity) target));

    pendingGift = giftManager.getActivePet(target.getUniqueId());
    if (pendingGift != null) {
      Lang.send(
          sender,
          Messages.SEND_PENDING_PET,
          Placeholder.unparsed("recipient_id", String.valueOf(pendingGift.to().getId())),
          Placeholder.unparsed("recipient", Lang.getName(pendingGift.to())),
          petPlaceholder);
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
        || !recipient.hasPermission("givepet.receive")) {
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
        target.getUniqueId());

    // Remove the gift's target, if any, and make it sit to keep it safer.
    if (target instanceof Mob mob) {
      mob.setTarget(null);
    }

    if (target instanceof Sittable sittable) {
      sittable.setSitting(true);
    }

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
    if (!(entity instanceof LivingEntity livingEntity)) {
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

    livingEntity.eject();

    // Transfer, unsit, and untarget entity.
    if (livingEntity instanceof Tameable tameable) {
      tameable.setOwner(recipient);
      tameable.setTarget(null);
    }

    if (livingEntity instanceof CopperGolem golem) {
      golem.setSummoner(recipient.getUniqueId());
    }

    if (livingEntity instanceof Sittable sittable) {
      sittable.setSitting(false);
    }

    Lang.send(
        recipient,
        Messages.RECEIVE_ACCEPT_RECIPIENT,
        Placeholder.unparsed("owner_id", String.valueOf(gift.from().getId())),
        Placeholder.unparsed("owner", Lang.getName(gift.from())),
        Placeholder.component("pet", Lang.getPetComponent(livingEntity)));
    if (from != null) {
      Lang.send(
          from,
          Messages.RECEIVE_ACCEPT_SENDER,
          Placeholder.unparsed("recipient_id", String.valueOf(gift.to().getId())),
          Placeholder.unparsed("recipient", Lang.getName(gift.to())),
          Placeholder.component("pet", Lang.getPetComponent(livingEntity)));
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
        Placeholder.unparsed("owner", Lang.getName(gift.from())));

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

  private int cancelGive(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();

    if (!(sender instanceof Player senderPlayer)) {
      return Command.SINGLE_SUCCESS;
    }

    // Check pending.
    Gift gift = giftManager.getActiveFrom(senderPlayer.getUniqueId());
    if (gift == null || giftManager.isExpired(gift)) {
      Lang.send(sender, Messages.RECEIVE_NO_PENDING);
      return Command.SINGLE_SUCCESS;
    }

    giftManager.removeTo(gift.to().getId());
    Player recipient = Bukkit.getPlayer(gift.to().getId());
    Entity entity = Bukkit.getEntity(gift.pet());

    if (recipient != null) {
      Lang.send(
          recipient,
          entity instanceof LivingEntity ? Messages.SEND_CANCELLED_TO : Messages.SEND_CANCELLED_TO_UNKNOWN_PET,
          Placeholder.unparsed("owner_id", String.valueOf(gift.from().getId())),
          Placeholder.unparsed("owner", Lang.getName(gift.from())),
          Placeholder.component("pet", entity instanceof LivingEntity le ? Lang.getPetComponent(le) : Component.empty()));
    }

    Lang.send(
        sender,
        entity instanceof LivingEntity ? Messages.SEND_CANCELLED_FROM : Messages.SEND_CANCELLED_FROM_UNKNOWN_PET,
        Placeholder.unparsed("recipient_id", String.valueOf(gift.to().getId())),
        Placeholder.unparsed("recipient", Lang.getName(gift.to())),
        Placeholder.component("pet", entity instanceof LivingEntity le ? Lang.getPetComponent(le) : Component.empty()));

    return Command.SINGLE_SUCCESS;
  }

  private int abandonPet(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();

    if (!(sender instanceof Player senderPlayer)) {
      return Command.SINGLE_SUCCESS;
    }

    LivingEntity target = findTargetedPet(senderPlayer);

    if (target == null) {
      Lang.send(senderPlayer, Messages.ABANDON_TARGET_PET);
      return Command.SINGLE_SUCCESS;
    }

    // Cancel any pending transfer for the pet
    Gift pendingGift = giftManager.getActivePet(target.getUniqueId());
    if (pendingGift != null) {
      giftManager.removeTo(pendingGift.to().getId());
      Player recipient = Bukkit.getPlayer(pendingGift.to().getId());

      if (recipient != null) {
        Lang.send(
            recipient,
            Messages.SEND_CANCELLED_TO,
            Placeholder.unparsed("owner_id", String.valueOf(pendingGift.from().getId())),
            Placeholder.unparsed("owner", Lang.getName(pendingGift.from())),
            Placeholder.component("pet", Lang.getPetComponent(target)));
      }
    }

    target.eject();

    // Abandon, unsit, and untarget entity.
    if (target instanceof Tameable tameable) {
      tameable.setOwner(null);
      tameable.setTarget(null);
    }

    if (target instanceof CopperGolem golem) {
      golem.setSummoner(null);
    }

    if (target instanceof Sittable sittable) {
      sittable.setSitting(false);
    }

    // Drop saddle if present
    if (target instanceof InventoryHolder holder
        && holder.getInventory() instanceof SaddledMountInventory inventory) {
      ItemStack saddle = inventory.getSaddle();

      if (saddle != null) {
        target.getWorld().dropItemNaturally(target.getLocation(), saddle);
        inventory.setSaddle(null);
      }
    }

    Single petPlaceholder = Placeholder.component("pet", Lang.getPetComponent(target));

    Lang.send(
        sender,
        Messages.ABANDON_COMPLETE,
        petPlaceholder);

    return Command.SINGLE_SUCCESS;
  }

  private LivingEntity findTargetedPet(Player player) {
    // Try to locate transfer target.
    Location eyeLocation = player.getEyeLocation();
    RayTraceResult traceResult = player.getWorld().rayTraceEntities(
        eyeLocation,
        eyeLocation.getDirection(),
        5.0,
        e -> e instanceof Tameable || e instanceof CopperGolem);

    // No entities hit
    if (traceResult == null) {
      return null;
    }

    LivingEntity target = (LivingEntity) traceResult.getHitEntity();

    // Entity isn't a pet
    if (target == null
        || (target instanceof Tameable tameable && !tameable.isTamed())
        || (target instanceof CopperGolem copperGolem && copperGolem.getSummoner() == null)) {
      return null;
    }

    PlayerData senderData = GriefPrevention.instance.dataStore.getPlayerData(
        player.getUniqueId());

    // Entity isn't player's pet
    if (!senderData.ignoreClaims &&
        ((target instanceof Tameable tameable && !player.equals(tameable.getOwner()))
            || (target instanceof CopperGolem golem && !player.getUniqueId().equals(golem.getSummoner()))
        )) {
      return null;
    }

    return target;
  }
}
