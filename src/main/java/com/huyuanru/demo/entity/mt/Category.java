package com.huyuanru.demo.entity.mt;

import lombok.Data;

import java.util.List;


@Data
public class Category {
        private int categoryId;

        private String categoryName;

        private int rank;

        private int iconType;

        private int position;

        private List<String> spuIds;

        private int categorySellableType;

        private List<Category> childDishCategories;

        private String categoryMultimediaList;

}
