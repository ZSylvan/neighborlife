package com.neighborlife.enums;

 
public enum OutboxState {
  PENDING,
  SENDING,
  SENT,
  DONE,
  REVIEW;

  public boolean matches(String value) {
    return name().equals(value);
  }
}
