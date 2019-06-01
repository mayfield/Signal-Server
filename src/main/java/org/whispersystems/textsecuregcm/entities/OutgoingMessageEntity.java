package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OutgoingMessageEntity {

  @JsonIgnore
  private long id;

  @JsonProperty
  private int type;

  @JsonProperty
  private String relay;

  @JsonProperty
  private long timestamp;

  @JsonProperty
  private String source;

  @JsonProperty
  private int sourceDevice;

  @JsonProperty
  private byte[] message;

  @JsonProperty
  private byte[] content;

  @JsonProperty
  private long age;

  @JsonProperty
  private long received;

  public OutgoingMessageEntity() {}

  public OutgoingMessageEntity(long id, int type, String relay, long timestamp,
                               String source, int sourceDevice, byte[] message,
                               byte[] content, long age, long received)
  {
    this.id           = id;
    this.type         = type;
    this.relay        = relay;
    this.timestamp    = timestamp;
    this.source       = source;
    this.sourceDevice = sourceDevice;
    this.message      = message;
    this.content      = content;
    this.age          = age;
    this.received     = received;
  }

  public int getType() {
    return type;
  }

  public String getRelay() {
    return relay;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public String getSource() {
    return source;
  }

  public int getSourceDevice() {
    return sourceDevice;
  }

  public byte[] getMessage() {
    return message;
  }

  public byte[] getContent() {
    return content;
  }

  public long getAge() {
    return age;
  }

  public long getReceived() {
    return received;
  }

  public long getId() {
    return id;
  }

}
