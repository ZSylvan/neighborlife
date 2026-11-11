package com.neighborlife.service;

import com.neighborlife.dto.Result;
import com.neighborlife.entity.VoucherOrder;

 
public interface ISeckillService {
  String STREAM = "stream.orders";
  String GROUP = "g1";

  void fulfillReservationFromMessage(long orderId);

   
  boolean lockReservationForFulfillment(VoucherOrder order);

  void markReservationSuccessful(long orderId);

  String queryReservationState(long orderId);

  Result queryReservationStatus(long orderId, long userId);

  Result publishActivity(long voucherId);

   
  Result recoverActivity(long voucherId);

  Result retryReservation(long orderId);

  Result markReservationFailedAndCompensateRedis(long orderId);

   
  Result queryInventoryReconciliationReport(long voucherId);

  Result queryReservationsForReview();
}
