package com.example.demo.bo;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@Component
@Data
public class OneTimePicBO implements Serializable {
    private List<LinkAndQosBO> linkAndQosBOList;
}
