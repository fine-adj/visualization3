package com.example.demo.controller;

import com.example.demo.bo.LinkBO;
import com.example.demo.bo.OneLinkMoreDayBO;
import com.example.demo.bo.TimestampBO;
import com.example.demo.config.RedisConfg;
import com.example.demo.dto.QosCoreFieldDO;
import com.example.demo.dto.WithTableNameAndCriteriaParam;
import com.example.demo.service.QosService;
import com.example.demo.utils.RedisSaveUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从redis中查每一条链路，读取其中的rtt_jitter_avg指标，如果为空，就把这条链路单独存起来，
 * 重新去数据库中读，再存进去。
 */
@RestController
@Slf4j
public class FailDataRetryController {
    @Autowired
    private RedisSaveUtil redisSaveUtil;

    @Autowired
    private RedisConfg redisConfg;

    @Autowired
    private QosService qosService;

    @Autowired
    private DataDetailController dataDetailController;

    private final String tableName = "rtt_jitter_avg_20211208";

    /**
     * 拿到redis3号库中（即20211206）jitter没存成功的链路集合
     *
     * @return
     */
    @RequestMapping("/failLinkJitterReSave")
    public Set<String> getJitterFailLink() {
        Set<String> jitterIsNullLinkList = getJitterIsNullLinkList();
        if (jitterIsNullLinkList != null) {
            for (String link : jitterIsNullLinkList) {
                List<OneLinkMoreDayBO> oneLinkMoreDayBOList = redisSaveUtil.getList(link,0);
                LinkBO linkBO = oneLinkMoreDayBOList.get(0).getLinkBOList().get(0);
                if (linkBO != null) {
                    List<TimestampBO> timestampBOList = linkBO.getTimestampBOList();
                    if (timestampBOList != null) {
                        for (int i = 0; i < timestampBOList.size(); i++) {
                            String rtt_jitter_avg = timestampBOList.get(0).getQosBO().getRtt_jitter_avg();
                            if (rtt_jitter_avg == null) {
//                                this.linkKey = link;
                                List<QosCoreFieldDO> qosCoreFieldDOList = failLinkReRead(link);
                                Object o = insertJitterToRedis(qosCoreFieldDOList,link);
                                if (o != null) {
                                    log.info("success to save "+link+" jitter");
                                    //这个for只是为了判断jitter是否为空，必须写break，否则一条链路存144次！！！
                                    break;
                                }
                            }
                        }
                    } else {
                        log.warn("timestampBOList is null!");
                    }
                } else {
                    log.warn("linkBO is null!");
                }

            }
        } else {
            log.warn("read from redis is null!");
        }
        log.info("finished to save jitter to redis!!!!!!");
        return jitterIsNullLinkList;
    }

    /**
     * 遍历没存成功的链路集合重新从数据库读取
     */
    private List<QosCoreFieldDO> failLinkReRead(String link) {
        //从mysql中读的数据
        WithTableNameAndCriteriaParam param = new WithTableNameAndCriteriaParam();
        param.setTableName(this.tableName);
        String sourceName = StringUtils.substringBefore(link, "-");
        param.setSourceName(sourceName);
        String desNameTemp = StringUtils.substringAfter(link, "-");
        param.setDesName("proc.icmp." + desNameTemp.replace("_", ".") + ".rtt.jitter.avg");
        List<QosCoreFieldDO> qosCoreFieldDOList = qosService.selectBySrcAndDesName(param);
        //这句日志可以用来观察读数据库这一步耗时情况
        log.info("success to read fail link from mysql");
        return qosCoreFieldDOList;
    }

    public List<Object> readOneLinkFromRedis2(String link) {
        System.out.println("readOneLinkFromRedis-linkKey:" + link);
        return redisSaveUtil.getList(link,0);
    }

    /**
     * 拿到从数据库读取的链路集合，存入Redis
     */

    private Object insertJitterToRedis(List<QosCoreFieldDO> qosCoreFieldDOList,String link) {
//         = failLinkReRead();
        if (qosCoreFieldDOList == null) {
            log.warn("insertJitterToRedis-qosCoreFieldDOList:" + qosCoreFieldDOList);
            return null;
        }
        //从Redis中获取的数据
        List<Object> hashMaps = readOneLinkFromRedis2(link);
        if (hashMaps == null) {
            log.warn("readOneLinkFromRedis result is null!");
            return hashMaps;
        }
        //从数据库查到的数据
        for (QosCoreFieldDO qosCoreFieldDO : qosCoreFieldDOList) {
            //从数据库读出来的原始时间戳
            String timestamp = qosCoreFieldDO.getTimestamp();
            //时间戳的处理
            String standardTimestamp = dataDetailController.findStandardTimestamp(timestamp);
            String value = qosCoreFieldDO.getValue();
            for (int i = 0; i < hashMaps.size(); i++) {
                //key是时间戳，value是qos列表
                OneLinkMoreDayBO oneLinkMoreDayBO = (OneLinkMoreDayBO) hashMaps.get(i);
                //由于每调用一次该方法传入一条链路的数据，所以linkBOList的长度恒为1
                List<LinkBO> linkBOList = oneLinkMoreDayBO.getLinkBOList();
                List<TimestampBO> timestampBOList = linkBOList.get(0).getTimestampBOList();
                if (timestampBOList != null) {
                    for (TimestampBO timestampBO : timestampBOList) {
                        if (timestampBO.getTimestamp().equals(standardTimestamp)) {
                            timestampBO.getQosBO().setRtt_jitter_avg(value);
                            //千万别忘了这个break!
                            break;
                        }
                    }
                } else {
                    log.warn("timestampBOList is null!" + link);
                }
            }
        }
        redisSaveUtil.setList(link, hashMaps,0);
        return hashMaps;
    }

    @RequestMapping("/test777")
    public Set<String> getJitterIsNullLinkList(){
        Set<String> list = new HashSet<>();
        Jedis jedisBean = redisConfg.getJedisBean();
        jedisBean.select(3);
        Set<String> keys = jedisBean.keys("*");
        if (keys != null) {
            for (String link : keys) {
                List<OneLinkMoreDayBO> oneLinkMoreDayBOList = redisSaveUtil.getList(link,0);
                LinkBO linkBO = oneLinkMoreDayBOList.get(0).getLinkBOList().get(0);
                if (linkBO != null) {
                    List<TimestampBO> timestampBOList = linkBO.getTimestampBOList();
                    if (timestampBOList != null) {
                        for (int i = 0; i < timestampBOList.size(); i++) {
                            String rtt_jitter_avg = timestampBOList.get(0).getQosBO().getRtt_jitter_avg();
                            if (rtt_jitter_avg == null) {
                                list.add(link);
                            }
                        }
                    }
                }
            }
        }
        System.out.println(list.size());
        return list;
    }

}
