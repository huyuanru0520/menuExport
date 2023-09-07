package com.huyuanru.demo.entity;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;
@Data
public class EleSpecFood {

    private List<EleSpec> specs;

    private String price;
}
