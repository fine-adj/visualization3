package com.example.demo.controller;

import com.example.demo.bo.*;
import com.example.demo.config.RedisConfg;
import com.example.demo.config.TableMapperConfig;
import com.example.demo.dto.QosCoreFieldDO;
import com.example.demo.dto.WithTableNameAndCriteriaParam;
import com.example.demo.service.QosService;
import com.example.demo.utils.RedisSaveUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
//import org.thymeleaf.util.StringUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
@Slf4j
@RestController
public class DataDetailController {

    @Autowired
    private RedisSaveUtil redisSaveUtil;

    @Autowired
    private QosService qosService;

    private final String filePath = "src/main/resources/node.txt";

    private String linkKey;

    private String qosType;

    private final Set<String> failLinkSet = new HashSet<>();

    //时间片去重：用来判断时间片是否已经存在
    private final HashMap<String,Integer> timestampsMap = new HashMap<>();

    /**
     * 预处理一天的数据：相同日期，每个指标一张表，最终结果是：
     * 所有链路，一天144个时间片的所有指标数据的集合
     */
    @RequestMapping("/saveToRedis")
    public Object oneDayAllLinksQosDataSetSaveToRedis() {
        String[] qosTypeArr = {QosTypeEnum.packet_loss.toString(), QosTypeEnum.rtt_avg.toString(),
                QosTypeEnum.rtt_jitter_avg.toString()};
        Object o = null;
        try {
            createTheDayStandardTimestampList("2021-12-08 00:00:00");
//            System.out.println("this.timestamp第一次且仅一次初始化："+this.timestampsMap);
            for (int i = 0; i < qosTypeArr.length; i++) {
                this.qosType = qosTypeArr[i];
                o = ReadLinkQosFromMySQLAndSaveToRedis(this.qosType,"20211208");
//                log.info("success to ReadLinkQosFromMySQLAndSaveToRedis" + "--" + this.qosType);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return o;
    }

    /**
     * 从文件中读取节点信息，构造FullMesh链路集合
     *
     * @return
     */
    public ArrayList<ArrayList<String>> createLinkSet(String filePath) {
        ArrayList<ArrayList<String>> lists = new ArrayList<>();
        //用别名，即name只能模糊查询
        ArrayList<String> nodeAlias = new ArrayList<>();
        ArrayList<String> nodeIp = new ArrayList<>();
        try {
//            String filePath = "src"+File.separator+"main"+File.separator+"resources"+File.separator+"node.txt";
//            String filePath = "/storage/node.txt";
            File file = new File(filePath);
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file));
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                //如果当前这一行的数据以#开头，就处理下一行
                char c = line.charAt(0);
                if (c != '#') {
                    //切割：=前的存入nodeAlias,=后存入nodeIp
                    nodeAlias.add(StringUtils.substringBefore(line, "="));
                    nodeIp.add(StringUtils.substringAfter(line, "="));
                }
            }
        } catch (IOException ie) {
            ie.printStackTrace();
        }
        lists.add(nodeAlias);//名称
        lists.add(nodeIp); //IP
        return lists;
    }

    /**
     * 每拿到一条路径，就去数据库查一条数据
     * 如果查询结果为空，就处理下一条
     * 如果查询结果不为空，就存入redis
     */

    private List<QosCoreFieldDO> ReadLinkQosFromMySQLAndSaveToRedis(String qosType,String time) {
        Object qosCoreFieldDOList1 = new ArrayList<>();
        List<QosCoreFieldDO> qosCoreFieldDOList = new ArrayList<>();
        //表名
        List<String> tableNameList = TableMapperConfig.getQosTableNameList(qosType,time);
        //暂时处理一张表，由于数据量过大，所以不能循环遍历生成的表名批量做处理。
        String tableName = tableNameList.get(0);
        System.out.println("表名：" + tableName);
        ArrayList<ArrayList<String>> linkSet = createLinkSet(this.filePath);
        if (Objects.isNull(linkSet)) {
            log.info("linkSet is null!");
            return qosCoreFieldDOList;
        }
        ArrayList<String> linkAlias = linkSet.get(0);
        try {
            //A-B与B-A是两条不同链路，包含A-B与B-A情况
            for (int i = 0; i < linkAlias.size(); i++) {
                for (int j = 0; j < linkAlias.size(); j++) {
                    String srcName = linkAlias.get(i);
                    String desName = linkAlias.get(j);
//                    String srcName = "Linode_Toronto";
//                    String desName = "TX_Mumbai2";
                    this.linkKey = srcName + "-" + desName;
                    //每拿到一对源目的节点就去数据库查，在一张表中的所有时间戳的数据
                    WithTableNameAndCriteriaParam param = new WithTableNameAndCriteriaParam();
                    param.setTableName(tableName);
                    param.setSourceName(srcName);
                    if (this.qosType.equals("packet_loss")){
                        param.setDesName("proc.icmp." + desName.replace("_",".") + ".packet.loss");
                    }else if (this.qosType.equals("rtt_avg")){
                        param.setDesName("proc.icmp."+desName.replace("_",".")+".rtt.avg");
                    }else if (this.qosType.equals("rtt_jitter_avg")){
                        param.setDesName("proc.icmp."+desName.replace("_",".")+".rtt.jitter.avg");
                    }

                    //查询结果是一条链路24小时的一个qos指标数据
                    qosCoreFieldDOList = qosService.selectBySrcAndDesName(param);
//                    System.out.println("888qosCoreFieldDOList"+qosCoreFieldDOList);
//                    writeObjToFile(qosCoreFieldDOList.toString());
                    //在双层for里面调用存入Redis表示，每一条链路作为一个redis的key
                    //向Redis中第一次存入指标的时候调用FirstCreateLinkQosDataSetAndSaveToRedis2方法
                    //什么情况是第一次存入：先从Redis中查这个key，不存在的时候就是第一个存入。
                    List<OneLinkMoreDayBO> list = redisSaveUtil.getList(this.linkKey,0);
                    if (list == null) {
                        boolean isSaveToRedisSuccess = FirstCreateLinkQosDataSetAndSaveToRedis(qosCoreFieldDOList, tableName);
                        if (isSaveToRedisSuccess) {
                            log.info("success to saveLinkQosToRedis！"+"--"+this.qosType);
                        } else {
                            log.warn("fail to saveLinkQosToRedis！"+"--"+this.qosType+"linkKey:"+this.linkKey);
                            log.warn("当前链路查询结果："+qosCoreFieldDOList);
                            failLinkSet.add(this.linkKey);
                        }
                    } else {
                        //向Redis中第二次存指标的时候调用：
                        qosCoreFieldDOList1 = insertNewQosToRedis(qosCoreFieldDOList);
                        if (qosCoreFieldDOList1 != null) {
                            log.info("success to insertNewQosToRedis！"+"--"+this.qosType);
                        } else {
                            log.warn("fail to insertNewQosToRedis！"+"--"+this.qosType+"linkKey:"+this.linkKey);
                            log.warn("当前链路查询结果："+qosCoreFieldDOList);
                            failLinkSet.add(this.linkKey);
                        }
                    }
                }
            }
            log.info("finished!");
            System.out.println("failLinkSet="+failLinkSet);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("数据量：" + qosCoreFieldDOList.size());
        return qosCoreFieldDOList;
    }

    /***
     * @param qosCoreFieldDOList1 包含的是一条链路在一张表的所有时间戳上的数据
     * @return
     * List/<Map<String,List<Map<String,String>>>>结构
     */
    @Deprecated
    private boolean createLinkQosDataSetAndSaveToRedis(List<QosCoreFieldDO> qosCoreFieldDOList1, String tableName) {
        if (qosCoreFieldDOList1 == null || qosCoreFieldDOList1.size() == 0) {
            return false;
        }
        try {
            List<Map<String, List<Map<String, String>>>> timeWithQosList = new ArrayList<>();
            //遍历qosCoreFieldDOList1中每一条数据
//            String linkKey = qosCoreFieldDOList1.get(1).getSourceName() + "-" + qosCoreFieldDOList1.get(1).getDesName();
            //在for中构造出一条链路的所有时间戳的数据
            for (QosCoreFieldDO qosCoreFieldDO : qosCoreFieldDOList1) {
                Map<String, List<Map<String, String>>> timeWithQosMap = new HashMap<>();
                List<Map<String, String>> qosList = new ArrayList<>();
                Map<String, String> linkQosMap = new HashMap<>();
                //存入一条packet_loss:0.03
                linkQosMap.put(StringUtils.substringBeforeLast(tableName, "_"),
                        qosCoreFieldDO.getValue());
                //qosList的结构是：[packet_loss:0.03]，未来的结构是：[packet_loss:0.03,rtt:100,jitter:10]
                qosList.add(linkQosMap);
                //timeWithQosMap的结构是：{时间戳：[packet_loss:0.03]}
                //时间戳要做处理，向timeWithQosMap存入时间戳之前，需要先拉齐
                String timestamp = qosCoreFieldDO.getTimestamp();
                String standardTimestamp = findStandardTimestamp(timestamp);
                timeWithQosMap.put(standardTimestamp, qosList);
                //timeWithQosList的结构是：
                // [{时间戳：[packet_loss:0.03]}, {时间戳：[packet_loss:0.14]}. {时间戳：[packet_loss:0.4]}]
                timeWithQosList.add(timeWithQosMap);
            }
            //序列化的方式存入redis
            redisSaveUtil.setList(linkKey, timeWithQosList,0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean FirstCreateLinkQosDataSetAndSaveToRedis(List<QosCoreFieldDO> qosCoreFieldDOList1, String tableName) {
        if (qosCoreFieldDOList1 == null || qosCoreFieldDOList1.size() == 0) {
            return false;
        }
        try {
            //一条链路一天的数据
            OneLinkMoreDayBO oneLinkMoreDayBO = new OneLinkMoreDayBO();
            oneLinkMoreDayBO.setLinkName(this.linkKey);
            //一条链路，所有时间戳的集合
            LinkBO linkBO = new LinkBO();
            linkBO.setDate(StringUtils.substringAfterLast(tableName, "_"));
            //根据表名获取存的是哪个qos指标
//            String qosType = StringUtils.substringBeforeLast(tableName, "_");
            List<TimestampBO> timestampBOList = new ArrayList<>();
            //先初始化this.timestampsMap
//            String timestampHead = qosCoreFieldDOList1.get(1).getTimestamp();
            //这个方法不需要每条链路都被调用，因为这个方法只用于得出一天的时间片集合，一天的数据，初始化一次就行
//            createTheDayStandardTimestampList();
            //在for中构造出一条链路的所有时间戳的数据，for执行144次
            for (QosCoreFieldDO qosCoreFieldDO : qosCoreFieldDOList1) {
                //一条链路，每一条数据对应一个时间戳
                TimestampBO timestampBO = new TimestampBO();
                String timestamp = qosCoreFieldDO.getTimestamp();
                //standardTimestamp一个时间片
                String standardTimestamp = findStandardTimestamp(timestamp);
                timestampBO.setTimestamp(standardTimestamp);
                QosBO qosBO = new QosBO();
                //判断当前该存什么qos指标
                switch (this.qosType) {
                    case "packet_loss":
                        qosBO.setPacket_loss(qosCoreFieldDO.getValue());
                        break;
                    case "rtt_avg":
                        qosBO.setRtt_avg(qosCoreFieldDO.getValue());
                        break;
                    case "rtt_jitter_avg":
                        qosBO.setRtt_jitter_avg(qosCoreFieldDO.getValue());
                        break;
                }
                timestampBO.setQosBO(qosBO);
//                System.out.println("%%%timestampBO:"+timestampBO);
                //[timestamp:[packet_loss:1,...], timestamp:[]]
                //此处先进行一下时间戳去重，再存入,如果时间戳已经存在，当前这条数据废弃，即只保留第一次存入的数据
                Object mayBeNull = timestampsMap.get(standardTimestamp);
                if (mayBeNull != null){
                    if (timestampsMap != null && timestampsMap.get(standardTimestamp) == 1){
//                        continue;
//                        System.out.println("###");
                    }else {
//                        System.out.println("@@@");
                        timestampBOList.add(timestampBO);
                        if (timestampsMap != null){
                            timestampsMap.put(standardTimestamp,1);
                        }
                    }
                }else {
                    log.warn("mayBeNull is null!"+standardTimestamp);
                }
                linkBO.setTimestampBOList(timestampBOList);
            }
            //重置时间片！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
            if (timestampsMap != null){
                for(Map.Entry<String,Integer> entry : timestampsMap.entrySet()){
                    timestampsMap.put(entry.getKey(),0);
                }
            }
            List<LinkBO> linkBOList = new ArrayList<>();
            linkBOList.add(linkBO);
            oneLinkMoreDayBO.setLinkBOList(linkBOList);

//            redisConfg.getJedisBean().set(linkKey, oneLinkMoreDayBO.toString());
            List<OneLinkMoreDayBO> oneLinkMoreDayBOList = new ArrayList<>();
            oneLinkMoreDayBOList.add(oneLinkMoreDayBO);
            System.out.println("FirstCreateLinkQosDataSetAndSaveToRedis2-linkKey:" + linkKey);
//            System.out.println("^^^oneLinkMoreDayBOList:"+oneLinkMoreDayBOList);
            redisSaveUtil.setList(linkKey, oneLinkMoreDayBOList,0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 读取redis中的数据
     * 每调用一次该方法，读一条链路的数据
     *
     * @return
     */
    public List<Object> readOneLinkFromRedis() {
        System.out.println("readOneLinkFromRedis-linkKey:" + linkKey);
        return redisSaveUtil.getList(linkKey,0);
    }

    @RequestMapping("/readFromRedis")
    public List<Object> readOneLinkFromRedis1() {
//        System.out.println("link-key:"+link);
        return redisSaveUtil.getList("TX_Shanghai2-SDZX_WuXi",0);
    }

    /**
     * 时间戳转成时间
     */
    private String transferStampToTime(@RequestParam("srcTimestamp") String srcTimestamp) {
        if (srcTimestamp == null || srcTimestamp.equals("")) {
            return null;
        }
        long time = new Long(srcTimestamp);
        String result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time * 1000));
        return result;
    }
    /**
     * 传进来一个时间戳，即按照该时间戳的当天日期，创造出144个规范的时间片序列返回
     * @param time 格式："2021-12-08 00:00:00"
     * @return
     * @throws ParseException
     */
    public String[] createTheDayStandardTimestampList(String time) throws ParseException {
        //传进来一个时间戳先转换成日期
//        String time = transferStampToTime(timestamp);
        //创造当天日期的时间起点字符串
        //1. 截取第一个空格之前的字符串就是日期
        String preDate = StringUtils.substringBefore(time, " ");
        String standardDateStart = preDate + " " + "00:00:00";
        //2. 把这个日期再转换为时间戳
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = simpleDateFormat.parse(standardDateStart);
        long ts = date.getTime();
        //当天起点时间戳
        String tempRes = String.valueOf(ts).substring(0, 10);
        long res = new Long(tempRes);
        //根据当天起点时间戳，构造当天144个时间片集合
        //["1638288000-1638288600","1638288600-1638289200"...]
        String[] standardTimestampList = new String[144];
//        this.timestampsMap = new HashMap<>();
        for (int i = 0; i < standardTimestampList.length; i++) {
            standardTimestampList[i] = res + "-" + (res + 600);
            res += 600;
            //为timestampsMap赋值，同时初始化为全0的状态，表示初始不存在该时间片
            timestampsMap.put(standardTimestampList[i],0);
        }
        if (timestampsMap == null){
            log.warn("this.timestamp is null");
        }
        return standardTimestampList;
    }

    /**
     * 左闭右开区间：[)
     * 传入一个时间戳，返回一个标准时间片作为一个key
     */
    public String findStandardTimestamp(String timestamp) {
        long lt = new Long(timestamp);
        long startTimestamp = (lt / 600) * 600;
        String res = startTimestamp + "-" + (startTimestamp + 600);
        return res;
    }

    /**
     * 读取RTT数据，并存入Redis
     * 由于这不是第一次向Redis中存入链路的qos数据，而是将已经存在的链路读出来，按照时间戳对应存入新的指标
     * <p>
     * 调一次该方法，传入的是一条链路的数据
     */
    private Object insertNewQosToRedis(List<QosCoreFieldDO> qosCoreFieldDOList) {
        if (qosCoreFieldDOList == null) {
            log.warn("insertNewQosToRedis-qosCoreFieldDOList:"+qosCoreFieldDOList);
            return null;
        }
        //从Redis中获取的数据
        List<Object> hashMaps = readOneLinkFromRedis();
        if (hashMaps == null) {
            log.warn("readOneLinkFromRedis result is null!");
            return hashMaps;
        }
        //从数据库查到的数据
        for (QosCoreFieldDO qosCoreFieldDO : qosCoreFieldDOList) {
            //从数据库读出来的原始时间戳
            String timestamp = qosCoreFieldDO.getTimestamp();
            //时间戳的处理
            String standardTimestamp = findStandardTimestamp(timestamp);
            String value = qosCoreFieldDO.getValue();
            for (int i = 0; i < hashMaps.size(); i++) {
                //key是时间戳，value是qos列表
                OneLinkMoreDayBO oneLinkMoreDayBO = (OneLinkMoreDayBO) hashMaps.get(i);
                //由于每调用一次该方法传入一条链路的数据，所以linkBOList的长度恒为1
                List<LinkBO> linkBOList = oneLinkMoreDayBO.getLinkBOList();
                List<TimestampBO> timestampBOList = linkBOList.get(0).getTimestampBOList();
                if (timestampBOList != null){
                    for (TimestampBO timestampBO : timestampBOList) {
                        if (timestampBO.getTimestamp().equals(standardTimestamp)) {
                            //判断当前该存什么qos指标
                            switch (this.qosType) {
                                case "packet_loss":
                                    timestampBO.getQosBO().setPacket_loss(value);
                                    break;
                                case "rtt_avg":
                                    timestampBO.getQosBO().setRtt_avg(value);
                                    break;
                                case "rtt_jitter_avg":
                                    timestampBO.getQosBO().setRtt_jitter_avg(value);
                                    break;
                            }
                        }
                    }
                }else {
                    log.warn("timestampBOList is null!"+linkKey);
                }

            }
        }
        redisSaveUtil.setList(this.linkKey, hashMaps,0);
        return hashMaps;
    }
}
