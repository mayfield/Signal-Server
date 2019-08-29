package org.whispersystems.textsecuregcm.push;

public class FcmMessage {

  private final String  fcmId;
  private final String  number;
  private final int     deviceId;
  private final boolean receipt;

  public FcmMessage(String fcmId, String number, int deviceId, boolean receipt) {
    this.fcmId        = fcmId;
    this.number       = number;
    this.deviceId     = deviceId;
    this.receipt      = receipt;
  }

  public String getFcmId() {
    return fcmId;
  }

  public String getNumber() {
    return number;
  }

  public boolean isReceipt() {
    return receipt;
  }

  public int getDeviceId() {
    return deviceId;
  }
}
