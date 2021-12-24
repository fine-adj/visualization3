package com.example.demo.config;

import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Configuration
public class TableMapperConfig {
    /**
     * 问题背景: mysql中数据库表数量过多，后端持久层使用mybatis框架如何读取每一张表的数据，
     *          并且，数据库表还是动态增加的。
     * 思路1：按照通常的处理方式，为每张表对应生成一套实体类，xml映射文件和mapper接口，实现思路如下：
     * 1. 自动生成表名，在generator配置文件中找到<table>标签，替换tableName属性
     * 2. 定期的执行generator配置文件，自动生成mybatis持久层三件套
     * 问题：
     * 1. 按照数据库中表名特点，根据日期动态生成表名，如果表名不存在，例如没有某一天的测量数据，
     *    generator组件将自动跳过该表，不作为。
     * 2. 会造成项目中文件数量快速增长，项目过大的情况。
     *
     * 思路2：
     * 观察到mysql中表数量虽多，但有一定规律：所有的表字段均相同，所以不需要为每一张表生成mybatis三件套，
     * 唯一需要改变的就是xml映射文件中对哪张表执行Sql操作。
     * 并且，考虑到数据读取要求：核心字段：value，timestamp，sourceIP,sourceName,desIp,desName
     *
     * 解决：
     * 1. 对mysql的操作基本只有读，所以修改generator生成的xml映射文件，写自定义sql，让tableName作为参数传入。
     * 2. 关键之一：需要根据mysql动态生成表名特点，按照同样规律自动生成表名，对于不存在的表捕获异常，不作为，继续处理下一个生成的表名。
     * 3. *按照前端展示的数据处理要求，设计从mysql中获取的数据如何处理并进行存储。
     *
     */


    private static String createDate(int day){
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.DAY_OF_WEEK,-day);//过去day天
        Date y = c.getTime();
        return format.format(y);
    }

    /**
     * 生成表名集合，默认生成前7天的表名
     */
    public static List<String> getQosTableNameList(String qosType,String time){
        List<String> tableNames = new ArrayList<>();
//        for (int i=7;i>0;i--){
//            //TODO 先把日期写死为了只处理一天的数据
//            String date = createDate(i);
//            String name = qosType+"_"+date;
//            tableNames.add(name);
//        }
        tableNames.add(qosType+"_"+time);
        return tableNames;
    }

}
