// vim: ts=2:sw=2:expandtab

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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.AuthenticationCredentials;
import org.whispersystems.textsecuregcm.auth.AuthorizationHeader;
import org.whispersystems.textsecuregcm.auth.AuthorizationToken;
import org.whispersystems.textsecuregcm.auth.AuthorizationTokenGenerator;
import org.whispersystems.textsecuregcm.auth.InvalidAuthorizationHeaderException;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.ApnRegistrationId;
import org.whispersystems.textsecuregcm.entities.DeviceInfo;
import org.whispersystems.textsecuregcm.entities.DeviceInfoList;
import org.whispersystems.textsecuregcm.entities.DeviceResponse;
import org.whispersystems.textsecuregcm.entities.GcmRegistrationId;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.partner.Partner;
import org.whispersystems.textsecuregcm.providers.TimeProvider;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import org.whispersystems.textsecuregcm.storage.PendingAccountsManager;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.util.VerificationCode;
import org.whispersystems.textsecuregcm.websocket.WebSocketConnection;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.DatatypeConverter;

import static com.codahale.metrics.MetricRegistry.name;
import io.dropwizard.auth.Auth;

@Path("/v1/accounts")
public class AccountController {

  private final Logger         logger         = LoggerFactory.getLogger(AccountController.class);
  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          newUserMeter   = metricRegistry.meter(name(AccountController.class, "brand_new_user"));

  private final PendingAccountsManager                pendingAccounts;
  private final AccountsManager                       accounts;
  private final RateLimiters                          rateLimiters;
  private final MessagesManager                       messagesManager;
  private final TimeProvider                          timeProvider;
  private final Map<String, Integer>                  testDevices;

  public AccountController(PendingAccountsManager pendingAccounts,
                           AccountsManager accounts,
                           RateLimiters rateLimiters,
                           MessagesManager messagesManager,
                           TimeProvider timeProvider,
                           Map<String, Integer> testDevices)
  {
    this.pendingAccounts  = pendingAccounts;
    this.accounts         = accounts;
    this.rateLimiters     = rateLimiters;
    this.messagesManager  = messagesManager;
    this.timeProvider     = timeProvider;
    this.testDevices      = testDevices;
  }

  @Timed
  @PUT
  @Path("/gcm/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setGcmRegistrationId(@Auth Account account, @Valid GcmRegistrationId registrationId) {
    Device device = account.getAuthenticatedDevice().get();
    device.setApnId(null);
    device.setVoipApnId(null);
    device.setGcmId(registrationId.getGcmRegistrationId());

    if (registrationId.isWebSocketChannel()) device.setFetchesMessages(true);
    else                                     device.setFetchesMessages(false);

    accounts.update(account);
  }

  @Timed
  @DELETE
  @Path("/gcm/")
  public void deleteGcmRegistrationId(@Auth Account account) {
    Device device = account.getAuthenticatedDevice().get();
    device.setGcmId(null);
    device.setFetchesMessages(false);
    accounts.update(account);
  }

  @Timed
  @PUT
  @Path("/apn/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setApnRegistrationId(@Auth Account account, @Valid ApnRegistrationId registrationId) {
    Device device = account.getAuthenticatedDevice().get();
    device.setApnId(registrationId.getApnRegistrationId());
    device.setVoipApnId(registrationId.getVoipRegistrationId());
    device.setGcmId(null);
    device.setFetchesMessages(true);
    accounts.update(account);
  }

  @Timed
  @DELETE
  @Path("/apn/")
  public void deleteApnRegistrationId(@Auth Account account) {
    Device device = account.getAuthenticatedDevice().get();
    device.setApnId(null);
    device.setFetchesMessages(false);
    accounts.update(account);
  }

  @Timed
  @PUT
  @Path("/attributes/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setAccountAttributes(@Auth Account account,
                                   @HeaderParam("X-Signal-Agent") String userAgent,
                                   @Valid AccountAttributes attributes)
  {
    Device device = account.getAuthenticatedDevice().get();

    if (attributes.getName() != null) {
      device.setName(attributes.getName());
    }
    if (attributes.getPassword() != null) {
      device.setAuthenticationCredentials(new AuthenticationCredentials(attributes.getPassword()));
    }
    if (attributes.getUserAgent() != null) {
      device.setUserAgent(attributes.getUserAgent());
    } else if (userAgent != null) {
      device.setUserAgent(userAgent);
    }
    device.setFetchesMessages(attributes.getFetchesMessages());
    device.setVoiceSupported(attributes.getVoice());
    device.setRegistrationId(attributes.getRegistrationId());
    device.setSignalingKey(attributes.getSignalingKey());
    device.setLastSeen(Util.todayInMillis());

    accounts.update(account);
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/user/{userId}")
  public DeviceResponse resetAccount(@Auth Partner trustedPartner,
                                     @PathParam("userId") String userId,
                                     @Valid AccountAttributes attrs) {
    Optional<Account> priorAccount = accounts.get(userId);
    long deviceId = 1;
    if (priorAccount.isPresent()) {
      logger.warn("Replacing existing account: " + userId);
      deviceId = priorAccount.get().getNextDeviceId();
    } else {
      logger.info("Creating account: " + userId);
    }

    Device device = new Device();
    device.setId(deviceId);
    device.setName(attrs.getName());
    device.setUserAgent(attrs.getUserAgent());
    device.setAuthenticationCredentials(new AuthenticationCredentials(attrs.getPassword()));
    device.setSignalingKey(attrs.getSignalingKey());
    device.setFetchesMessages(attrs.getFetchesMessages());
    device.setRegistrationId(attrs.getRegistrationId());
    device.setLastSeen(Util.todayInMillis());
    device.setCreated(System.currentTimeMillis());

    Account account = new Account();
    account.setNumber(userId);
    account.addDevice(device);

    messagesManager.clear(userId);

    if (accounts.create(account)) {
      newUserMeter.mark();
    }

    return new DeviceResponse(device.getId());
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/user/{userId}")
  public DeviceInfoList getAccount(@Auth Partner trustedPartner,
                                   @PathParam("userId") String userId) {
    Optional<Account> account = accounts.get(userId);
    if (!account.isPresent()) {
      throw new WebApplicationException(Response.status(404).build());
    }
    List<DeviceInfo> devices = new LinkedList<>();
    for (Device device : account.get().getDevices()) {
      devices.add(new DeviceInfo(device.getId(), device.getName(),
                                 device.getLastSeen(), device.getCreated()));
    }
    return new DeviceInfoList(devices);
  }

  @VisibleForTesting protected VerificationCode generateVerificationCode(String number) {
    try {
      if (testDevices.containsKey(number)) {
        return new VerificationCode(testDevices.get(number));
      }

      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      int randomInt       = 100000 + random.nextInt(900000);
      return new VerificationCode(randomInt);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
