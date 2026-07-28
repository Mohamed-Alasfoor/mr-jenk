package com.buy01.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.buy01.productservice.model.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class ProductSearchServiceTest {
    @Test void appliesFacetsAndReturnsPaginationMetadata() {
        MongoTemplate mongo=mock(MongoTemplate.class);
        Product product=new Product();product.setId("p1");product.setName("Mechanical Keyboard");product.setDescription("Wireless");
        product.setCategory("Electronics");product.setPrice(new BigDecimal("55"));product.setQuantity(3);
        product.setSellerId("seller");product.setCreatedAt(Instant.now());product.setUpdatedAt(Instant.now());
        when(mongo.count(any(Query.class),eq(Product.class))).thenReturn(1L);
        when(mongo.find(any(Query.class),eq(Product.class))).thenReturn(List.of(product));
        when(mongo.findAll(Product.class)).thenReturn(List.of(product));

        var result=new ProductSearchService(mongo).search("keyboard","Electronics",BigDecimal.TEN,
                new BigDecimal("100"),"low-stock","price-low",0,12);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).extracting("name").containsExactly("Mechanical Keyboard");
        assertThat(result.categories()).containsExactly("Electronics");
        ArgumentCaptor<Query> query=ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(),eq(Product.class));
        assertThat(query.getValue().getLimit()).isEqualTo(12);
    }
}
