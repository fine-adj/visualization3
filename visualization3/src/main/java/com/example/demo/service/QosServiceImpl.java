package com.example.demo.service;

import com.example.demo.dto.QosCoreFieldDO;
import com.example.demo.dto.QosDataDO;
import com.example.demo.dto.QosDataKeyDO;
import com.example.demo.dto.WithTableNameAndCriteriaParam;
import com.example.demo.mapper.QosDataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QosServiceImpl implements QosService{
//    @Autowired(required=false)

    @Autowired
    private QosDataMapper qosDataMapper;


//    public QosServiceImpl(PacketLoss20210828Mapper packetLoss20210828Mapper){
//        this.packetLoss20210828Mapper = packetLoss20210828Mapper;
//    }
//@Autowired(required = false)
//public void setSqlSessionFactory(
//        @Qualifier("sqlSessionFactory")
//                SqlSessionFactory sqlSessionFactory) {
//    super.setSqlSessionFactory(sqlSessionFactory);
//}
//    @Override
//    public List<PacketLoss20210828> test() {
//        //generator自动生成的dto包下的example类
//        PacketLoss20210828Example packetLoss20210828Example = new PacketLoss20210828Example();
//        //创建一个条件
//        PacketLoss20210828Example.Criteria criteria = packetLoss20210828Example.createCriteria();
//        criteria.andSourceNameEqualTo("HuaWei_HK2");
//
//        List<PacketLoss20210828> packetLoss20210828s = packetLoss20210828Mapper.selectByExample(packetLoss20210828Example);
//        return packetLoss20210828s;
//    }

    @Override
    public QosDataDO selectByPrimaryKey(QosDataKeyDO key){
        return qosDataMapper.selectByPrimaryKey(key);
    }

    @Override
    public List<QosCoreFieldDO> selectByExample2(WithTableNameAndCriteriaParam withTableNameAndCriteriaParam) {
        return qosDataMapper.selectByExample2(withTableNameAndCriteriaParam);
    }

    @Override
    public List<QosCoreFieldDO> selectBySrcAndDesName(WithTableNameAndCriteriaParam param){
        return qosDataMapper.selectBySrcAndDesName(param);
    }
}
