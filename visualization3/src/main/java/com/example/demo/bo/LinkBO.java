package com.example.demo.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class LinkBO implements Serializable {
    private String date;
    private List<TimestampBO> timestampBOList;

//    @Override
//    public String toString() {
//        String s="";
//        s=s+date+"/&";
//        if (timestampBOList != null){
//            s=s+timestampBOList.get(0).toString();
//            for (int i = 1; i < timestampBOList.size(); i++) {
//                s=s+"/|"+timestampBOList.get(i).toString();
//            }
//        }
//        return s;
//    }
}
