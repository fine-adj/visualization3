package com.example.demo.controller;

import com.example.demo.bo.*;
import com.example.demo.config.RedisConfg;
import com.example.demo.utils.RedisSaveUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@Slf4j
public class DrawDataController {

    @Autowired
    private RedisSaveUtil redisSaveUtil;

    @Autowired
    private RedisConfg redisConfg;

    @Autowired
    private DataDetailController dataDetailController;

    @GetMapping("/readOne")
    public List<Object> OneLinkOneDayData(@RequestParam("srcName") String srcName,@RequestParam("desName") String desName,
                                    @RequestParam("time") String time){
        //前端输入源节点，目的节点，日期。然后从redis中查
        String linkKey = srcName + "-" + desName;
        List<Object> list = redisSaveUtil.getList(linkKey,0);

        return list;
    }

    /**
     * 处理每个时间片上，所有链路的qos指标数据。从Redis中读,
     * 构造好当天的时间片列表
     * @param timePic 传入一个时间片
     */

    public List<LinkAndQosBO> detailOneTimePicData(String timePic){
        //一个OneTimePicBO对象包含所有链路的qos数据
        OneTimePicBO oneTimePicBO = new OneTimePicBO();
        List<LinkAndQosBO> linkAndQosBOList = new ArrayList<>();
        //从redis中指定数据库（0号库）中读取所有key就是所有链路
        Jedis jedisBean = redisConfg.getJedisBean();
        jedisBean.select(RedisDBNumAndDataDateMapping.NUM_0_20211203); //选择0号数据库
        Set<String> keys = jedisBean.keys("*");
        System.out.println("--------keys:"+keys.size());
        //for循环,每读一条链路就存入
        if (keys != null){
            for(String link : keys){
                List<OneLinkMoreDayBO> oneLinkMoreDayBOList = redisSaveUtil.getList(link,RedisDBNumAndDataDateMapping.NUM_0_20211203);
                if (oneLinkMoreDayBOList != null){
                    //找到匹配的时间片
                    List<TimestampBO> timestampBOList = oneLinkMoreDayBOList.get(0).getLinkBOList().get(0).getTimestampBOList();
                    if (timestampBOList != null){
                        QosBO qosBO = null;
                        for (TimestampBO timestampBO : timestampBOList){
                            if (timePic.equals(timestampBO.getTimestamp())){
                                //拿到当前时间片对应的qosBO
                                qosBO = timestampBO.getQosBO();
                                //找到一个匹配的时间片后，后面的时间片不再操作
                                break;
                            }
                        }
                        //linkAndQosBO存，一个时间片的，一条链路和一个qos对象
                        LinkAndQosBO linkAndQosBO = new LinkAndQosBO();
                        linkAndQosBO.setQosBO(qosBO);
                        linkAndQosBO.setLink(link);
                        //linkAndQosBOList存，一个时间片的，所有链路及其对应的qosBO
                        linkAndQosBOList.add(linkAndQosBO);
                    }else {
                        log.warn("timestampBOList is null!");
                    }

                }
            }
//            oneTimePicBO.setLinkAndQosBOList(linkAndQosBOList);
        }else {
            log.warn("从Redis中查到的所有key-链路集合为空！");
        }
        return linkAndQosBOList;
    }
    @RequestMapping("/detail-onetime-pic-data")
    public String saveOneTimePicData(){
        try{

            //获得一个时间片集合
            String[] timePicArr = dataDetailController.createTheDayStandardTimestampList("2021-12-03 11:11:11");
            if (timePicArr != null){
                for (int i=0;i<timePicArr.length;i++){
//                    List<OneTimePicBO> oneTimePicBOList = new ArrayList<>();
                    List<LinkAndQosBO> linkAndQosBOList = detailOneTimePicData(timePicArr[i]);
//                    oneTimePicBOList.add(oneTimePicBO);
                    redisSaveUtil.setList(timePicArr[i],linkAndQosBOList,RedisDBNumAndDataDateMapping.NUM_7_20211203);
                    log.info("success to saveOneTimePicData");
                }
            }
        }catch (ParseException e){
            e.printStackTrace();
        }

        return "success";
    }

    @RequestMapping("test888")
    public List<Object> saveOneTimePicDat2(){
        List<Object> list = redisSaveUtil.getList("1638537000-1638537600", RedisDBNumAndDataDateMapping.NUM_7_20211203);
        System.out.println(list.size());
        return list;

    }

}
