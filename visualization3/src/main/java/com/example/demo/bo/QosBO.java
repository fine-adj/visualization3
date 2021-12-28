package com.example.demo.bo;

import lombok.Data;

import java.io.Serializable;

@Data
public class QosBO implements Serializable {
    //7272882838619294397
    //由于缓存实体数据后，实体类有改动，所以会报错序列化版本ID不匹配。
    //解决1：清空缓存重新存。解决2：将实体类序列化版本ID改为和缓存一致的。
    private static final long serialVersionUID = 5028871392060667208L;
    private String packet_loss;
    private String rtt_avg;
    private String rtt_jitter_avg;

    public QosBO(){
        this.packet_loss = null;
        this.rtt_avg = null;
        this.rtt_jitter_avg = null;
    }

    public QosBO(String rtt_avg,String rtt_jitter_avg,String packet_loss){
        this.rtt_avg = rtt_avg;
        this.rtt_jitter_avg = rtt_jitter_avg;
        this.packet_loss = packet_loss;
    }

//    @Override
//    public String toString() {
//        return packet_loss+"/#"+rtt+"/#"+jitter;
//    }
}
