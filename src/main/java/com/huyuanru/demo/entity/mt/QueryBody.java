package com.huyuanru.demo.entity.mt;


import com.alibaba.fastjson.JSONObject;
import lombok.Data;

@Data
public class QueryBody {
    private JSONObject menu;

    private JSONObject spus;

    private String  menus;

    private String spuss;
}
