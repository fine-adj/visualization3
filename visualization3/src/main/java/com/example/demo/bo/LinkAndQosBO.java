package com.example.demo.bo;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
public class LinkAndQosBO implements Serializable {
    private String link;
    private QosBO qosBO;
}
