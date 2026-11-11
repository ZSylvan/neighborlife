package com.neighborlife.service;

import com.neighborlife.dto.Result;
import org.springframework.web.multipart.MultipartFile;

 
public interface IMediaService {
  boolean lockOwnedImages(String images, long user);

  void attach(long blog, String images);

  Result delete(String input, long user);

  Result upload(MultipartFile image, long user);
}
