package com.huyuanru.demo.service.impl;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.huyuanru.demo.entity.eleme.EleBaseInfo;
import com.huyuanru.demo.entity.eleme.ItemGroup;
import com.huyuanru.demo.entity.export.BaseInfo;
import com.huyuanru.demo.entity.mt.Category;
import com.huyuanru.demo.entity.mtzh.MTZHCTInfo;
import com.huyuanru.demo.entity.mtzh.MTZHCTItem;
import com.huyuanru.demo.entity.shouyintai.ShoYinTaiDish;
import com.huyuanru.demo.entity.shouyintai.ShoyinTaiCate;
import com.huyuanru.demo.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/jsonToExcel")
@Slf4j
public class JsonToExcel {


    private Path templatePath;

    private Path json;

    private Path mtzhJson;

    private Path mtUrl;

    private Path sytJson;


    private final static String MTZH_STATUS = "AVAILABLE";

    @GetMapping("/getMtData")
    public void toExcel(HttpServletResponse response) {
        init();
        try (FileInputStream mtUrlIS = new FileInputStream(mtUrl.toFile());
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int k;
            while ((k = mtUrlIS.read()) != -1) {
                baos.write(k);
            }
            String mtInfoStr = baos.toString("utf-8");
            //JSONObject jsonObject = JSONObject.parseObject(str);
            Map<String, String> urlMap = parseUrl(mtInfoStr);
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
                if (CollectionUtils.isNotEmpty(cate.getSpuIds())) {
                    spuIds.addAll(cate.getSpuIds());
                }
                if (CollectionUtils.isNotEmpty(cate.getChildDishCategories())) {
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
            XSSFWorkbook workbook = export(response, infos);
            workbook.write(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @GetMapping("/getMtZHCT")
    public void getMtZHCT(HttpServletResponse response) {
        init();
        try (FileInputStream is = new FileInputStream(mtzhJson.toFile());
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int i;
            while ((i = is.read()) != -1) {
                baos.write(i);
            }
            String mtZHStr = baos.toString("utf-8");
            List<BaseInfo> infos = new ArrayList<>();
            JSONObject responseBody = JSONObject.parseObject(mtZHStr);
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

            XSSFWorkbook export = export(response, infos);
            export.write(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }




/*
    @PostMapping("/toBaseInfo")
    public void toBaseInfo(@RequestBody QueryBody queryBody, HttpServletResponse response) {
        try {
            init();
            //String spuss = HttpUtils.get("https://rms.meituan.com/diancan/menu/api/pageSpuInfo?mtShopId=601078148&tableNum=24169569&reserveMode=0&peopleCount=0&selectedTime=0&pageNum=1&timestamp=1688711604669&tenantId=11648303&brandId=&restaurantViewId=&shopCache=&sign=8e9cc70ccc85c645cc7eda9a679754a4");
            //String menus = HttpUtils.get("https://rms.meituan.com/diancan/menu/api/pageSpuInfo?qrcode=https%253A%252F%252Frms.meituan.com%252Fdiancan%252F14%252F1yh2YpgJBnT&pageNum=1&timestamp=1688707191291&sign=8e9cc70ccc85c645cc7eda9a679754a4https://rms.meituan.com/diancan/menu/api/pageSpuInfo?qrcode=https%253A%252F%252Frms.meituan.com%252Fdiancan%252F14%252F1yh2YpgJBnT&pageNum=2&timestamp=1688707191291&sign=8e9cc70ccc85c645cc7eda9a679754a4");
            String menus = HttpUtils.get(queryBody.getMenus());
            String spuss = HttpUtils.get(queryBody.getSpuss());
            List<BaseInfo> infos = new ArrayList<>();

            JSONObject spuString = JSONObject.parseObject(spuss);

            Map<String, Object> spuMap = spuString.getJSONObject("data").getJSONObject("spuDetail");

            JSONObject menuString = JSONObject.parseObject(menus);

            JSONArray categories = menuString.getJSONObject("data").getJSONArray("categories");
            for (Object category : categories) {
                Category cate = JSONObject.parseObject(JSONObject.toJSONString(category), Category.class);
                List<String> spuIds = cate.getSpuIds();
                for (String spuId : spuIds) {
                    Map<String, Object> spu = (Map<String, Object>) spuMap.get(spuId);
                    String name = (String) spu.get("spuName");
                    String price = String.valueOf(spu.get("currentPrice"));
                    BaseInfo baseInfo = BaseInfo.builder().category(cate.getCategoryName()).name(name).price(price)
                            .specification("1人份").nums("1").build();
                    infos.add(baseInfo);
                }
            }
            //2010格式设置
            //response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            //2003格式设置
            XSSFWorkbook export = export(response, infos);
            export.write(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

    @GetMapping("/getEleData")
    public void get(HttpServletResponse response) {
        init();
        try (FileInputStream is = new FileInputStream(json.toFile());
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int i;
            while ((i = is.read()) != -1) {
                baos.write(i);
            }
            String str = baos.toString("utf-8");
            List<BaseInfo> infos = new ArrayList<>();
            JSONObject responseBody = JSONObject.parseObject(str);
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
                    //List<EleSpecFood> specFoods = eleBaseInfo.getSpecFoods();

                    /**
                     *
                     * 忽略判断大小份
                     * if (CollectionUtils.isNotEmpty(specFoods) && specFoods.size() > 1) {
                     *                         for (EleSpecFood specFood : specFoods) {
                     *                             List<EleSpec> spec = specFood.getSpecs();
                     *                             if (CollectionUtils.isEmpty(spec)) {
                     *                                 continue;
                     *                             }
                     *                             String value = spec.get(0).getValue();
                     *                             String realName = eleBaseInfo.getName().concat("(").concat(value).concat(")");
                     *                             String price = specFood.getPrice();
                     *                             BaseInfo info = BaseInfo.builder().category(category).name(realName).price(price)
                     *                                     .specification("1人份").nums("1").build();
                     *                             infos.add(info);
                     *                         }
                     *                     }
                     *
                     */
                    BaseInfo info = BaseInfo.builder().category(category).name(eleBaseInfo.getName())
                            .price(StringUtils.isBlank(eleBaseInfo.getOriginPrice()) ? eleBaseInfo.getPrice() : eleBaseInfo.getOriginPrice())
                            .specification("1人份").nums("1").build();
                    infos.add(info);

                    //intercept(eleBaseInfo.getName())
                }
            }
            //过滤掉infos中name相同的
            infos = infos.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(BaseInfo::getName))), ArrayList::new));
            //按照category排序
            infos.sort(Comparator.comparing(BaseInfo::getCategory));
            XSSFWorkbook export = export(response, infos);
            export.write(response.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /*private void downloadPic(List<BaseInfo> infos) {
        String preUrl = "https://cube.elemecdn.com/";
        String afterurl = "?x-oss-process=image/resize,m_fill,w_204,h_204/format,webp/quality,q_75";
        Set<String> urls = infos.stream().
                filter(info -> StringUtils.isNotBlank(info.getPicUrl()))
                .map(info -> preUrl + info.getPicUrl() + afterurl)
                .collect(Collectors.toSet());
    }*/
    @GetMapping("/getShouyinTai")
    public void getShouYin(HttpServletResponse response) {
        init();
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
                if (CollectionUtils.isNotEmpty(cateDishList)) {
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
     * 提取字符串中所有的汉字
     */
  /*  public String intercept(String str) throws Exception {
        String regex = "[\u4E00-\u9FA5]";//汉字
        Matcher matcher = Pattern.compile(regex).matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            sb.append(matcher.group());
        }

        return sb.toString();
    }*/
    private XSSFWorkbook export(HttpServletResponse response, List<BaseInfo> infos) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.addHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode("meun_download.xls", "utf-8"));
        FileInputStream inputStream = new FileInputStream(templatePath.toFile());
        XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
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


    private void init() {
        try {
            String property = System.getProperty("user.dir");
            File file = new File(property);
            String absolutePath = file.getParentFile().getAbsolutePath();


            //log.error("absolutePath:{}", absolutePath);
            Path path = Paths.get(absolutePath + "/templates");
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
            templatePath = Paths.get(path + "/template.xlsx");
            if (!Files.exists(templatePath)) {
                Files.createFile(templatePath);
            }
            json = Paths.get(path + "/饿了么数据.json");
            if (!Files.exists(json)) {
                Files.createFile(json);
            }
            mtzhJson = Paths.get(path + "/美团智慧数据.json");
            if (!Files.exists(mtzhJson)) {
                Files.createFile(mtzhJson);
            }

            mtUrl = Paths.get(path + "/美团url.json");
            if (!Files.exists(mtUrl)) {
                Files.createFile(mtUrl);
            }
            sytJson = Paths.get(path + "/收银台数据.json");
            if (!Files.exists(sytJson)) {
                Files.createFile(sytJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
