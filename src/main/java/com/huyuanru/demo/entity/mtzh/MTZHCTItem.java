package com.huyuanru.demo.entity.mtzh;

import lombok.Data;

import java.util.List;


@Data
public class MTZHCTItem {


    private String id;

    private String name;

    private String status;

    private List<Object> skus;
}
