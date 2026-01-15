package com.github.gpaddons.givepet;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GiftManager {

  private final Map<UUID, Gift> from = new HashMap<>();
  private final Map<UUID, Gift> to = new HashMap<>();
  private final Map<UUID, Gift> pets = new HashMap<>();

  GiftManager() {
  }

  public void addGift(@NotNull PlayerProfile sender, @NotNull PlayerProfile recipient,
      @NotNull UUID entity) {
    Gift gift = new Gift(sender, recipient, entity, Instant.now().plus(2, ChronoUnit.MINUTES));
    from.put(sender.getId(), gift);
    to.put(recipient.getId(), gift);
    pets.put(entity, gift);
  }

  public @Nullable Gift getActiveFrom(@NotNull UUID sender) {
    return from.computeIfPresent(sender, (uuid, existing) -> {
      if (isExpired(existing)) {
        pets.remove(existing.pet(), existing);
        // We intentionally leave the "to" version active so as to warn that a previous sending expired.
        return null;
      }
      return existing;
    });
  }

  public boolean isExpired(@NotNull Gift gift) {
    return gift.expiration().isBefore(Instant.now());
  }

  public @Nullable Gift getActiveTo(@NotNull UUID recipient) {
    return from.computeIfPresent(recipient, (uuid, existing) -> {
      if (isExpired(existing)) {
        pets.remove(existing.pet(), existing);
        from.remove(existing.from().getId(), existing);
        return null;
      }
      return existing;
    });
  }

  public @Nullable Gift getActivePet(@NotNull UUID pet) {
    return pets.computeIfPresent(pet, (uuid, existing) -> {
      if (isExpired(existing)) {
        from.remove(existing.from().getId(), existing);
        // We intentionally leave the "to" version active so as to warn that a previous sending expired.
        return null;
      }
      return existing;
    });
  }

  public @Nullable Gift getTo(@NotNull UUID recipient) {
    return to.get(recipient);
  }

  public @Nullable Gift removeTo(@NotNull UUID recipient) {
    Gift pending = to.remove(recipient);
    if (pending != null) {
      // If from contains same pending entry, also remove from.
      from.remove(pending.from().getId(), pending);
      pets.remove(pending.pet(), pending);
    }
    return pending;
  }
}
