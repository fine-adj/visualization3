package com.example.demo.service;

import com.example.demo.dto.QosCoreFieldDO;
import com.example.demo.dto.QosDataDO;
import com.example.demo.dto.QosDataKeyDO;
import com.example.demo.dto.WithTableNameAndCriteriaParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface QosService {

    QosDataDO selectByPrimaryKey(QosDataKeyDO key);
//    List<PacketLoss20210828> test();
    List<QosCoreFieldDO> selectByExample2(WithTableNameAndCriteriaParam withTableNameAndCriteriaParam);

    List<QosCoreFieldDO> selectBySrcAndDesName(WithTableNameAndCriteriaParam param);

}
