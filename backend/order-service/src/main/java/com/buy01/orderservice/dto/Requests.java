package com.buy01.orderservice.dto;
import com.buy01.orderservice.model.OrderStatus;
import jakarta.validation.constraints.*;
public final class Requests {
    private Requests(){}
    public record CartItemRequest(@NotBlank String productId,@Min(1) @Max(99) int quantity){}
    public record CheckoutRequest(@NotBlank String address,@NotBlank String paymentMethod){}
    public record StatusRequest(@NotNull OrderStatus status){}
}
