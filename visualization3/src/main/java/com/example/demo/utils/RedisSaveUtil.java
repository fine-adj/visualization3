package com.example.demo.utils;

import com.example.demo.bo.RedisDBNumAndDataDateMapping;
import com.example.demo.config.RedisConfg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class RedisSaveUtil {

    @Autowired
    private RedisConfg redisConfg;

    /**
     * 设置 list
     * @param <T>
     * @param key
     */
    public <T> void setList(String key , List<T> list,Integer redisDBNum){
        try {
            Jedis jedisBean = redisConfg.getJedisBean();
            if (jedisBean != null){
                //将20211204的数据存入1号数据库
                jedisBean.select(redisDBNum);
                jedisBean.set(key.getBytes(),RedisSerializeUtil.serialize(list));
            }else {
                log.warn("redis bean is null!");
            }
        } catch (Exception e) {
            log.error("Set key error : "+e);
        }
    }

    /**
     * 获取list
     * @param <T>
     * @param key
     * @return list
     */
    public <T> List<T> getList(String key,Integer redisDBNum){
        Jedis jedisBean = redisConfg.getJedisBean();
        if (jedisBean == null){
            return null;
        }
        List<T> list = null;
        jedisBean.select(redisDBNum);
        if ((jedisBean.exists(key.getBytes()))){
            byte[] in = jedisBean.get(key.getBytes());
            list = (List<T>) RedisSerializeUtil.deserialize(in);
        }
        return list;
    }
}