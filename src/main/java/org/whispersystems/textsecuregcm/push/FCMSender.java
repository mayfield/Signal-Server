// vim: ts=2:sw=2:expandtab

package org.whispersystems.textsecuregcm.push;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.whispersystems.textsecuregcm.configuration.FirebaseConfiguration;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.Constants;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;
import io.dropwizard.lifecycle.Managed;

public class FCMSender implements Managed {

  private final Logger logger = LoggerFactory.getLogger(FCMSender.class);

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          success        = metricRegistry.meter(name(getClass(), "sent", "success"));
  private final Meter          failure        = metricRegistry.meter(name(getClass(), "sent", "failure"));
  private final Meter          unregistered   = metricRegistry.meter(name(getClass(), "sent", "unregistered"));
  private final Meter          canonical      = metricRegistry.meter(name(getClass(), "sent", "canonical"));

  private final Map<String, Meter> outboundMeters = new HashMap<String, Meter>() {{
    put("receipt", metricRegistry.meter(name(getClass(), "outbound", "receipt")));
    put("notification", metricRegistry.meter(name(getClass(), "outbound", "notification")));
  }};


  private final AccountsManager   accountsManager;

  public FCMSender(AccountsManager accountsManager, FirebaseConfiguration fbConfig) throws IOException {
    if (fbConfig == null || !fbConfig.hasConfig()) {
      logger.warn("Google Firebase Messaging Unconfigured - Android and Web wakeup will not work");
      this.accountsManager = null;
      return;
    }
    this.accountsManager = accountsManager;
    FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(fbConfig.getStream()))
        //.setDatabaseUrl("https://forsta-notifications.firebaseio.com")
        .build());
  }

  public void sendMessage(FcmMessage message) {
    if (this.accountsManager == null) {
        return;
    }
    String key = message.isReceipt() ? "receipt" : "notification";
    Message fbMsg = Message.builder().setToken(message.getFcmId())
                                     .putData(key, "")
                                     .build();
    try {
      FirebaseMessaging.getInstance().send(fbMsg);
    } catch(FirebaseMessagingException e) {
      String errorCode = e.getErrorCode();
      logger.error("TODO: Handle FCM error code parsing!!! " + errorCode);
      // XXX Speculative error message tests here..  need to actually inspect contents.
      if (errorCode.equals("UNREGISTERED") || errorCode.equals("SENDER_ID_MISMATCH")) {
        handleBadRegistration(message);
      } else {
        handleGenericError(message, errorCode);
      }
      return;
    }
    // Response is a message ID string.
    logger.info("Sent FCM notification to: " + message.getNumber() +
                "." + message.getDeviceId());

    success.mark();
    markOutboundMeter(key);
  }

  @Override
  public void start() {
    if (this.accountsManager == null) {
        return;
    }
  }

  @Override
  public void stop() throws IOException {
    if (this.accountsManager == null) {
        return;
    }
  }

  private void handleBadRegistration(FcmMessage message) {
    logger.warn("Got Firebase unregistered notice: " + message.getNumber() + "." + message.getDeviceId());

    Optional<Account> account = getAccountForEvent(message);

    if (account.isPresent()) {
      Device device = account.get().getDevice(message.getDeviceId()).get();
      device.setFcmId(null);

      accountsManager.update(account.get());
    }

    unregistered.mark();
  }

  /*private void handleCanonicalRegistrationId(FcmMessage message) {
    FcmMessage message = (FcmMessage)result.getContext();
    logger.warn(String.format("Actually received 'CanonicalRegistrationId' ::: (canonical=%s), (original=%s)",
                              result.getCanonicalRegistrationId(), message.getFcmId()));

    Optional<Account> account = getAccountForEvent(message);

    if (account.isPresent()) {
      Device device = account.get().getDevice(message.getDeviceId()).get();
      device.setFcmId(result.getCanonicalRegistrationId());

      accountsManager.update(account.get());
    }

    canonical.mark();
  }*/

  private void handleGenericError(FcmMessage message, String errorCode) {
    logger.error(String.format("Unrecoverable Error ::: (error=%s), (gcm_id=%s), " +
                               "(destination=%s), (device_id=%d)",
                               errorCode, message.getFcmId(), message.getNumber(),
                               message.getDeviceId()));
    failure.mark();
  }

  private Optional<Account> getAccountForEvent(FcmMessage message) {
    Optional<Account> account = accountsManager.get(message.getNumber());

    if (account.isPresent()) {
      Optional<Device> device = account.get().getDevice(message.getDeviceId());

      if (device.isPresent()) {
        if (message.getFcmId().equals(device.get().getFcmId())) {
          logger.info("GCM Unregister GCM ID matches!");

          if (device.get().getPushTimestamp() == 0 ||
              System.currentTimeMillis() > (device.get().getPushTimestamp() +
                                            TimeUnit.SECONDS.toMillis(10))) {
            logger.info("GCM Unregister Timestamp matches!");
            return account;
          }
        }
      }
    }

    return Optional.absent();
  }

  private void markOutboundMeter(String key) {
    Meter meter = outboundMeters.get(key);

    if (meter != null) {
      meter.mark();
    } else {
      logger.warn("Unknown outbound key: " + key);
    }
  }
}
