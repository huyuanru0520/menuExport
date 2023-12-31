package com.huyuanru.demo.entity.eleme;


import lombok.Data;

import java.util.List;

@Data
public class EleBaseInfo {
    private String name;
    private String price;
    private String originPrice;
    private List<EleSpecFood> specFoods;
    private String imageHash;
}
