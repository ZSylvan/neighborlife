package com.neighborlife.service;

public interface IOperationAuditService {
  void recordAudit(Long userId, String method, String path, Integer httpStatus);
}
