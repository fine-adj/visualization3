package com.example.demo.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TimestampBO implements Serializable {
    private String timestamp;
    private QosBO qosBO;

//    @Override
//    public String toString() {
//       String s="";
//       s=s+timestamp+"/*";
//       if (qosBOList != null){
//           s=s+qosBOList.get(0).toString();
//           for (int i = 1; i < qosBOList.size(); i++) {
//                s = s+ "/!"+qosBOList.get(i).toString();
//           }
//       }
//        return s;
//    }
}
