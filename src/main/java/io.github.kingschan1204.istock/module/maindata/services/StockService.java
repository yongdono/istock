package io.github.kingschan1204.istock.module.maindata.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.kingschan1204.istock.common.util.stock.StockSpider;
import io.github.kingschan1204.istock.module.maindata.po.*;
import io.github.kingschan1204.istock.module.maindata.repository.StockHisDividendRepository;
import io.github.kingschan1204.istock.module.maindata.repository.StockHisPbRepository;
import io.github.kingschan1204.istock.module.maindata.repository.StockHisPeRepository;
import io.github.kingschan1204.istock.module.maindata.repository.StockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stock service
 * @author chenguoxiang
 * @create 2018-03-27 10:27
 **/
@Service
public class StockService {
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockHisDividendRepository stockHisDividendRepository;
    @Autowired
    private StockHisRoeService stockHisRoeService;
    @Autowired
    private StockHisPbRepository stockHisPbRepository;
    @Autowired
    private StockHisPeRepository stockHisPeRepository;
    @Autowired
    private StockSpider spider;
    @Autowired
    private MongoTemplate template;

    /**
     * add stock code
     *
     * @param codes
     * @throws Exception
     */
    public void addStock(String... codes) throws Exception {
        JSONArray jsons = spider.getStockPrice(codes);
        for (int i = 0; i < jsons.size(); i++) {
            JSONObject json = jsons.getJSONObject(i);
            String scode=json.getString("code");
            JSONObject info=spider.getStockInfo(scode);
            //"date":"2017-07-07","code":"600519","year":"2016年报","executeDate":"2017-07-01","remark":"10派67.87元(含税)","percent":1.44}
            JSONArray dividends=spider.getHistoryDividendRate(scode);
            JSONObject dividend;
            String date="";
            Double percent=0D;
            if(null!=dividends&&dividends.size()>0){
                for (int j = 0; j < dividends.size(); j++) {
                    if(dividends.getJSONObject(j).getDouble("percent")>0){
                        percent=dividends.getJSONObject(j).getDoubleValue("percent");
                        date=dividends.getJSONObject(j).getString("date");
                        break;
                    }
                }
                //save dividend
                List<StockHisDividend> stockHisDividendList = JSONArray.parseArray(dividends.toJSONString(),StockHisDividend.class);
                stockHisDividendRepository.save(stockHisDividendList);
            }
            json.put("dividend",percent);
            json.put("dividendDate",date);
            json.putAll(info);
            // his roe
            stockHisRoeService.addStockHisRoe(scode);
            //his pb
            addStockHisPb(scode);
            //his pe
            addStockHisPe(scode);
        }
        List<Stock> list = JSON.parseArray(jsons.toJSONString(), Stock.class);
        stockRepository.save(list);
    }


    /**
     * 增加历史pe
     * @param code
     * @throws Exception
     */
    public void addStockHisPe(String code)throws Exception{
        JSONArray jsons=spider.getHistoryPE(StockSpider.formatStockCode(code));
        List<StockHisPe> lis = JSON.parseArray(jsons.toJSONString(),StockHisPe.class);
        stockHisPeRepository.save(lis);
    }

    /**
     * 增加历史pb
     * @param code
     * @throws Exception
     */
    public void addStockHisPb(String code)throws Exception{
        JSONArray jsons=spider.getHistoryPB(StockSpider.formatStockCode(code));
        List<StockHisPb> lis = JSON.parseArray(jsons.toJSONString(),StockHisPb.class);
        stockHisPbRepository.save(lis);
    }


    public String queryStock(int pageindex, int pagesize, final String pcode, String orderfidld, String psort){
        Query query = new Query();
        Optional<String> code =Optional.ofNullable(pcode);
        if (code.isPresent()){
            if(pcode.matches("\\d{6}")){
                query.addCriteria(Criteria.where("_id").is(pcode));
            }
        }
        //记录总数
        Long total=template.count(query,Stock.class);
        //分页
        query.skip((pageindex-1)*pagesize).limit(pagesize);
        //排序
        List<Sort.Order> orders = new ArrayList<Sort.Order>();  //排序
        orders.add(new Sort.Order(
                psort.equalsIgnoreCase("asc")?Sort.Direction.ASC:Sort.Direction.DESC
                ,orderfidld));
        Sort sort = new Sort(orders);
        query.with(sort);
        //code
        List<Stock> list =template.find(query,Stock.class);
        JSONArray jsons = JSONArray.parseArray(JSONArray.toJSONString(list));
        JSONObject temp;
        for (int i = 0; i <jsons.size() ; i++) {
            temp =jsons.getJSONObject(i);
            temp.put("fluctuate",temp.getString("fluctuate")+"%");
            temp.put("roe",temp.getString("roe")+"%");
            temp.put("totalValue",temp.getString("totalValue")+"亿");
            temp.put("dividend",temp.getString("dividend")+"%");
        }

        JSONObject data= new JSONObject();
        data.put("rows",jsons);
        data.put("total",(total-1)/pagesize);//有多少页
        data.put("records",total);// 总共有多少条记录
        data.put("page",pageindex);
        return data.toJSONString();
    }


    /**
     *
     * @param code
     * @return
     */
    public String getStockDividend(String code){
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(code));
        //排序
        List<Sort.Order> orders = new ArrayList<Sort.Order>();  //排序
        orders.add(new Sort.Order(Sort.Direction.ASC,"title"));
        Sort sort = new Sort(orders);
        query.with(sort);
        //code
        List<StockHisDividend> list =template.find(query,StockHisDividend.class);
        StringBuffer year = new StringBuffer();
        StringBuffer percent = new StringBuffer();
        list.stream().forEach(item ->{
            year.append("'").append(item.getTitle()).append("',");
            if(item.getPercent()>0){
                percent.append(item.getPercent()).append(",");
            }else{
                percent.append("0,");
            }
        });
        return  String.format("%s|%s",year.toString().replaceAll("\\,$",""),
                percent.toString().replaceAll("\\,$","")
        );
    }


    public String getStockHisRoe(String code){
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(code));
        //排序
        List<Sort.Order> orders = new ArrayList<Sort.Order>();  //排序
        orders.add(new Sort.Order(Sort.Direction.ASC,"year"));
        Sort sort = new Sort(orders);
        query.with(sort);
        //code
        List<StockHisRoe> list =template.find(query,StockHisRoe.class);
        StringBuffer year = new StringBuffer();
        StringBuffer percent = new StringBuffer();
        list.stream().forEach(item ->{
            year.append("'").append(item.getYear()).append("',");
            if(item.getRoe()>0){
                percent.append(item.getRoe()).append(",");
            }else{
                percent.append("0,");
            }

        });
        return  String.format("%s|%s",year.toString().replaceAll("\\,$",""),
                percent.toString().replaceAll("\\,$","")
        );
    }


    public String getStockHisPb(String code){
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(code));
        //排序
        List<Sort.Order> orders = new ArrayList<Sort.Order>();  //排序
        orders.add(new Sort.Order(Sort.Direction.ASC,"date"));
        Sort sort = new Sort(orders);
        query.with(sort);
        //code
        List<StockHisPb> list =template.find(query,StockHisPb.class);
        StringBuffer year = new StringBuffer();
        StringBuffer pb = new StringBuffer();
        list.stream().forEach(item ->{
            year.append("'").append(item.getDate()).append("',");
            if(item.getPb()>0){
                pb.append(item.getPb()).append(",");
            }else{
                pb.append("0,");
            }

        });
        return  String.format("%s|%s",year.toString().replaceAll("\\,$",""),
                pb.toString().replaceAll("\\,$","")
        );
    }



    public String getStockHisPe(String code){
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(code));
        //排序
        List<Sort.Order> orders = new ArrayList<Sort.Order>();  //排序
        orders.add(new Sort.Order(Sort.Direction.ASC,"date"));
        Sort sort = new Sort(orders);
        query.with(sort);
        //code
        List<StockHisPe> list =template.find(query,StockHisPe.class);
        StringBuffer year = new StringBuffer();
        StringBuffer pe = new StringBuffer();
        list.stream().forEach(item ->{
            year.append("'").append(item.getDate()).append("',");
            if(item.getPe()>0){
                pe.append(item.getPe()).append(",");
            }else{
                pe.append("0,");
            }

        });
        return  String.format("%s|%s",year.toString().replaceAll("\\,$",""),
                pe.toString().replaceAll("\\,$","")
        );
    }
}