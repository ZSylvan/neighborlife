package com.neighborlife.cache;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.neighborlife.entity.Shop;
import com.neighborlife.entity.Voucher;
import com.neighborlife.service.IShopService;
import com.neighborlife.service.IVoucherService;
import com.neighborlife.utils.CacheClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheChangeProjector {
  private static final Set<String> WATCHED_TABLES =
      Collections.unmodifiableSet(
          new HashSet<>(Arrays.asList("tb_shop", "tb_voucher", "tb_seckill_voucher")));
  private static final long CDC_SHOP_TTL_MINUTES = 20;
  private static final long VOUCHER_METADATA_TTL_MINUTES = 10;

  private final CacheClient cache;
  private final ObjectProvider<IShopService> shopServiceProvider;
  private final ObjectProvider<IVoucherService> voucherServiceProvider;
  private final MeterRegistry metrics;

  @Value("${neighborlife.cdc.schema:neighborlife}")
  private String schema;

  public CacheChangeProjector(
      CacheClient cache,
      ObjectProvider<IShopService> shopServiceProvider,
      ObjectProvider<IVoucherService> voucherServiceProvider,
      MeterRegistry metrics) {
    this.cache = cache;
    this.shopServiceProvider = shopServiceProvider;
    this.voucherServiceProvider = voucherServiceProvider;
    this.metrics = metrics;
  }

  public boolean isRelevantEntry(CanalEntry.Entry entry) {
    return entry.getEntryType() == CanalEntry.EntryType.ROWDATA
        && schema.equals(entry.getHeader().getSchemaName())
        && WATCHED_TABLES.contains(entry.getHeader().getTableName());
  }

  




  public void projectEntryChanges(CanalEntry.Entry entry) throws InvalidProtocolBufferException {
    log.debug(
        "CDC entry: {} {}.{} type={}",
        entry.getEntryType(),
        entry.getHeader().getSchemaName(),
        entry.getHeader().getTableName(),
        entry.getHeader().getEventType());
    if (!isRelevantEntry(entry)) {
      return;
    }
    String table = entry.getHeader().getTableName();
    CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
    if (change.getIsDdl()) {
      invalidateTableMetadata(table);
      metrics.counter("neighborlife.cdc.ddl", "table", table).increment();
      return;
    }
    log.debug("CDC row count={} event={}", change.getRowDatasCount(), change.getEventType());
    for (CanalEntry.RowData row : change.getRowDatasList()) {
      projectRow(table, change.getEventType(), row);
      metrics.counter("neighborlife.cdc.rows", "table", table).increment();
    }
  }

  private void invalidateTableMetadata(String table) {
    if ("tb_shop".equals(table)) {
      cache.invalidatePrefix("cache:shop:");
    }
    if ("tb_voucher".equals(table)) {
      cache.invalidatePrefix("cache:voucher-meta:");
      cache.invalidatePrefix("cache:voucher-list:");
    }
  }

  private void projectRow(String table, CanalEntry.EventType eventType, CanalEntry.RowData row) {
    Map<String, String> before = mapColumnValues(row.getBeforeColumnsList());
    Map<String, String> after = mapColumnValues(row.getAfterColumnsList());
    Map<String, String> identity = after.isEmpty() ? before : after;
    if ("tb_shop".equals(table)) {
      projectShopChange(parseRowId(identity, "id"), eventType);
    } else if ("tb_voucher".equals(table)) {
      projectVoucherChange(parseRowId(identity, "id"), eventType, before, after);
    }
     
  }

  private void projectShopChange(long shopId, CanalEntry.EventType eventType) {
    cache.invalidate("cache:shop:" + shopId);
    if (eventType != CanalEntry.EventType.DELETE) {
      cache.queryWithMutex(
          "cache:shop:",
          shopId,
          Shop.class,
          id -> shopServiceProvider.getObject().getById(id),
          CDC_SHOP_TTL_MINUTES,
          TimeUnit.MINUTES);
    }
  }

  private void projectVoucherChange(
      long voucherId,
      CanalEntry.EventType eventType,
      Map<String, String> before,
      Map<String, String> after) {
    cache.invalidate("cache:voucher-meta:" + voucherId);
     
    Set<String> shopIds = new HashSet<>();
    shopIds.add(before.get("shop_id"));
    shopIds.add(after.get("shop_id"));
    shopIds.remove(null);
    for (String shopId : shopIds) {
      cache.invalidate("cache:voucher-list:" + Long.parseLong(shopId));
    }
    if (eventType != CanalEntry.EventType.DELETE) {
      cache.queryWithMutex(
          "cache:voucher-meta:",
          voucherId,
          Voucher.class,
          id -> voucherServiceProvider.getObject().getById(id),
          VOUCHER_METADATA_TTL_MINUTES,
          TimeUnit.MINUTES);
    }
  }

  private static Map<String, String> mapColumnValues(List<CanalEntry.Column> columns) {
    Map<String, String> result = new HashMap<>();
    for (CanalEntry.Column column : columns) {
      if (!column.getIsNull()) {
        result.put(column.getName(), column.getValue());
      }
    }
    return result;
  }

  private static long parseRowId(Map<String, String> row, String key) {
    String value = row.get(key);
    if (value == null) {
      throw new IllegalStateException("binlog 缺少完整主键: " + key);
    }
    return Long.parseLong(value);
  }
}
