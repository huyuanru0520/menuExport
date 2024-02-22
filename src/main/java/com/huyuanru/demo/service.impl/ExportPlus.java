package com.huyuanru.demo.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.huyuanru.demo.entity.eleme.EleBaseInfo;
import com.huyuanru.demo.entity.eleme.ItemGroup;
import com.huyuanru.demo.entity.export.BaseInfo;
import com.huyuanru.demo.entity.keruyun.Cates;
import com.huyuanru.demo.entity.keruyun.SpuInfo;
import com.huyuanru.demo.entity.mt.Category;
import com.huyuanru.demo.entity.mtzh.MTZHCTInfo;
import com.huyuanru.demo.entity.mtzh.MTZHCTItem;
import com.huyuanru.demo.entity.shouyintai.ShoYinTaiDish;
import com.huyuanru.demo.entity.shouyintai.ShoyinTaiCate;
import com.huyuanru.demo.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


@RestController
//@CrossOrigin
@RequestMapping("/exportPlus")
@Slf4j
public class ExportPlus {


    private Path sytJson;

    private final static String MTZH_STATUS = "AVAILABLE";


    @PostMapping("/exportInOne")
    public void exportInOne(@RequestBody JSONObject data, HttpServletResponse response) {
        Workbook workbook = null;
        ServletOutputStream outputStream = null;
        try {
            List<BaseInfo> infos = getBaseInfos(data);
            if (!CollectionUtils.isEmpty(infos)) {
                workbook = export(response, infos);
                outputStream = response.getOutputStream();
                workbook.write(outputStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (workbook != null) {
                    workbook.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    private List<BaseInfo> getBaseInfos(JSONObject data) {
        String content = data.getString("content");
        if (StringUtils.isNotBlank(content)) {
            if (content.contains("query.v2")) {
                return exportEleData(data);
            } else if (content.contains("poi")) {
                return exportMtZHCT(data);
            } else if (content.contains("shopId")) {
                return exportMtData(data);
            } else if (content.contains("components")) {
                return exporKeruyun(data);
            } else if (content.contains("allMenuTime")) {
                return eshineExport(data);
            }
        }
        return Lists.newArrayList();
    }


    private List<BaseInfo> exportEleData(@RequestBody JSONObject data) {

        List<BaseInfo> infos = new ArrayList<>();
        JSONObject responseBody = JSONObject.parseObject(data.getString("content"));
        JSONArray itemGroups = responseBody.getJSONObject("data").getJSONObject("resultMap").getJSONObject("menu").getJSONArray("itemGroups");
        for (Object o : itemGroups) {
            ItemGroup itemGroup = JSONObject.parseObject(JSONObject.toJSONString(o), ItemGroup.class);
            //String category = intercept(itemGroup.getName());
            String category = itemGroup.getName();
            if (StringUtils.equals(category, "优惠") || StringUtils.equals("热销", category)) {
                continue;
            }
            List<Object> items = itemGroup.getItems();
            for (Object item : items) {
                EleBaseInfo eleBaseInfo = JSONObject.parseObject(JSONObject.toJSONString(item), EleBaseInfo.class);
                if (StringUtils.isBlank(eleBaseInfo.getPrice())) {
                    continue;
                }
                BaseInfo info = BaseInfo.builder().category(category).name(eleBaseInfo.getName())
                        .price(StringUtils.isBlank(eleBaseInfo.getOriginPrice()) ? eleBaseInfo.getPrice() : eleBaseInfo.getOriginPrice())
                        .specification("1人份").nums("1").build();
                infos.add(info);
            }
        }
        //过滤掉infos中name相同的
        infos = infos.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(BaseInfo::getName))), ArrayList::new));
        //按照category排序
        infos.sort(Comparator.comparing(BaseInfo::getCategory));
        return infos;
    }


    private List<BaseInfo> exportMtData(@RequestBody JSONObject data) {
        Map<String, String> urlMap = parseUrl(data.getString("content"));
        String menu = HttpUtils.get(urlMap.get("menus"));
        String spus = HttpUtils.get(urlMap.get("spuss"));
        List<BaseInfo> infos = new ArrayList<>();
        JSONObject spuString = JSONObject.parseObject(spus);
        Map<String, Object> spuMap = spuString.getJSONObject("data").getJSONObject("spuDetail");
        JSONObject menuString = JSONObject.parseObject(menu);
        JSONArray categories = menuString.getJSONObject("data").getJSONArray("categories");
        for (Object category : categories) {
            List<String> spuIds = new ArrayList<>();
            Category cate = JSONObject.parseObject(JSONObject.toJSONString(category), Category.class);
            if (!CollectionUtils.isEmpty(cate.getSpuIds())) {
                spuIds.addAll(cate.getSpuIds());
            }
            if (!CollectionUtils.isEmpty(cate.getChildDishCategories())) {
                cate.getChildDishCategories().forEach(child -> spuIds.addAll(child.getSpuIds()));
            }
            for (String spuId : spuIds) {
                Map<String, Object> spu = (Map<String, Object>) spuMap.get(spuId);
                if (spu == null || spu.isEmpty()) {
                    log.error("spuId{}没有该商品", spuId);
                    continue;
                }
                String name = (String) spu.get("spuName");
                String price = String.valueOf(spu.get("currentPrice"));
                BaseInfo baseInfo = BaseInfo.builder().category(cate.getCategoryName()).name(name).price(price)
                        .specification("1人份").nums("1").build();
                infos.add(baseInfo);
            }
        }
        return infos;
    }


    private List<BaseInfo> exportMtZHCT(@RequestBody JSONObject data) {
        List<BaseInfo> infos = new ArrayList<>();
        JSONObject responseBody = JSONObject.parseObject(data.getString("content"));
        JSONArray menus = responseBody.getJSONObject("data").getJSONObject("poi").getJSONArray("menus");
        for (Object o : menus) {
            MTZHCTInfo mtzhctInfo = JSONObject.parseObject(JSONObject.toJSONString(o), MTZHCTInfo.class);
            List<Object> items = mtzhctInfo.getItems();

            for (Object item : items) {
                MTZHCTItem mtzhctItem = JSONObject.parseObject(JSONObject.toJSONString(item), MTZHCTItem.class);
                String status = mtzhctItem.getStatus();
                String name = mtzhctItem.getName();
                List<Object> skus = mtzhctItem.getSkus();
                if (!MTZH_STATUS.equals(status) || StringUtils.isBlank(name) || CollectionUtils.isEmpty(skus)) {
                    continue;
                }
                Integer price = JSONObject.parseObject(JSONObject.toJSONString(skus.get(0))).getInteger("price");
                BaseInfo baseInfo = BaseInfo.builder().category(mtzhctInfo.getName()).name(name)
                        .price(String.valueOf(price / 100 + price % 100))
                        .specification("1人份").nums("1").build();
                infos.add(baseInfo);
            }
        }
        //过滤掉infos中name相同的
        infos = infos.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(BaseInfo::getName))), ArrayList::new));
        //按照category排序
        infos.sort(Comparator.comparing(BaseInfo::getCategory));
        return infos;
    }


    @PostMapping("/exportShouyinTai")
    public void exportShouyinTai(HttpServletResponse response) {
        try (FileInputStream is = new FileInputStream(sytJson.toFile());
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int i;
            while ((i = is.read()) != -1) {
                baos.write(i);
            }
            String str = baos.toString("utf-8");
            List<BaseInfo> infos = new ArrayList<>();
            JSONObject jsonObject = JSONObject.parseObject(str);
            JSONArray cateList = jsonObject.getJSONObject("data").getJSONArray("dishList");
            for (Object o : cateList) {
                ShoyinTaiCate shoyinTaiCate = JSONObject.parseObject(JSONObject.toJSONString(o), ShoyinTaiCate.class);
                String categoryName = shoyinTaiCate.getCategoryName();
                List<ShoYinTaiDish> cateDishList = shoyinTaiCate.getCateDishList();
                if (!CollectionUtils.isEmpty(cateDishList)) {
                    for (ShoYinTaiDish shoYinTaiDish : cateDishList) {
                        BaseInfo info = BaseInfo.builder().category(categoryName).name(shoYinTaiDish.getDishName())
                                .price(String.valueOf(shoYinTaiDish.getPrice() / 100))
                                .specification("1人份").nums("1").build();
                        infos.add(info);
                    }
                }
            }
            infos = infos.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(BaseInfo::getName))), ArrayList::new));
            //按照category排序
            infos.sort(Comparator.comparing(BaseInfo::getCategory));
            XSSFWorkbook export = export(response, infos);
            export.write(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    /**
     * 该数据无法一次性获取全部, 需要将两个接口的数据拼接后导出
     * 在queryspuinfos接口中获取data中的spuinfos 放到 querysdkpageconfig的 spuinfos 列表内
     *
     * @param data
     */
    private List<BaseInfo> exporKeruyun(@RequestBody JSONObject data) {
        JSONObject content = JSONObject.parseObject(data.getString("content"));
        JSONArray tempArray = content.getJSONObject("data").getJSONObject("data").getJSONArray("components");
        if (CollectionUtils.isEmpty(tempArray)) {
            return Lists.newArrayList();
        }
        Optional<Object> originDish = tempArray.stream().filter(o -> StringUtils.equals("menu-dish-component", JSONObject.parseObject(JSONObject.toJSONString(o)).getString("componentCode"))).findFirst();
        JSONObject datas = JSON.parseObject(JSONObject.toJSONString(originDish.get())).getJSONObject("data").getJSONObject("dishMenu").getJSONObject("data");

        Set<Cates> dishCates = datas.getJSONArray("dishCates").stream().map(o -> JSONObject.parseObject(JSONObject.toJSONString(o), Cates.class)).collect(Collectors.toSet());

        Map<String, JSONObject> spuInfos = (Map) datas.get("spuInfos");

        List<BaseInfo> infos = Lists.newArrayList();
        for (Cates dishCate : dishCates) {
            for (String spuId : dishCate.getSpuIds()) {
                SpuInfo spuInfo = JSONObject.parseObject(JSONObject.toJSONString(spuInfos.get(spuId)), SpuInfo.class);
                if (Objects.isNull(spuInfo)) {
                    log.error("没有该商品{}", spuId);
                    continue;
                }
                BaseInfo info = BaseInfo.builder().category(dishCate.getName()).price(String.valueOf(spuInfo.getSellPrice() / 100)).name(spuInfo.getDishName()).specification("1人份").nums("1").build();
                infos.add(info);
            }
        }
        //按照category排序
        infos.sort(Comparator.comparing(BaseInfo::getCategory));
        return infos;
    }


    private List<BaseInfo> eshineExport(JSONObject data) {
        List<BaseInfo> baseInfos = Lists.newArrayList();
        Object content = data.getJSONObject("content").getJSONObject("result").getJSONArray("content").stream().findFirst().get();
        JSONArray spuInfos = JSONObject.parseObject(JSONObject.toJSONString(content)).getJSONArray("content");
        for (Object temp : spuInfos) {
            JSONObject spuInfo = JSONObject.parseObject(JSONObject.toJSONString(temp));
            String name = spuInfo.getString("productName");
            Float price = 0.0F;
            String cateName = "";
            JSONObject productScaleGroup = spuInfo.getJSONObject("productScaleGroup");
            if (Objects.nonNull(productScaleGroup)) {
                JSONArray tempArr = productScaleGroup.getJSONArray("productScale");
                if (!CollectionUtils.isEmpty(tempArr)) {
                    JSONObject priceObject = JSONObject.parseObject(JSONObject.toJSONString(tempArr.stream().findFirst().get()));
                    price = priceObject.getFloat("price");
                }
            }
            JSONArray cateArr = spuInfo.getJSONArray("productCategoryDetail");
            if (!CollectionUtils.isEmpty(cateArr)) {
                JSONObject cateInfo = JSONObject.parseObject(JSONObject.toJSONString(cateArr.stream().findFirst().get()));
                cateName = cateInfo.getString("name");
            }
            BaseInfo info = BaseInfo.builder().name(name).category(cateName).price(String.valueOf(price)).specification("1人份").nums("1").build();
            baseInfos.add(info);
        }
        return baseInfos;
    }


    private XSSFWorkbook export(HttpServletResponse response, List<BaseInfo> infos) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.addHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode("meun_download.xls", "utf-8"));
        XSSFWorkbook workbook = new XSSFWorkbook(Objects.requireNonNull(this.getClass().getResourceAsStream("/template.xlsx")));
        CellStyle cellStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 11);
        cellStyle.setFont(font);
        XSSFSheet sheet = workbook.getSheetAt(0);
        CellStyle cellStyleNew = workbook.createCellStyle();
        Font fontNew = workbook.createFont();
        fontNew.setFontName("宋体");
        fontNew.setFontHeightInPoints((short) 11);
        cellStyleNew.setFont(fontNew);
        cellStyleNew.setFillBackgroundColor(IndexedColors.WHITE.getIndex());
        cellStyleNew.setAlignment(HorizontalAlignment.CENTER);
        for (int i = 0; i < infos.size(); i++) {
            BaseInfo info = infos.get(i);
            Row row = sheet.createRow(i + 1);
            Cell cell0 = row.createCell(0);
            cell0.setCellValue(info.getCategory());
            cell0.setCellStyle(cellStyleNew);

            Cell cell1 = row.createCell(1);
            cell1.setCellValue(info.getName());
            cell1.setCellStyle(cellStyleNew);

            Cell cell2 = row.createCell(2);
            cell2.setCellValue(info.getPrice());
            cell2.setCellStyle(cellStyleNew);

            Cell cell3 = row.createCell(3);
            cell3.setCellValue(info.getSpecification());
            cell3.setCellStyle(cellStyleNew);

            Cell cell4 = row.createCell(4);
            cell4.setCellValue(info.getNums());
            cell4.setCellStyle(cellStyleNew);
        }
        return workbook;
    }


    private Map<String, String> parseUrl(String orgUrl) {
        //将s按&分割,并且分割后按等号分割并汇集成map
        String[] split = orgUrl.split("&");
        HashMap<String, Object> paramsMap = Maps.newHashMap();
        for (String orgParam : split) {
            if (StringUtils.isNotBlank(orgParam) && orgParam.contains("=")) {
                String[] paramNameAndValue = orgParam.split("=");
                if (paramNameAndValue.length > 1 && StringUtils.isNotBlank(paramNameAndValue[0])) {
                    paramsMap.put(paramNameAndValue[0], paramNameAndValue[1]);
                }
            }
        }

        Object shopId = paramsMap.get("shopId");
        Object tableNum = paramsMap.get("tableNum");
        Object tenantId = paramsMap.get("tenantId");
        Object sign = paramsMap.get("sign");
        Object timestamp = paramsMap.get("t");


        String fpmTempUrl = "https://rms.meituan.com/diancan/menu/api/loadFMPInfo?" +
                "mtShopId=" + shopId +
                "&tableNum=" + tableNum + "&reserveMode=0&selectedTime=0&peopleCount=0&mealType=0&fromMenu=true&orderBizTag=0" +
                "&timestamp=" + timestamp + "&preview=false&previewParam=&multiShop=" +
                "&tenantId=" + tenantId + "&orderViewId=&shopCache=&orderScene=1" +
                "&validateAllowOdAfterClose=" + shopId + "_true&buffetLimitMealTipsFlag=true" +
                "&sign=" + sign;

        String spuTempUlr = "https://rms.meituan.com/diancan/menu/api/pageSpuInfo?" +
                "mtShopId=" + shopId +
                "&tableNum=" + tableNum + "&reserveMode=0&peopleCount=0&selectedTime=0&pageNum=1" +
                "&timestamp=" + timestamp + "&bizType=0" +
                "&tenantId=" + tenantId + "&shopCache=" +
                "&sign=" + sign;

        HashMap<String, String> urlMap = new HashMap<>();
        urlMap.put("menus", fpmTempUrl);
        urlMap.put("spuss", spuTempUlr);
        return urlMap;
    }


}
