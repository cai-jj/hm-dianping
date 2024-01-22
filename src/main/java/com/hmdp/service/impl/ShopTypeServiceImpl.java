package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopTypeMapper shopTypeMapper;
    @Override
    public Result queryTypeList() {
        String key =  CACHE_SHOPTYPE_KEY;
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(shopTypeListJson)) {
            LambdaQueryWrapper<ShopType> wrapper = new LambdaQueryWrapper<ShopType>();
            List<ShopType> shopTypeList = shopTypeMapper.selectList(null);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList));
            return Result.ok(shopTypeList);
        }
       return null;
    }
}
