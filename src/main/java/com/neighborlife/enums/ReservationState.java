package com.neighborlife.enums;

 
public enum ReservationState {
  PROCESSING,
  SUCCESS,
  REVIEW,
  FAILED;

  public boolean matches(String value) {
    return name().equals(value);
  }
}
