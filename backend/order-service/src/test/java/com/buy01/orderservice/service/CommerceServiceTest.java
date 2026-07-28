package com.buy01.orderservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.buy01.orderservice.client.ProductClient;
import com.buy01.orderservice.dto.Requests.CheckoutRequest;
import com.buy01.orderservice.model.*;
import com.buy01.orderservice.repository.*;
import com.buy01.orderservice.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommerceServiceTest {
    private CartRepository carts;
    private OrderRepository orders;
    private ProductClient products;
    private CommerceService service;

    @BeforeEach void setup() {
        carts=mock(CartRepository.class);orders=mock(OrderRepository.class);products=mock(ProductClient.class);
        service=new CommerceService(carts,orders,products);
    }

    @Test void checkoutSnapshotsTotalAndClearsCart() {
        Cart cart=new Cart();cart.setUserId("buyer");cart.setItems(new ArrayList<>(List.of(new CartItem("p1",2))));
        when(carts.findByUserId("buyer")).thenReturn(Optional.of(cart));
        when(products.reserve("Bearer token","p1",2)).thenReturn(new ProductClient.ProductSnapshot("p1","Keyboard","Electronics",new BigDecimal("25.50"),2,"seller"));
        when(orders.save(any())).thenAnswer(invocation->{Order o=invocation.getArgument(0);o.setId("order-1");return o;});

        Order result=service.checkout("buyer","Bearer token",new CheckoutRequest("Manama","PAY_ON_DELIVERY"));

        assertEquals(new BigDecimal("51.00"),result.getTotal());
        assertEquals("PAY_ON_DELIVERY",result.getPaymentMethod());
        assertEquals(OrderStatus.PENDING,result.getStatus());
        assertTrue(cart.getItems().isEmpty());
        verify(carts).save(cart);
    }

    @Test void sellerCannotSkipOrderStatus() {
        Order order=new Order();order.setId("o1");order.setBuyerId("buyer");order.setSellerIds(List.of("seller"));
        order.setStatus(OrderStatus.PENDING);order.setCreatedAt(Instant.now());
        when(orders.findById("o1")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,()->service.status(
                new AuthenticatedUser("seller","seller@test","SELLER"),"Bearer token","o1",OrderStatus.DELIVERED));
        verify(orders,never()).save(any());
    }

    @Test void relatedSellerCanConfirmPendingOrder() {
        Order order=new Order();order.setId("o1");order.setBuyerId("buyer");order.setSellerIds(List.of("seller"));
        order.setStatus(OrderStatus.PENDING);order.setCreatedAt(Instant.now());
        when(orders.findById("o1")).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);

        Order result=service.status(new AuthenticatedUser("seller","seller@test","SELLER"),
                "Bearer token","o1",OrderStatus.CONFIRMED);

        assertEquals(OrderStatus.CONFIRMED,result.getStatus());
        verify(orders).save(order);
    }

    @Test void unrelatedUserCannotReadOrder() {
        Order order=new Order();order.setBuyerId("buyer");order.setSellerIds(List.of("seller"));
        when(orders.findById("o1")).thenReturn(Optional.of(order));
        assertThrows(SecurityException.class,()->service.one(
                new AuthenticatedUser("stranger","x@test","CLIENT"),"o1"));
    }

    @Test void buyerCannotChangeOrderStatus() {
        Order order=new Order();order.setId("o1");order.setBuyerId("buyer");order.setSellerIds(List.of("seller"));
        order.setStatus(OrderStatus.PENDING);
        assertThrows(SecurityException.class,()->service.status(
                new AuthenticatedUser("buyer","buyer@test","CLIENT"),"Bearer token","o1",OrderStatus.CONFIRMED));
        verify(orders,never()).save(any());
    }

    @Test void unrelatedSellerCannotChangeOrderStatus() {
        Order order=new Order();order.setId("o1");order.setBuyerId("buyer");order.setSellerIds(List.of("seller"));
        order.setStatus(OrderStatus.PENDING);
        when(orders.findById("o1")).thenReturn(Optional.of(order));
        assertThrows(SecurityException.class,()->service.status(
                new AuthenticatedUser("other-seller","other@test","SELLER"),"Bearer token","o1",OrderStatus.CONFIRMED));
        verify(orders,never()).save(any());
    }

    @Test void checkoutReleasesAlreadyReservedStockWhenAnotherReservationFails() {
        Cart cart=new Cart();cart.setUserId("buyer");cart.setItems(new ArrayList<>(List.of(new CartItem("p1",1),new CartItem("p2",1))));
        when(carts.findByUserId("buyer")).thenReturn(Optional.of(cart));
        when(products.reserve("Bearer token","p1",1)).thenReturn(new ProductClient.ProductSnapshot("p1","Keyboard","Electronics",new BigDecimal("25"),2,"seller"));
        when(products.reserve("Bearer token","p2",1)).thenThrow(new IllegalStateException("Insufficient stock"));

        assertThrows(IllegalStateException.class,()->service.checkout("buyer","Bearer token",new CheckoutRequest("Manama","PAY_ON_DELIVERY")));

        verify(products).release("Bearer token","p1",1);
        verify(orders,never()).save(any());
    }

    @Test void sellerAnalyticsExposeStableProductKeyAndValueFields() {
        OrderItem item=new OrderItem();item.setSellerId("seller");item.setProductName("Keyboard");
        item.setCategory("Electronics");item.setUnitPrice(new BigDecimal("25"));item.setQuantity(3);
        Order order=new Order();order.setBuyerId("buyer");order.setSellerIds(List.of("seller"));
        order.setItems(List.of(item));order.setStatus(OrderStatus.CONFIRMED);order.setCreatedAt(Instant.now());
        when(orders.findBySellerIdsContainingOrderByCreatedAtDesc("seller")).thenReturn(List.of(order));

        Map<String,Object> analytics=service.analytics(
                new AuthenticatedUser("seller","seller@test","SELLER"));

        List<?> topProducts=(List<?>)analytics.get("topProducts");
        assertFalse(topProducts.isEmpty());
        Map<?,?> bestSeller=(Map<?,?>)topProducts.get(0);
        assertEquals("Keyboard",bestSeller.get("key"));
        assertEquals(3,bestSeller.get("value"));
    }
}
