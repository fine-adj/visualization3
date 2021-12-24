package com.example.demo.dto;

import lombok.Data;

@Data
public class WithTableNameAndCriteriaParam {
    private Integer id;
    private String tableName;
    private String sourceIp;
    private String sourceName;
    private String desIp;
    private String desName;
    private String timestamp;
}
