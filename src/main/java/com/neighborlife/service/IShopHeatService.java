package com.neighborlife.service;

import com.neighborlife.dto.Result;

public interface IShopHeatService {
  void refresh();

  Result ranking(int windowMinutes, int limit);
}
