package com.neighborlife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neighborlife.dto.Result;
import com.neighborlife.dto.ScrollResult;
import com.neighborlife.dto.UserDTO;
import com.neighborlife.entity.Blog;
import com.neighborlife.entity.User;
import com.neighborlife.mapper.BlogMapper;
import com.neighborlife.mapper.ShopMapper;
import com.neighborlife.service.IBlogService;
import com.neighborlife.service.IBusinessOutboxService;
import com.neighborlife.service.IFollowService;
import com.neighborlife.service.IMediaService;
import com.neighborlife.service.IUserService;
import com.neighborlife.utils.RedisConstants;
import com.neighborlife.utils.SystemConstants;
import com.neighborlife.utils.UserHolder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;







@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

  @Resource

  private ShopMapper shopMapper;

  @Resource

  private IUserService userService;

  @Resource

  private StringRedisTemplate stringRedisTemplate;

  @Resource

  private IFollowService followService;

  @Resource

  private TransactionTemplate transactions;

  @Resource

  private IBusinessOutboxService outbox;

  @Resource

  private IMediaService media;

  @Override
  public Result queryHotBlog(Integer current) {
     
    Page<Blog> page =
        query()
            .orderByDesc("liked")
            .orderByDesc("id")
            .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
     
    List<Blog> records = page.getRecords();
    enrich(records);
    return Result.ok(records);
  }

  @Override
  public Result queryBlogById(Long id) {
     
    Blog blog = getById(id);
    if (blog == null) {
      return Result.fail("笔记不存在！");
    }
     
    queryBlogUser(blog);
     
    isBlogLiked(blog);
    return Result.ok(blog);
  }

  private void isBlogLiked(Blog blog) {
     
    UserDTO user = UserHolder.getUser();
    if (user == null) {
       
      return;
    }
    blog.setIsLike(baseMapper.countLike(blog.getId(), user.getId()) > 0);
  }

  @Override
  public Result likeBlog(Long id) {
    return updateLike(id, null);
  }

  @Override
  public Result setBlogLike(Long id, boolean liked) {
    return updateLike(id, liked);
  }

  private Result updateLike(Long id, Boolean requested) {
    long user = UserHolder.getUser().getId();
    return transactions.execute(
        tx -> {
          if (baseMapper.selectIdForUpdate(id).isEmpty()) {
            return Result.fail("笔记不存在");
          }
          boolean current = baseMapper.countLike(id, user) > 0;
          boolean desired = requested == null ? !current : requested;
          if (current == desired) {
            return Result.ok();
          }
          if (desired) {
            baseMapper.insertLike(id, user);
            baseMapper.incrementLikes(id);
          } else {
            baseMapper.deleteLike(id, user);
            baseMapper.decrementLikes(id);
          }
          outbox.appendBusinessEvent("LIKE", id, user);
          return Result.ok();
        });
  }

  @Override
  public Result queryBlogLikes(Long id) {
    List<Long> ids = baseMapper.selectFirstLikeUserIds(id);
    if (ids.isEmpty()) {
      return Result.ok(Collections.emptyList());
    }
    String idStr = StrUtil.join(",", ids);
     
    List<UserDTO> userDTOS =
        userService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list().stream()
            .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
            .collect(Collectors.toList());
     
    return Result.ok(userDTOS);
  }

  @Override
  @Transactional
  public Result saveBlog(Blog blog) {
     
    UserDTO user = UserHolder.getUser();
    blog.setUserId(user.getId());
    if (blog.getImages() == null) {
      blog.setImages("");
    }
    if (blog.getImages().length() > 2048
        || (!blog.getImages().isEmpty() && blog.getImages().split(",").length > 9)) {
      return Result.fail("图片数量或路径长度无效");
    }
    if (!media.lockOwnedImages(blog.getImages(), user.getId())) {
      return Result.fail("图片不存在、不属于当前账号或正在删除");
    }
    blog.setId(null);
    blog.setLiked(0);
    blog.setComments(0);
    blog.setCreateTime(null);
    blog.setUpdateTime(null);
    if (blog.getTitle() == null
        || blog.getTitle().trim().isEmpty()
        || blog.getTitle().length() > 255
        || blog.getContent() == null
        || blog.getContent().length() > 10000
        || blog.getShopId() == null
        || shopMapper.countByShopId(blog.getShopId()) == 0) {
      return Result.fail("笔记内容或关联店铺无效");
    }
     
    boolean isSuccess = save(blog);
    if (!isSuccess) {
      return Result.fail("新增笔记失败!");
    }
    media.attach(blog.getId(), blog.getImages());
    outbox.appendBusinessEvent("FEED", blog.getId(), user.getId());
    return Result.ok(blog.getId());
  }

  @Override
  public Result queryBlogOfFollow(Long max, Integer offset) {
     
    Long userId = UserHolder.getUser().getId();
     
    String key = RedisConstants.FEED_KEY + userId;
    Set<ZSetOperations.TypedTuple<String>> typedTuples =
        stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
     
    if (typedTuples == null || typedTuples.isEmpty()) {
      return Result.ok();
    }
     
    List<Long> ids = new ArrayList<>(typedTuples.size());
    long minTime = 0;  
    int os = 1;  
    for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {  
       
      ids.add(Long.valueOf(tuple.getValue()));
       
      long time = tuple.getScore().longValue();
      if (time == minTime) {
        os++;
      } else {
        minTime = time;
        os = 1;
      }
    }

     
    String idStr = StrUtil.join(",", ids);
    List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

    enrich(blogs);
     
    ScrollResult r = new ScrollResult();
    r.setList(blogs);
    r.setOffset(minTime == max ? offset + os : os);
    r.setMinTime(minTime);

    return Result.ok(r);
  }

  private void enrich(List<Blog> blogs) {
    if (blogs.isEmpty()) {
      return;
    }
    List<Long> authorIds =
        blogs.stream().map(Blog::getUserId).distinct().collect(Collectors.toList());
    Map<Long, User> authors =
        userService.listByIds(authorIds).stream().collect(Collectors.toMap(User::getId, u -> u));
    Set<Long> likedIds = new HashSet<>();
    if (UserHolder.getUser() != null) {
      likedIds.addAll(
          baseMapper.selectLikedBlogIds(
              UserHolder.getUser().getId(),
              blogs.stream().map(Blog::getId).collect(Collectors.toList())));
    }
    for (Blog blog : blogs) {
      User author = authors.get(blog.getUserId());
      if (author != null) {
        blog.setName(author.getNickName());
        blog.setIcon(author.getIcon());
      }
      blog.setIsLike(likedIds.contains(blog.getId()));
    }
  }

  private void queryBlogUser(Blog blog) {
    User user = userService.getById(blog.getUserId());
    if (user != null) {
      blog.setName(user.getNickName());
      blog.setIcon(user.getIcon());
    }
  }

  @Override
  public Result queryMyBlog(Integer current) {
     
    UserDTO user = UserHolder.getUser();
     
    Page<Blog> page =
        query()
            .eq("user_id", user.getId())
            .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
     
    List<Blog> records = page.getRecords();
    return Result.ok(records);
  }

  @Override
  public Result queryBlogByShop(Long shopId, Integer current) {
    if (shopId == null || shopId <= 0 || current < 1 || current > 100000) {
      return Result.fail("shopId 和 current 必须是正整数");
    }
    Page<Blog> page =
        query().eq("shop_id", shopId).orderByDesc("create_time").page(new Page<>(current, 5));
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @Override
  public Result searchBlogEvidence(Long shopId, String keyword, Integer current) {
    if (shopId == null
        || shopId <= 0
        || current == null
        || current < 1
        || current > 100000
        || keyword == null
        || keyword.trim().length() < 2
        || keyword.trim().length() > 40) {
      return Result.fail("shopId、keyword 或 current 不合法");
    }
    String query = keyword.trim();
    Page<Blog> page =
        page(
            new Page<>(current, 5),
            new QueryWrapper<Blog>()
                .eq("shop_id", shopId)
                .and(w -> w.like("title", query).or().like("content", query))
                .orderByDesc("create_time")
                .orderByDesc("id"));
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @Override
  public Result queryBlogByUserId(Integer current, Long id) {
     
    Page<Blog> page =
        query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
     
    List<Blog> records = page.getRecords();
    return Result.ok(records);
  }
}
