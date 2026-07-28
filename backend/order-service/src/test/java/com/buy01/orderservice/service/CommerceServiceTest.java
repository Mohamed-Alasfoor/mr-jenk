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

    @Test void unrelatedUserCannotReadOrder() {
        Order order=new Order();order.setBuyerId("buyer");order.setSellerIds(List.of("seller"));
        when(orders.findById("o1")).thenReturn(Optional.of(order));
        assertThrows(SecurityException.class,()->service.one(
                new AuthenticatedUser("stranger","x@test","CLIENT"),"o1"));
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
}
