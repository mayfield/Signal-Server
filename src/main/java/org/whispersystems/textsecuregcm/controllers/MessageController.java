/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.entities.IncomingMessage;
import org.whispersystems.textsecuregcm.entities.IncomingMessageList;
import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.entities.MismatchedDevices;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntity;
import org.whispersystems.textsecuregcm.entities.OutgoingMessageEntityList;
import org.whispersystems.textsecuregcm.entities.SendMessageResponse;
import org.whispersystems.textsecuregcm.entities.StaleDevices;
import org.whispersystems.textsecuregcm.federation.FederatedClient;
import org.whispersystems.textsecuregcm.federation.FederatedClientManager;
import org.whispersystems.textsecuregcm.federation.NoSuchPeerException;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.push.NotPushRegisteredException;
import org.whispersystems.textsecuregcm.push.PushSender;
import org.whispersystems.textsecuregcm.push.ReceiptSender;
import org.whispersystems.textsecuregcm.push.TransientPushFailureException;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.util.Base64;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.websocket.WebSocketConnection;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.dropwizard.auth.Auth;

@Path("/v1/messages")
public class MessageController {

  private final Logger logger = LoggerFactory.getLogger(MessageController.class);

  private final RateLimiters           rateLimiters;
  private final PushSender             pushSender;
  private final ReceiptSender          receiptSender;
  private final FederatedClientManager federatedClientManager;
  private final AccountsManager        accountsManager;
  private final MessagesManager        messagesManager;

  public MessageController(RateLimiters rateLimiters,
                           PushSender pushSender,
                           ReceiptSender receiptSender,
                           AccountsManager accountsManager,
                           MessagesManager messagesManager,
                           FederatedClientManager federatedClientManager)
  {
    this.rateLimiters           = rateLimiters;
    this.pushSender             = pushSender;
    this.receiptSender          = receiptSender;
    this.accountsManager        = accountsManager;
    this.messagesManager        = messagesManager;
    this.federatedClientManager = federatedClientManager;
  }

  @Timed
  @Path("/{destination}")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public SendMessageResponse sendMessage(@Auth                     Account source,
                                         @PathParam("destination") String destinationName,
                                         @Valid                    IncomingMessageList messages)
      throws IOException, RateLimitExceededException
  {
    rateLimiters.getMessagesLimiter().validate(source.getNumber() + "__" + destinationName);

    try {
      boolean isSyncMessage = source.getNumber().equals(destinationName);

      if (Util.isEmpty(messages.getRelay())) sendLocalMessage(source, destinationName, messages, isSyncMessage);
      else                                   sendRelayMessage(source, destinationName, messages, isSyncMessage);

      return new SendMessageResponse(!isSyncMessage && source.getActiveDeviceCount() > 1);
    } catch (NoSuchUserException e) {
      throw new WebApplicationException(Response.status(404).build());
    } catch (MismatchedDevicesException e) {
      throw new WebApplicationException(Response.status(409)
                                                .type(MediaType.APPLICATION_JSON_TYPE)
                                                .entity(new MismatchedDevices(e.getMissingDevices(),
                                                                              e.getExtraDevices()))
                                                .build());
    } catch (StaleDevicesException e) {
      throw new WebApplicationException(Response.status(410)
                                                .type(MediaType.APPLICATION_JSON)
                                                .entity(new StaleDevices(e.getStaleDevices()))
                                                .build());
    } catch (InvalidDestinationException e) {
      throw new WebApplicationException(Response.status(400).build());
    }
  }

  @Timed
  @Path("/{destination}/{deviceId}")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public SendMessageResponse sendMessage(@Auth                     Account source,
                                         @PathParam("destination") String destinationName,
                                         @PathParam("deviceId")    Long deviceId,
                                         @Valid                    IncomingMessage message)
      throws IOException, RateLimitExceededException
  {
    rateLimiters.getMessagesLimiter().validate(source.getNumber() + "__" + destinationName);
    if (message.getDestinationDeviceId() != deviceId) {
      logger.error("Destination deviceId mismatched with message payload");
      throw new WebApplicationException(Response.status(400).build());
    }
    if (message.getTimestamp() == 0) {
      logger.error("Timestamp required in message payload");
      throw new WebApplicationException(Response.status(400).build());
    }
    Account destination;
    try {
      destination = getDestinationAccount(destinationName);
    } catch (NoSuchUserException e) {
      throw new WebApplicationException(Response.status(404).build());
    }
    Optional<Device> device = destination.getDevice(deviceId);
    if (device.isPresent()) {
      if (message.getDestinationRegistrationId() > 0 &&
          message.getDestinationRegistrationId() != device.get().getRegistrationId()) {
        throw new WebApplicationException(Response.status(410).build());
      }
      if (sendLocalMessage(source, destination, device.get(), message.getTimestamp(), message)) {
        boolean isSyncMessage = source.getNumber().equals(destinationName);
        return new SendMessageResponse(!isSyncMessage && source.getActiveDeviceCount() > 1);
      }
    }
    throw new WebApplicationException(Response.status(404).build());
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public OutgoingMessageEntityList getPendingMessages(@Auth Account account) {
    return messagesManager.getMessagesForDevice(account.getNumber(),
                                                account.getAuthenticatedDevice().get().getId());
  }

  @Timed
  @DELETE
  @Path("/{source}/{timestamp}")
  public void removePendingMessage(@Auth Account account,
                                   @PathParam("source") String source,
                                   @PathParam("timestamp") long timestamp)
      throws IOException
  {
    try {
      WebSocketConnection.messageTime.update(System.currentTimeMillis() - timestamp);

      Optional<OutgoingMessageEntity> message = messagesManager.delete(account.getNumber(),
                                                                       account.getAuthenticatedDevice().get().getId(),
                                                                       source, timestamp);

      if (message.isPresent() && message.get().getType() != Envelope.Type.RECEIPT_VALUE) {
        receiptSender.sendReceipt(account,
                                  message.get().getSource(),
                                  message.get().getTimestamp(),
                                  Optional.fromNullable(message.get().getRelay()));
      }
    } catch (NotPushRegisteredException e) {
      logger.info("User no longer push registered for delivery receipt: " + e.getMessage());
    } catch (NoSuchUserException | TransientPushFailureException e) {
      logger.warn("Sending delivery receipt", e);
    }
  }


  private void sendLocalMessage(Account source,
                                String destinationName,
                                IncomingMessageList messages,
                                boolean isSyncMessage)
      throws NoSuchUserException,
             MismatchedDevicesException,
             StaleDevicesException,
             InvalidDestinationException
  {
    Account destination = isSyncMessage ? source : getDestinationAccount(destinationName);
    boolean found = false;

    validateCompleteDeviceList(destination, messages.getMessages(), isSyncMessage);
    validateRegistrationIds(destination, messages.getMessages());

    for (IncomingMessage incomingMessage : messages.getMessages()) {
      Optional<Device> destinationDevice = destination.getDevice(incomingMessage.getDestinationDeviceId());

      if (destinationDevice.isPresent()) {
        if (sendLocalMessage(source, destination, destinationDevice.get(), messages.getTimestamp(), incomingMessage)) {
          found = true;
        }
      }
    }
    if (!found && !isSyncMessage) {
      throw new NoSuchUserException(destinationName);
    }
  }

  private boolean sendLocalMessage(Account source,
                                   Account destinationAccount,
                                   Device destinationDevice,
                                   long timestamp,
                                   IncomingMessage incomingMessage)
  {
    try {
      Optional<byte[]> messageBody    = getMessageBody(incomingMessage);
      Optional<byte[]> messageContent = getMessageContent(incomingMessage);
      Envelope.Builder messageBuilder = Envelope.newBuilder();

      messageBuilder.setType(Envelope.Type.valueOf(incomingMessage.getType()))
                    .setSource(source.getNumber())
                    .setTimestamp(timestamp == 0 ? System.currentTimeMillis() : timestamp)
                    .setSourceDevice((int) source.getAuthenticatedDevice().get().getId());

      if (messageBody.isPresent()) {
        messageBuilder.setLegacyMessage(ByteString.copyFrom(messageBody.get()));
      }

      if (messageContent.isPresent()) {
        messageBuilder.setContent(ByteString.copyFrom(messageContent.get()));
      }

      if (source.getRelay().isPresent()) {
        messageBuilder.setRelay(source.getRelay().get());
      }

      pushSender.sendMessage(destinationAccount, destinationDevice, messageBuilder.build(), incomingMessage.isSilent());
    } catch (NotPushRegisteredException e) {
      logger.warn("Device not registered", e);
      return false;
    }
    return true;
  }

  private void sendRelayMessage(Account source,
                                String destinationName,
                                IncomingMessageList messages,
                                boolean isSyncMessage)
      throws IOException, NoSuchUserException, InvalidDestinationException
  {
    if (isSyncMessage) throw new InvalidDestinationException("Transcript messages can't be relayed!");

    try {
      FederatedClient client = federatedClientManager.getClient(messages.getRelay());
      client.sendMessages(source.getNumber(), source.getAuthenticatedDevice().get().getId(),
                          destinationName, messages);
    } catch (NoSuchPeerException e) {
      throw new NoSuchUserException(e);
    }
  }

  private Account getDestinationAccount(String destination)
      throws NoSuchUserException
  {
    Optional<Account> account = accountsManager.get(destination);

    if (!account.isPresent() || !account.get().isActive()) {
      throw new NoSuchUserException(destination);
    }

    return account.get();
  }

  private void validateRegistrationIds(Account account, List<IncomingMessage> messages)
      throws StaleDevicesException
  {
    List<Long> staleDevices = new LinkedList<>();

    for (IncomingMessage message : messages) {
      Optional<Device> device = account.getDevice(message.getDestinationDeviceId());

      if (device.isPresent() &&
          message.getDestinationRegistrationId() > 0 &&
          message.getDestinationRegistrationId() != device.get().getRegistrationId())
      {
        staleDevices.add(device.get().getId());
      }
    }

    if (!staleDevices.isEmpty()) {
      throw new StaleDevicesException(staleDevices);
    }
  }

  private void validateCompleteDeviceList(Account account,
                                          List<IncomingMessage> messages,
                                          boolean isSyncMessage)
      throws MismatchedDevicesException, InvalidDestinationException
  {
    Set<Long> messageDeviceIds = new HashSet<>();
    Set<Long> accountDeviceIds = new HashSet<>();

    List<Long> missingDeviceIds = new LinkedList<>();
    List<Long> extraDeviceIds   = new LinkedList<>();
    List<Long> dupCheck         = new LinkedList<>();

    for (IncomingMessage message : messages) {
      messageDeviceIds.add(message.getDestinationDeviceId());
    }

    for (Device device : account.getDevices()) {
      if (device.isActive() &&
          !(isSyncMessage && device.getId() == account.getAuthenticatedDevice().get().getId()))
      {
        accountDeviceIds.add(device.getId());

        if (!messageDeviceIds.contains(device.getId())) {
          missingDeviceIds.add(device.getId());
        }
      }
    }

    for (IncomingMessage message : messages) {
      Long deviceId = message.getDestinationDeviceId();
      if (dupCheck.contains(deviceId)) {
        throw new InvalidDestinationException("Duplicate device id used in messages array");
      }
      dupCheck.add(deviceId);
      if (!accountDeviceIds.contains(deviceId)) {
        extraDeviceIds.add(deviceId);
      }
    }

    if (!missingDeviceIds.isEmpty() || !extraDeviceIds.isEmpty()) {
      throw new MismatchedDevicesException(missingDeviceIds, extraDeviceIds);
    }
  }

  private Optional<byte[]> getMessageBody(IncomingMessage message) {
    if (Util.isEmpty(message.getBody())) return Optional.absent();

    try {
      return Optional.of(Base64.decode(message.getBody()));
    } catch (IOException ioe) {
      logger.debug("Bad B64", ioe);
      return Optional.absent();
    }
  }

  private Optional<byte[]> getMessageContent(IncomingMessage message) {
    if (Util.isEmpty(message.getContent())) return Optional.absent();

    try {
      return Optional.of(Base64.decode(message.getContent()));
    } catch (IOException ioe) {
      logger.debug("Bad B64", ioe);
      return Optional.absent();
    }
  }
}
