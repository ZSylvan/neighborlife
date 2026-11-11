package com.neighborlife.mapper;

import org.apache.ibatis.annotations.Param;

 
public interface OperationAuditMapper {

  int insertAudit(
      @Param("userId") Long userId,
      @Param("method") String method,
      @Param("path") String path,
      @Param("httpStatus") Integer httpStatus);
}
