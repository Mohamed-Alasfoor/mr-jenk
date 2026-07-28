package com.buy01.productservice.config;

import java.util.List;
import org.bson.BsonType;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import static com.mongodb.client.model.Filters.type;

@Component
public class ProductPriceMigration implements ApplicationRunner {
    private final MongoTemplate mongoTemplate;

    public ProductPriceMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Document conversion = new Document("$convert",
                new Document("input", "$price")
                        .append("to", "decimal")
                        .append("onError", "$price")
                        .append("onNull", "$price"));
        mongoTemplate.getCollection("products").updateMany(
                type("price", BsonType.STRING),
                List.of(new Document("$set", new Document("price", conversion)))
        );
    }
}
