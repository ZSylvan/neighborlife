package com.neighborlife.service;

 
public interface IOrderLifecycleService {
  void registerOrderCreatedEventAndDeadline(long orderId);

  void registerOrderPaidEvent(long orderId, long userId);

  void registerOrderClosedEvent(long orderId, long userId);

  boolean closeExpiredOrdinaryOrder(long orderId);

  void expireOverdueOrders();
}
