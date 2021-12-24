package com.example.demo.bo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OneLinkMoreDayBO implements Serializable {
    /**
     * {
     *     link1:[2021.1.2: [timestamp:[packet_loss=1,rtt=2,jitter=3],
     *                      [timestamp:[packet_loss=1,rtt=2,jitter=3],
     *                      [timestamp:[packet_loss=1,rtt=2,jitter=3],
     *            2021.1.3:[timestamp:[packet_loss=1,rtt=2,jitter=3],
     *                     [timestamp:[packet_loss=1,rtt=2,jitter=3],
     *                     [timestamp:[packet_loss=1,rtt=2,jitter=3],]
     *      link2:.....]
     * }
     */
    private String linkName;
    private List<LinkBO> linkBOList;

//    @Override
//    public String toString(){
//        String s = "";
//        s=s+linkName+"/:";
//        if (linkBOList != null){
//            s=s+linkBOList.get(0).toString();
//            for (int i = 1; i < linkBOList.size(); i++) {
//                s=s+"/,"+linkBOList.get(i).toString();
//            }
//        }
//        return s;
//    }
}
