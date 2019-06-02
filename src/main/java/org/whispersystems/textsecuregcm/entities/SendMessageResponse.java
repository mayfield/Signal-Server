package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SendMessageResponse {

  @JsonProperty
  private boolean needsSync;

  @JsonProperty
  private long received;

  public SendMessageResponse() {}

  public SendMessageResponse(boolean needsSync) {
    this.needsSync = needsSync;
    this.received = System.currentTimeMillis();
  }
}
