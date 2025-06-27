package com.github.gpaddons.givepet;

import java.time.Instant;
import java.util.UUID;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;

public record Gift(
    @NotNull PlayerProfile from,
    @NotNull PlayerProfile to,
    @NotNull UUID tamed,
    @NotNull Instant expiration) {

}
