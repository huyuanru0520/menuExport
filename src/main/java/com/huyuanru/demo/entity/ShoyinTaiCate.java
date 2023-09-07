package com.huyuanru.demo.entity;


import lombok.Data;

import java.util.List;

@Data
public class ShoyinTaiCate {
    private List<ShoYinTaiDish> cateDishList;

    private String categoryName;


}
