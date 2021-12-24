package com.example.demo.bo;

import lombok.Data;

import java.io.Serializable;

@Data
public class QosBO implements Serializable {
    private String packet_loss;
    private String rtt_avg;
    private String rtt_jitter_avg;

    public QosBO(){
        this.packet_loss = null;
        this.rtt_avg = null;
        this.rtt_jitter_avg = null;
    }

//    @Override
//    public String toString() {
//        return packet_loss+"/#"+rtt+"/#"+jitter;
//    }
}
