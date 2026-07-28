package com.buy01.productservice.service;

import com.buy01.productservice.dto.*;
import com.buy01.productservice.model.Product;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {
    private final MongoTemplate mongo;
    public ProductSearchService(MongoTemplate mongo){this.mongo=mongo;}

    public ProductSearchResponse search(String keyword,String category,BigDecimal minPrice,BigDecimal maxPrice,
                                        String availability,String sort,int page,int size){
        int safePage=Math.max(0,page);int safeSize=Math.min(Math.max(size,1),50);
        List<Criteria> filters=new ArrayList<>();
        if(keyword!=null&&!keyword.isBlank()){
            String value=Pattern.quote(keyword.trim());
            filters.add(new Criteria().orOperator(Criteria.where("name").regex(value,"i"),Criteria.where("description").regex(value,"i")));
        }
        if(category!=null&&!category.isBlank())filters.add(Criteria.where("category").is(category));
        if(minPrice!=null)filters.add(Criteria.where("price").gte(minPrice));
        if(maxPrice!=null)filters.add(Criteria.where("price").lte(maxPrice));
        if("available".equals(availability))filters.add(Criteria.where("quantity").gt(0));
        if("low-stock".equals(availability))filters.add(Criteria.where("quantity").gt(0).lt(5));
        if("sold-out".equals(availability))filters.add(Criteria.where("quantity").is(0));
        Query query=new Query();if(!filters.isEmpty())query.addCriteria(new Criteria().andOperator(filters));
        long total=mongo.count(query,Product.class);
        Sort ordering=switch(sort==null?"newest":sort){
            case "oldest"->Sort.by("createdAt").ascending();case "price-low"->Sort.by("price").ascending();
            case "price-high"->Sort.by("price").descending();case "stock"->Sort.by("quantity").descending();
            default->Sort.by("createdAt").descending();};
        query.with(PageRequest.of(safePage,safeSize,ordering));
        List<ProductResponse> items=mongo.find(query,Product.class).stream().map(this::map).toList();
        List<Product> all=mongo.findAll(Product.class);
        List<String> categories=all.stream().map(p->p.getCategory()==null?"General":p.getCategory()).distinct().sorted().toList();
        BigDecimal catalogMin=all.stream().map(Product::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal catalogMax=all.stream().map(Product::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        return new ProductSearchResponse(items,total,(int)Math.ceil((double)total/safeSize),safePage,safeSize,categories,catalogMin,catalogMax);
    }
    private ProductResponse map(Product p){return new ProductResponse(p.getId(),p.getName(),p.getDescription(),p.getCategory()==null?"General":p.getCategory(),p.getPrice(),p.getQuantity(),p.getSellerId(),p.getImageUrls()==null?List.of():p.getImageUrls(),p.getCreatedAt(),p.getUpdatedAt());}
}
