package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

@Configuration
public class RedisConfg {
    private static Jedis jedis = null;
    //返回一个Jedis实例
    @Bean
    public Jedis getJedisBean(){
        if (jedis == null){
            synchronized (RedisConfg.class){
                if (jedis == null){
                    jedis = new Jedis("10.112.219.154",6399);
                }
            }
        }
        return jedis;
    }
}
