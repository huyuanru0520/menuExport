package com.huyuanru.demo.entity.keruyun;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;


@Data
public class SpuInfo {

    private String defaultSkuId;
    private String name;
    private Integer sellPrice;
    private String dishName;
    private List<JSONObject> cookbookDishSkuList;


}
