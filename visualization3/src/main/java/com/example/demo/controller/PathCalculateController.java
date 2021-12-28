package com.example.demo.controller;

import com.example.demo.bo.LinkAndQosBO;
import com.example.demo.bo.OneTimePicBO;
import com.example.demo.bo.QosBO;
import com.example.demo.bo.RedisDBNumAndDataDateMapping;
import com.example.demo.config.RedisConfg;
import com.example.demo.utils.RedisSaveUtil;
import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;

@RestController
@Slf4j
public class PathCalculateController {

    @Autowired
    private DataDetailController dataDetailController;

    @Autowired
    private RedisSaveUtil redisSaveUtil;

    private final String nodeFile = "src/main/resources/node2.txt";

    private final Integer maxJump = 3;

    private QosBO[][] topoArr;
    //节点和数组索引之间的映射
    private final HashMap<String, Integer> nodeIndexMap = new HashMap<>();

    //从redis中加载的链路qos数据
    private List<LinkAndQosBO> linkAndQosBOList = new ArrayList<>();

//    private final String timePic = "1638516000-1638516600";

    private Pair<ArrayList<String>, String> lastTimePicPathRtt;

    /**
     * 2.创建拓扑结构
     */
    public void createFullMeshTopo(String timePic) {
        //读取节点集合
        ArrayList<ArrayList<String>> nodeSet = dataDetailController.createLinkSet(nodeFile);
        int nodeSetSize = 0;
        if (nodeSet != null && nodeSet.get(0) != null) {
            nodeSetSize = nodeSet.get(0).size();//24
        }
        //创建FullMesh拓扑集合，用二维数据存，topoArr只用来存结构
        topoArr = new QosBO[nodeSetSize][nodeSetSize];
        //第四步：从redis中加载出来当前时间片的所有链路数据
        this.linkAndQosBOList = redisSaveUtil.getList(timePic, RedisDBNumAndDataDateMapping.NUM_7_20211203);
        if (this.linkAndQosBOList == null) {
            log.warn("linkAndQosBOList is null!");
            return;
        }
        for (int i = 0; i < nodeSetSize; i++) {
            nodeIndexMap.put(nodeSet.get(0).get(i), i);
        }
        for (LinkAndQosBO linkAndQosBO : this.linkAndQosBOList) {
            String srcName = StringUtils.substringBefore(linkAndQosBO.getLink(), "-");
            String desName = StringUtils.substringAfter(linkAndQosBO.getLink(), "-");
            Integer i = nodeIndexMap.get(srcName);
            Integer j = nodeIndexMap.get(desName);
            QosBO qosBO = linkAndQosBO.getQosBO();
            if (i != null && j != null) {
                if (qosBO != null && !("-9".equals(qosBO.getRtt_avg())) && !("-9".equals(qosBO.getRtt_jitter_avg()))
                        && !("-9".equals(qosBO.getPacket_loss()))) {
                    topoArr[i][j] = linkAndQosBO.getQosBO();
                } else {
                    topoArr[i][j] = null;
                }
            }
        }
    }

    /**
     * 3.计算任意两点所有备选虚拟专线：基于已经引入了qos数据构建的拓扑，已经排除了不连通的链路。
     * 注意：这个方法中先不计算每条路径的qos值，也就是Pair的value先都赋空！！！第6步筛选完成后再计算筛选后路径集合的每条路径的Qos数据
     * /**
     * 3.使用DFS计算任意两点所有备选虚拟专线
     */
    public void DFS(LinkedList<ArrayList<Integer>> linkresult, ArrayList<Integer> result, boolean isvisit[], int src, int end, int jump, int maxJump) {
        if (jump >= maxJump)        //跳出大于最大跳数限制
            return;
        for (int i = 0; i < isvisit.length; i++) {
            if (topoArr[src][i] != null && isvisit[i] == false) {
                if (i == end) {                 //找到一条链路
                    result.add(i);
                    ArrayList<Integer> result1 = new ArrayList<>();//深拷贝
                    for (int n = 0; n < result.size(); n++) {
                        result1.add(result.get(n));
                    }
                    linkresult.add(result1);
                    result.remove(result.size() - 1);
                    return;
                }
                isvisit[i] = true;
                result.add(i);
                jump += 1;
                DFS(linkresult, result, isvisit, i, end, jump, maxJump);        //继续向下查找链路
                result.remove(result.size() - 1);
                jump -= 1;
                isvisit[i] = false;
            }
        }
    }

    /**
     * 3.计算任意两点所有备选虚拟专线
     */
    public LinkedList<Pair<ArrayList<String>, QosBO>> srcAndDesAllPath(String timePic, String srcNode, String desNode) {
        LinkedList<Pair<ArrayList<String>, QosBO>> allPathList = new LinkedList<>();
        //调用该方法为了初始化全局的topoArr，或者写在静态代码块中，程序跑起来就加载
        createFullMeshTopo(timePic);
        //DFS
        Integer src = nodeIndexMap.get(srcNode), des = nodeIndexMap.get(desNode), jump = maxJump;
        if (src != null && des != null) {
            boolean[] isVisit = new boolean[topoArr.length];
//            for (int i = 0; i < isvisit.length; i++) {
//                isvisit[i] = false;
//            }
            HashMap<Integer, String> renodeIndexMap = new HashMap<>();
            for (Map.Entry<String, Integer> entry : nodeIndexMap.entrySet()) {
                renodeIndexMap.put(entry.getValue(), entry.getKey());
            }
            LinkedList<ArrayList<Integer>> linkresult = new LinkedList<>();
            ArrayList<Integer> result = new ArrayList<>();
            result.add(src);
            DFS(linkresult, result, isVisit, src, des, 0, jump);
            for (int i = 0; i < linkresult.size(); i++) {
                ArrayList<String> arrayList = new ArrayList<>();
                for (int j = 0; j < linkresult.get(i).size(); j++) {
                    arrayList.add(renodeIndexMap.get(linkresult.get(i).get(j)));
                }
                Pair<ArrayList<String>, QosBO> P = new Pair<>(arrayList, null);
                allPathList.add(P);
            }
        }

        return allPathList;

    }

    /**
     * 5.根据qos指标阈值进行链路预筛选
     *
     * @param rtt
     * @param jitter
     * @param packet_loss_threshold
     * @return
     */
    public Object[] filterLinkSetByQoS(String rtt, String jitter, String packet_loss_threshold) {
        //符合qos阈值要求的链路集合
        List<LinkAndQosBO> finishFilterLinkList = new ArrayList<>();
        //不符合qos要求的链路集合：将来用于筛选所有备选虚拟专线集合
        Set<String> unOkLinkSet = new HashSet<>();
        //TODO 这里应该根据链路名称去map中找到节点对应索引，从topoArr中读取qos
        if (this.linkAndQosBOList != null) {
            System.out.println("筛选之前的链路数量：" + this.linkAndQosBOList.size());
            for (LinkAndQosBO linkAndQosBO : this.linkAndQosBOList) {
                //如果时延满足要求就加入finishFilterLinkList
                if (linkAndQosBO.getQosBO() != null) {
                    String rtt_avg = linkAndQosBO.getQosBO().getRtt_avg();
                    String rtt_jitter_avg = linkAndQosBO.getQosBO().getRtt_jitter_avg();
                    String packet_loss = linkAndQosBO.getQosBO().getPacket_loss();
                    //同时满足3个指标的链路才会被保留
                    if ("-9".equals(rtt_avg) || "-9".equals(rtt_jitter_avg) || "-9".equals(packet_loss)) {
                        unOkLinkSet.add(linkAndQosBO.getLink());
                        continue;
                    }
                    //rtt_avg小于阈值rtt
                    if (compareQos(rtt, rtt_avg)) {
                        //rtt_jitter_avg小于阈值jitter
                        if (compareQos(jitter, rtt_jitter_avg)) {
                            //packet_loss小于阈值packet_loss_threshold
                            if (compareQos(packet_loss_threshold, packet_loss)) {
                                finishFilterLinkList.add(linkAndQosBO);
                            } else {
                                unOkLinkSet.add(linkAndQosBO.getLink());
                            }
                        } else {
                            unOkLinkSet.add(linkAndQosBO.getLink());
                        }
                    } else {
                        unOkLinkSet.add(linkAndQosBO.getLink());
                    }
                }
            }
            System.out.println("用rtt筛选后，链路数量：" + finishFilterLinkList.size());
        }
        return new Object[]{finishFilterLinkList, unOkLinkSet};
    }

    /**
     * 6.根据不满足qos阈值的链路筛选路径
     *
     * @param allPath
     * @param rtt
     * @param jitter
     * @param packet_loss_threshold
     * @return
     */
    public LinkedList<Pair<ArrayList<String>, QosBO>> filterPathByLink(LinkedList<Pair<ArrayList<String>, QosBO>> allPath, String rtt, String jitter, String packet_loss_threshold) {
        Object[] objects = filterLinkSetByQoS(rtt, jitter, packet_loss_threshold);
        if (objects == null || objects.length != 2) {
            return allPath;
        }
//        @SuppressWarnings("unchecked") //忽略警告但警告仍存在
        Object object = objects[1];
        Set<String> unOkLinkSet = null;
        if (object instanceof Set<?>) {
            unOkLinkSet = (Set<String>) object;
        }

        if (unOkLinkSet == null || unOkLinkSet.size() == 0) {
            log.warn("unOkLinkSet is null!");
            return allPath;
        }
        int i = 0;
        while (i < allPath.size()) {
            ArrayList<String> onePath = allPath.get(i).getKey();
            if (onePath != null && onePath.size() > 1) {
                int flag = 0;
                for (int j = 0; j < onePath.size(); j++) {
                    if (j + 1 < onePath.size()) {
                        String srcNode = onePath.get(j);
                        String desNode = onePath.get(j + 1);
                        //如果路径中的链路包含于不满足要求的链路集合，那就从allPath移除整条链路
                        if (unOkLinkSet.contains(srcNode + "-" + desNode)) {
                            allPath.remove(allPath.get(i));
                            flag += 1;
                            break;
                        }
                    }
                }
                if (flag == 0) {
                    i++;
                }
            }
        }
//        log.info("用不满足qos阈值的链路筛选路径结果："+allPath);
        return allPath;
    }

    /**
     * 7.计算一条路径rtt
     *
     * @param path
     * @return
     */
    public String getPathRtt(ArrayList<String> path) {
//        createFullMeshTopo();
        //遍历传进来的路径每一条链路，nodeIndexMap中找到对应索引，再从topoArr中拿到qos
        if (path == null || path.size() <= 1) {
            return null;
        }
        Double pathRtt = 0.0;
        for (int i = 0; i < path.size(); i++) {
            Integer srcNodeIdx = this.nodeIndexMap.get(path.get(i));
            if (i + 1 < path.size()) {
                Integer desNodeIdx = this.nodeIndexMap.get(path.get(i + 1));
                if (srcNodeIdx != null && desNodeIdx != null) {
                    QosBO qosBO = this.topoArr[srcNodeIdx][desNodeIdx];
                    if (qosBO != null) {
                        pathRtt = pathRtt + Double.parseDouble(qosBO.getRtt_avg());
                    }
                }
            }
        }
        return pathRtt.toString();
    }

    /**
     * 7.计算一条路径丢包率
     *
     * @param path
     * @return
     */
    public String getPathPacketLoss(ArrayList<String> path) {
//        createFullMeshTopo();
        if (path == null || path.size() <= 1) {
            return null;
        }
        Double pathPacketUnLoss = 1.0;
        for (int i = 0; i < path.size(); i++) {
            Integer srcNodeIdx = this.nodeIndexMap.get(path.get(i));
            if (i + 1 < path.size()) {
                Integer desNodeIdx = this.nodeIndexMap.get(path.get(i + 1));
                if (srcNodeIdx != null && desNodeIdx != null) {
                    QosBO qosBO = this.topoArr[srcNodeIdx][desNodeIdx];
                    if (qosBO != null) {
                        BigDecimal db1 = new BigDecimal(1);
                        BigDecimal unLoss = db1.subtract(new BigDecimal(qosBO.getPacket_loss()));
                        BigDecimal db2 = new BigDecimal(pathPacketUnLoss.toString());
                        pathPacketUnLoss = db2.multiply(unLoss).doubleValue();
                    }
                }
            }
        }
        BigDecimal db3 = new BigDecimal(1);
        return db3.subtract(new BigDecimal(pathPacketUnLoss.toString())).toString();
    }

    /**
     * 7.计算一条路径抖动
     *
     * @param path
     * @return
     */
    public String getPathJitter(ArrayList<String> path,String tempPathRtt) {
        if (path == null || path.size() <= 1) {
            return null;
        }
        String jitter = null;
        if (this.lastTimePicPathRtt != null){
            jitter = new BigDecimal(tempPathRtt).subtract(new BigDecimal(this.lastTimePicPathRtt.getValue())).toString();
            return Math.abs(Double.parseDouble(jitter))+"";
        }else {
            return null;
        }

    }

    /**
     * 根据链路筛选后的路径集合：计算每一条路径的qos
     *
     * @param allPath 拿到的是经过链路筛选后的路径集合
     * @return
     */
    public LinkedList<Pair<ArrayList<String>, QosBO>> finishFilterByLinkAndWithQoS(LinkedList<Pair<ArrayList<String>, QosBO>> allPath) {
        if (allPath == null) {
            log.warn("finishFilterByLinkAndWithQoS_param allPath is null!");
            return null;
        }
        LinkedList<Pair<ArrayList<String>, QosBO>> pathWithQosList = new LinkedList<>();
        for (Pair<ArrayList<String>, QosBO> onePathPair : allPath) {
            ArrayList<String> onePath = onePathPair.getKey();
            String pathRtt = getPathRtt(onePath);
            String pathPacketLoss = getPathPacketLoss(onePath);
            String pathJitter = getPathJitter(onePath,pathRtt);//这里第一条专线抖动应该是null
            //lastTimePicPathRtt的赋值必须在getPathJitter()调用之后
            this.lastTimePicPathRtt = new Pair<>(onePath,pathRtt);
            QosBO qosBO = new QosBO(pathRtt, pathJitter, pathPacketLoss);
            Pair<ArrayList<String>, QosBO> pathWithQos = new Pair<>(onePath, qosBO);
            pathWithQosList.add(pathWithQos);
        }
        return pathWithQosList;
    }

    /**
     * 8.根据需求qos筛选虚拟专线集合---最终结果
     *
     * @param qosBO
     * @param allPath
     * @return
     */
    public LinkedList<Pair<ArrayList<String>, QosBO>> filterPathByQos(QosBO qosBO, LinkedList<Pair<ArrayList<String>, QosBO>> allPath) {
        if (allPath == null) {
            return null;
        }
        //遍历allPath中每一条路径，分别用rtt,packet,jitter筛选
        int i = 0;
        //先用rtt筛：
        while (i < allPath.size()) {
            Pair<ArrayList<String>, QosBO> pair = allPath.get(i);
//            ArrayList<String> onePath = pair.getKey();
            QosBO qos = pair.getValue();
            int flag = 0;
            //rtt大于阈值
            if (!compareQos(qosBO.getRtt_avg(), qos.getRtt_avg())) {
                allPath.remove(pair);
                flag += 1;
            }
            if (flag == 0) {
                i++;
            }
        }
        //再用packet_loss筛
        if (allPath.size() > 0) {
            i = 0;
            while (i < allPath.size()) {
                Pair<ArrayList<String>, QosBO> pair = allPath.get(i);
                ArrayList<String> onePath = pair.getKey();
                QosBO qos = pair.getValue();
                int flag = 0;
                //packet_loss大于阈值
                if (!compareQos(qosBO.getPacket_loss(), qos.getPacket_loss())) {
                    allPath.remove(pair);
                    flag += 1;
                }
                if (flag == 0) {
                    i++;
                }
            }
        } else {
            log.warn("rtt筛完后，allPath已为空！");
        }
        //再用抖动筛
        if (allPath.size()>0){
            i=0;
            while (i < allPath.size()){
                Pair<ArrayList<String>,QosBO> pair = allPath.get(i);
                ArrayList<String> onePath = pair.getKey();
                QosBO qos = pair.getValue();
                int flag = 0;
                //jitter大于阈值
                if (!compareQos(qosBO.getRtt_jitter_avg(),qos.getRtt_jitter_avg())){
                    allPath.remove(pair);
                    flag += 1;
                }
                if (flag == 0){
                    i++ ;
                }
            }
        }else {
            log.warn("packet_loss筛完后，allPath已为空！");
        }
        return allPath;
    }

    @RequestMapping("/getfromredis")
    public List<Object> get1() {
        return redisSaveUtil.getList("1638516000-1638516600", RedisDBNumAndDataDateMapping.NUM_7_20211203);
    }

    @RequestMapping("/getfromnode2txt")
    public ArrayList<String> get2() {
        ArrayList<ArrayList<String>> nodeSet = dataDetailController.createLinkSet(nodeFile);
        return nodeSet.get(0);
    }

    /**
     * 判断rtt1是否大于rtt2
     *
     * @param rtt1
     * @param rtt2
     * @return
     */
    private boolean compareQos(String rtt1, String rtt2) {
        if (rtt1 == null || rtt2 == null) {
            return false;
        }
        BigDecimal decimalRtt1 = new BigDecimal(rtt1);
        BigDecimal decimalRtt2 = new BigDecimal(rtt2);
        //decimalRtt1大于阈值decimalRtt2
        return decimalRtt1.compareTo(decimalRtt2) >= 0;
    }

    @RequestMapping("/getpathresult")
    public HashMap<String, LinkedList<Pair<ArrayList<String>, QosBO>>> getPathResult() {
        LinkedList<Pair<ArrayList<String>, QosBO>> result = null;
        HashMap<String, LinkedList<Pair<ArrayList<String>, QosBO>>> oneDayPathResultList = new HashMap<>();
        try {
            //获取标准时间片区间：
            String[] theDayStandardTimestampList = dataDetailController.createTheDayStandardTimestampList("2021-12-03 00:00:00");
            for (int i = 0; i < 144; i++) {
                String timePic = theDayStandardTimestampList[i];
                //创建所有备选虚拟专线，其中包括构建网络拓扑
                LinkedList<Pair<ArrayList<String>, QosBO>> allPath = srcAndDesAllPath(timePic, "Ali_HuHeHaoTe2", "Ali_HuaDong1");
                System.out.println("跳数限制" + this.maxJump + "跳，所有备选虚拟专线数量：" + allPath.size());
                //根据链路筛选结果，筛选路径集合.包括根据需求QOS筛选链路集合
                LinkedList<Pair<ArrayList<String>, QosBO>> finishPathFilterByLink = filterPathByLink(allPath, "700", "10000000", "0.9");
                //基于链路筛选后的路径集合，其中包括为每一条路径计算qos值
                LinkedList<Pair<ArrayList<String>, QosBO>> pathWithQosList = finishFilterByLinkAndWithQoS(finishPathFilterByLink);
//              log.info("pathWithQosList--"+pathWithQosList);
                //最终再根据需求qos筛选虚拟专线
                QosBO qosBO = new QosBO("300", "400", "0.4");
                result = filterPathByQos(qosBO, pathWithQosList);
                oneDayPathResultList.put(timePic, result);
                log.info("最终虚拟专线计算结果数量：" + result.size());
            }

        } catch (ParseException parseException) {
            parseException.printStackTrace();
        }
        return oneDayPathResultList;

    }


}
