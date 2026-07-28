package com.buy01.orderservice.service;
import com.buy01.orderservice.client.ProductClient;
import com.buy01.orderservice.dto.Requests.*;
import com.buy01.orderservice.model.*;
import com.buy01.orderservice.repository.*;
import com.buy01.orderservice.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class CommerceService {
    private final CartRepository carts; private final OrderRepository orders; private final ProductClient products;
    public CommerceService(CartRepository c,OrderRepository o,ProductClient p){carts=c;orders=o;products=p;}
    public Cart cart(String uid){return carts.findByUserId(uid).orElseGet(()->empty(uid));}
    public Cart put(String uid,CartItemRequest r){
        var p=products.get(r.productId()); if(r.quantity()>p.quantity())throw new IllegalStateException("Requested quantity exceeds stock");
        Cart c=cart(uid);c.getItems().removeIf(i->i.getProductId().equals(r.productId()));c.getItems().add(new CartItem(r.productId(),r.quantity()));c.setUpdatedAt(Instant.now());return carts.save(c);
    }
    public Cart remove(String uid,String pid){Cart c=cart(uid);c.getItems().removeIf(i->i.getProductId().equals(pid));c.setUpdatedAt(Instant.now());return carts.save(c);}
    public Order checkout(String uid,String authorization,CheckoutRequest r){
        if(!"PAY_ON_DELIVERY".equals(r.paymentMethod()))throw new IllegalArgumentException("Only PAY_ON_DELIVERY is supported");
        Cart c=cart(uid);if(c.getItems().isEmpty())throw new IllegalStateException("Cart is empty");
        List<OrderItem> items=new ArrayList<>();
        List<CartItem> reserved=new ArrayList<>();
        try {
            for(CartItem ci:c.getItems()){var p=products.reserve(authorization,ci.getProductId(),ci.getQuantity());
                if(p==null)throw new IllegalStateException("Could not reserve stock");
                reserved.add(ci);
                OrderItem i=new OrderItem();i.setProductId(p.id());i.setSellerId(p.sellerId());i.setProductName(p.name());i.setCategory(p.category()==null?"General":p.category());i.setUnitPrice(p.price());i.setQuantity(ci.getQuantity());items.add(i);}
        } catch (RuntimeException exception) {
            reserved.forEach(item -> products.release(authorization,item.getProductId(),item.getQuantity()));
            throw exception;
        }
        Instant now=Instant.now();Order o=new Order();o.setBuyerId(uid);o.setItems(items);o.setSellerIds(items.stream().map(OrderItem::getSellerId).distinct().toList());
        o.setAddress(r.address().trim());o.setPaymentMethod("PAY_ON_DELIVERY");o.setStatus(OrderStatus.PENDING);
        o.setTotal(items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add));o.setCreatedAt(now);o.setUpdatedAt(now);
        try {
            Order saved=orders.save(o);c.getItems().clear();c.setUpdatedAt(now);carts.save(c);return saved;
        } catch (RuntimeException exception) {
            reserved.forEach(item -> products.release(authorization,item.getProductId(),item.getQuantity()));
            throw exception;
        }
    }
    public List<Order> list(AuthenticatedUser u,String query,OrderStatus status){
        return list(u,query,status,null,null);
    }
    public List<Order> list(AuthenticatedUser u,String query,OrderStatus status,LocalDate from,LocalDate to){
        List<Order> source=u.isSeller()?orders.findBySellerIdsContainingOrderByCreatedAtDesc(u.userId()):orders.findByBuyerIdOrderByCreatedAtDesc(u.userId());
        String q=query==null?"":query.trim().toLowerCase();
        return source.stream().filter(o->status==null||o.getStatus()==status)
                .filter(o->from==null||!o.getCreatedAt().isBefore(from.atStartOfDay().toInstant(ZoneOffset.UTC)))
                .filter(o->to==null||o.getCreatedAt().isBefore(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
                .filter(o->q.isEmpty()||o.getId().toLowerCase().contains(q)||o.getItems().stream().anyMatch(i->i.getProductName().toLowerCase().contains(q))).toList();
    }
    public Order one(AuthenticatedUser u,String id){Order o=orders.findById(id).orElseThrow(()->new NoSuchElementException("Order not found"));
        if(!o.getBuyerId().equals(u.userId())&&!o.getSellerIds().contains(u.userId()))throw new SecurityException("Order access denied");return o;}
    public Order cancel(String uid,String authorization,String id){Order o=one(new AuthenticatedUser(uid,"","CLIENT"),id);if(o.getStatus()!=OrderStatus.PENDING&&o.getStatus()!=OrderStatus.CONFIRMED)throw new IllegalStateException("Order can no longer be cancelled");releaseOrderStock(authorization,o);o.setStatus(OrderStatus.CANCELLED);o.setUpdatedAt(Instant.now());return orders.save(o);}
    public void removeOrder(String uid,String id){Order o=one(new AuthenticatedUser(uid,"","CLIENT"),id);if(o.getStatus()!=OrderStatus.CANCELLED)throw new IllegalStateException("Only cancelled orders can be removed");orders.delete(o);}
    public Order redo(String uid,String id){Order o=one(new AuthenticatedUser(uid,"","CLIENT"),id);Cart c=cart(uid);c.setItems(o.getItems().stream().map(i->new CartItem(i.getProductId(),i.getQuantity())).toList());c.setUpdatedAt(Instant.now());carts.save(c);return o;}
    public Order status(AuthenticatedUser u,String authorization,String id,OrderStatus next){Order o=one(u,id);Map<OrderStatus,List<OrderStatus>> allowed=Map.of(OrderStatus.PENDING,List.of(OrderStatus.CONFIRMED,OrderStatus.CANCELLED),OrderStatus.CONFIRMED,List.of(OrderStatus.SHIPPED,OrderStatus.CANCELLED),OrderStatus.SHIPPED,List.of(OrderStatus.DELIVERED));if(!allowed.getOrDefault(o.getStatus(),List.of()).contains(next))throw new IllegalStateException("Invalid status transition");if(next==OrderStatus.CANCELLED)releaseOrderStock(authorization,o);o.setStatus(next);o.setUpdatedAt(Instant.now());return orders.save(o);}
    public Map<String,Object> analytics(AuthenticatedUser u){List<Order> valid=list(u,null,null).stream().filter(o->o.getStatus()!=OrderStatus.CANCELLED).toList();Map<String,Integer> units=new HashMap<>();Map<String,BigDecimal> categories=new HashMap<>();BigDecimal total=BigDecimal.ZERO;
        for(Order o:valid)for(OrderItem i:o.getItems()){if(u.isSeller()&&!i.getSellerId().equals(u.userId()))continue;total=total.add(i.subtotal());units.merge(i.getProductName(),i.getQuantity(),Integer::sum);categories.merge(i.getCategory()==null?"General":i.getCategory(),i.subtotal(),BigDecimal::add);}
        Map<String,Object> result=new HashMap<>();result.put(u.isSeller()?"revenue":"totalSpent",total);result.put("orderCount",valid.size());result.put("topProducts",units.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5).toList());result.put("topCategories",categories.entrySet().stream().sorted(Map.Entry.<String,BigDecimal>comparingByValue().reversed()).limit(5).toList());return result;}
    private Cart empty(String uid){Cart c=new Cart();c.setUserId(uid);c.setUpdatedAt(Instant.now());return c;}
    private void releaseOrderStock(String authorization,Order order){order.getItems().forEach(item->products.release(authorization,item.getProductId(),item.getQuantity()));}
}
