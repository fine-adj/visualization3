package com.example.demo.controller;

import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import net.sf.json.JSONObject;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 虚拟专线：负责处理前端直接用的数据，与前端交互
 */
@RestController
@Slf4j
public class DrawDataController {

    @Autowired
    private DataDetailController dataDetailController;

    private ArrayList<String> selectPosFailIpList = new ArrayList<>();

    /**
     * http访问远程服务器查询一个ip的经纬度
     *
     * @param ip
     * @return
     */
    @Deprecated
    public String selectCityPosInfo(String ip) {
        String urlParam = "http://192.168.0.248:8000/club203/ipipnet/" + ip;
        HttpURLConnection con = null;
        BufferedReader buffer = null;
        StringBuffer resultBuffer = null;
        try {
            URL url = new URL(urlParam);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestMethod("GET");
            //设置请求需要返回的数据类型和字符集类型
            con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            //允许写出
            con.setDoOutput(true);
            //允许读入
            con.setDoInput(true);
            //不使用缓存
            con.setUseCaches(false);
            //得到响应码
            int responseCode = con.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                //得到响应流
                InputStream inputStream = con.getInputStream();
                //将响应流转换成字符串
                resultBuffer = new StringBuffer();
                String line;
                buffer = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                while ((line = buffer.readLine()) != null) {
                    resultBuffer.append(line);
                    return resultBuffer.toString();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.warn("^^^^^^^^^^^^^^^^^");
        }
        return "";
    }

    /**
     * 查询所有IP的经纬度
     * [ 117.78.40.125,  139.9.4.193,  119.3.66.99, google.com, cloud.google.com, amazon.com, aws.amazon.com, azure.microsoft.com, qq.com, taobao.com, baidu.com, weixin.qq.com, douyin.com, toutiao.com, kuaishou.com, www.pinduoduo.com, www.12306.cn, pvp.qq.com, pay.weixin.qq.com, alipay.com, www.online.paymaxuae.com, pay.google.com, worldpay.com, 172.105.42.99]
     * @return
     */
    @Deprecated
    public HashMap<String, String> getInfo() {
        ArrayList<ArrayList<String>> linkSet = dataDetailController.createLinkSet("src/main/resources/node.txt");
        HashMap<String, String> ipPosMap = new HashMap<>();
        if (linkSet != null) {
            ArrayList<String> ipList = linkSet.get(1);
            System.out.println("总ip数量：" + ipList.size());
            for (String ip : ipList) {
                String s = selectCityPosInfo(ip);
                if (s != null && !("".equals(s))) {
                    JSONObject jsonObject = JSONObject.fromObject(s);
                    Object latitude = jsonObject.get("latitude"); //纬度
                    Object longitude = jsonObject.get("longitude"); //经度
                    Object cityName = jsonObject.get("city_name"); //城市名称
                    ipPosMap.put(ip, latitude + "-" + longitude+"-"+cityName);
                } else {
                    this.selectPosFailIpList.add(ip);
                }
            }
        }
        System.out.println("已查经纬度ip数量：" + ipPosMap.size());
        System.out.println(this.selectPosFailIpList);
        return ipPosMap;
    }

    @Deprecated
    @RequestMapping("/ttt")
    public String wrirteToAvl() {
        PrintWriter fw;
        HashMap<String, String> info = getInfo();
        System.out.println("info:"+info);
        String fileFullPath = "src/main/resources/ippos.txt";
        try {
            fw = new PrintWriter(fileFullPath);
            @SuppressWarnings("unused")
            BufferedWriter bw = new BufferedWriter(fw);
            log.info("开始向" + fileFullPath + "文件写入清单数据！");
            for (Map.Entry<String, String> entry : info.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                fw.write(key + ":" + value);
                fw.write("\n");
                fw.flush();
                fw.close();
            }
            log.info("写入完成！");
        } catch (Exception e) {
            log.error("写入文件时报错！", e);
        }
        return "success";
    }

    /**
     * 根据节点别名查询节点经纬度
     * @param nodeName 输入节点别名，比如："HuaWei_Shanghai2_3"
     * @return 返回值格式：{"key":"HuaWei_Shanghai2_3","value":"121.48941-31.40527"}
     */
    public Pair<String,String> getCityPosMap(@RequestParam("nodeName") String nodeName){
        String filePath = "src/main/resources/citypos.txt";
        HashMap<String,String> cityPosMap = new HashMap<>();
        Pair<String,String> pair = null;
        try {
            File file = new File(filePath);
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file));
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String city = StringUtils.substringBefore(line,"=");
                String pos = StringUtils.substringAfter(line,"=");
                cityPosMap.put(city,pos);
            }
            for (Map.Entry<String,String> map : cityPosMap.entrySet()){
                String pattern = ".*"+map.getKey()+".*";
                boolean isMatch = Pattern.matches(pattern, nodeName);
                if (isMatch){
                    pair = new Pair<>(nodeName,map.getValue());
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pair;
    }

}
