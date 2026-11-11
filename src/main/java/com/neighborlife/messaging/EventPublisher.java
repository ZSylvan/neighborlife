package com.neighborlife.messaging;

public interface EventPublisher {
  void publish(BusinessEvent event);
}
