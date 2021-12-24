package com.example.demo.dto;

import lombok.Data;

@Data
public class QosCoreFieldDO {
    private Integer id;
    private String sourceIp;
    private String sourceName;
    private String desIp;
    private String desName;
    private String timestamp;
    private String value;
}
