package com.buy01.orderservice.client;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
@Component
public class ProductClient {
    private final RestClient client;
    public ProductClient(RestClient.Builder builder,@Value("${services.product.base-url}") String url){client=builder.baseUrl(url).build();}
    public ProductSnapshot get(String id){
        ProductSnapshot value=client.get().uri("/products/{id}",id).retrieve().body(ProductSnapshot.class);
        if(value==null)throw new IllegalArgumentException("Product not found: "+id); return value;
    }
    public ProductSnapshot reserve(String authorization,String id,int quantity){
        return client.post().uri("/internal/products/{id}/stock/reserve",id)
                .header(HttpHeaders.AUTHORIZATION,authorization).body(new StockRequest(quantity))
                .retrieve().body(ProductSnapshot.class);
    }
    public void release(String authorization,String id,int quantity){
        client.post().uri("/internal/products/{id}/stock/release",id)
                .header(HttpHeaders.AUTHORIZATION,authorization).body(new StockRequest(quantity)).retrieve().toBodilessEntity();
    }
    private record StockRequest(int quantity){}
    public record ProductSnapshot(String id,String name,String category,BigDecimal price,int quantity,String sellerId){}
}
