package com.neighborlife.service;

import com.neighborlife.entity.BusinessOutboxTask;
import com.neighborlife.messaging.ProjectionResult;

 
public interface IBusinessProjectionService {
   
  ProjectionResult project(BusinessOutboxTask task);
}
