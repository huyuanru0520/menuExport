package com.huyuanru.demo.entity.export;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaseInfo {

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品分类
     */
    private String category;

    /**
     * 商品价格
     */
    private String price;

    /**
     * 商品规格
     */
    private String specification = "1人份";

    /**
     * 商品数量
     */
    private String nums = "1";

    /**
     * 图片链接
     */
    private String picUrl ;
}
