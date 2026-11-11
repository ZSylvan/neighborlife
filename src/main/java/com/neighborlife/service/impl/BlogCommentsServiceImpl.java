package com.neighborlife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neighborlife.entity.BlogComments;
import com.neighborlife.mapper.BlogCommentsMapper;
import com.neighborlife.service.IBlogCommentsService;
import org.springframework.stereotype.Service;







@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
    implements IBlogCommentsService {}
