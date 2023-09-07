package com.huyuanru.demo.entity;


import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class EleBaseInfo {
    private String name;
    private String price;
    private List<EleSpecFood> specFoods;
}
