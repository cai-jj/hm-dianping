package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        //根据点赞数量进行排行查询
        //select * from tb_blog order by liked desc limit 0, 10
        Page<Blog> page = query().orderByDesc("liked").page(
                new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //获取当前页记录
        List<Blog> records = page.getRecords();
        records.forEach(blog->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }


    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if(blog == null) {
            return Result.fail("该博客不存在");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);
        //3. 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null) return;
        Long loginId = UserHolder.getUser().getId();
        //查看是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, loginId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userMapper.selectById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

   /* @Override
    public Result likeBlog(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null) return Result.fail("用户未登录不能点赞");
        Long loginId = UserHolder.getUser().getId();
        //查看是否点赞
        String key = BLOG_LIKED_KEY + id;

        Boolean isLike = stringRedisTemplate.opsForSet().isMember(key, loginId.toString());
        if(isLike) {
            //已经点赞，取消点赞
            //update tb_blog set liked = liked - 1 where id = ?
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id",id).update();
            if(isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, loginId.toString());
            }
        } else {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id",id).update();
            log.debug("点赞");
            if(isSuccess) {
                stringRedisTemplate.opsForSet().add(key, loginId.toString());
            }
        }
        return Result.ok();
    }*/

    //改进：使用zset实现按点赞排行，按时间排序
    @Override
    public Result likeBlog(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null) return Result.fail("用户未登录不能点赞");
        Long loginId = UserHolder.getUser().getId();
        //查看是否点赞
        String key = BLOG_LIKED_KEY + id;

        Double score = stringRedisTemplate.opsForZSet().score(key, loginId.toString());
        if(score != null) {
            //已经点赞，取消点赞
            //update tb_blog set liked = liked - 1 where id = ?
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id",id).update();
            if(isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, loginId.toString());
            }
        } else {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id",id).update();
            if(isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, loginId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess) {
            return Result.fail("新增笔记失败!");
        }
        //推送到粉丝收件箱
        //select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", blog.getUserId()).list();
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            String key = FEED_KEY  + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        log.debug("分页查询");
        if (typedTuples == null || typedTuples.isEmpty()) {
            log.debug("分页查询为空");
            return Result.ok();
        }
        // 4.解析数据：blogId、minTime（时间戳）、offset
        long minTime = 0;
        int minCount = 1;
        List<Long> ids = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 4.1.获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            // 4.2.获取分数(时间戳）
            long time = typedTuple.getScore().longValue();
            if(time == minTime) {
                minCount++;
            } else {
                minTime = minTime;
                minCount = 1;
            }
        }

        // 5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1.查询blog有关的用户
            queryBlogUser(blog);
            // 5.2.查询blog是否被点赞
            isBlogLiked(blog);
        }
        ScrollResult sr = new ScrollResult();
        sr.setList(blogs);
        sr.setOffset(minCount);
        sr.setMinTime(minTime);
        return Result.ok(sr);
    }


}
