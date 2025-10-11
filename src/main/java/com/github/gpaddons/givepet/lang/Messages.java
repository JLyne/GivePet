package com.github.gpaddons.givepet.lang;

import com.github.gpaddons.util.lang.value.ConfigMessage;
import org.jetbrains.annotations.NotNull;

public enum Messages implements ConfigMessage {

  SEND_PENDING_FROM("send.pending.from", "<red>You already have a pending transfer to <aqua><recipient>!<red> Please wait for them to respond."),
  SEND_PENDING_TO("send.pending.to", "<aqua><recipient><green> already has a pending pet transfer! Please wait for them to respond."),
  SEND_TARGET_PET("send.target_pet", "<red>You must target one of your pets to transfer!"),
  SEND_NO_RECIPIENT("send.no_recipient", "<red>Invalid recipient!"),
  SEND_OFFER("send.offer", "<aqua><owner><green> would like to transfer <aqua>[<pet>]<green> to you!\nYou have two minutes to <aqua>[<acceptpet>]<green> or <aqua>[<declinepet>]<green>."),
  SEND_OFFERED("send.offered", "<green>Offered <aqua>[<pet>]<green> to <aqua><recipient><green>! They have two minutes to respond."),

  RECEIVE_NO_PENDING("receive.no_pending", "<red>You don't have any pending pet transfers!"),
  RECEIVE_NOT_FOUND_SENDER("receive.not_found.sender", "<red>Unable to transfer pet! Please make sure you stay near it until the transfer completes."),
  RECEIVE_NOT_FOUND_RECIPIENT("receive.not_found.recipient", "<red>Unable to locate pet!"),
  RECEIVE_ACCEPT_SENDER("receive.accept.sender", "<green>Pet transferred!"),
  RECEIVE_ACCEPT_RECIPIENT("receive.accept.recipient", "<green>Pet transferred!"),
  RECEIVE_DECLINE_SENDER("receive.decline.sender", "<red>Pet transfer declined!"),
  RECEIVE_DECLINE_RECIPIENT("receive.decline.recipient", "<green>Declined pet transfer from <aqua><owner><green>!\nIf they continue to send you unwanted pets, you can <aqua>[<ignore>]<green> them.");

  private final String key;
  private final String defaultValue;

  Messages(String key, String defaultValue) {
    this.key = key;
    this.defaultValue = defaultValue;
  }

  @Override
  public @NotNull String getKey() {
    return key;
  }

  @Override
  public @NotNull String getDefault() {
    return defaultValue;
  }

}
