package com.buy01.orderservice.controller;
import com.buy01.orderservice.dto.Requests.*;
import com.buy01.orderservice.model.*;
import com.buy01.orderservice.security.AuthenticatedUser;
import com.buy01.orderservice.service.CommerceService;
import jakarta.validation.Valid;
import java.util.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
@RestController
public class CommerceController {
    private final CommerceService service; public CommerceController(CommerceService s){service=s;}
    @GetMapping("/cart") @PreAuthorize("hasRole('CLIENT')") public Cart cart(Authentication a){return service.cart(user(a).userId());}
    @PutMapping("/cart/items") @PreAuthorize("hasRole('CLIENT')") public Cart put(Authentication a,@Valid @RequestBody CartItemRequest r){return service.put(user(a).userId(),r);}
    @DeleteMapping("/cart/items/{id}") @PreAuthorize("hasRole('CLIENT')") public Cart remove(Authentication a,@PathVariable String id){return service.remove(user(a).userId(),id);}
    @PostMapping("/orders") @PreAuthorize("hasRole('CLIENT')") public Order checkout(Authentication a,@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,@Valid @RequestBody CheckoutRequest r){return service.checkout(user(a).userId(),authorization,r);}
    @GetMapping("/orders") public List<Order> list(Authentication a,@RequestParam(required=false) String query,@RequestParam(required=false) OrderStatus status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return service.list(user(a),query,status,from,to);}
    @GetMapping("/orders/{id}") public Order one(Authentication a,@PathVariable String id){return service.one(user(a),id);}
    @PostMapping("/orders/{id}/cancel") @PreAuthorize("hasRole('CLIENT')") public Order cancel(Authentication a,@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,@PathVariable String id){return service.cancel(user(a).userId(),authorization,id);}
    @PostMapping("/orders/{id}/redo") @PreAuthorize("hasRole('CLIENT')") public Order redo(Authentication a,@PathVariable String id){return service.redo(user(a).userId(),id);}
    @DeleteMapping("/orders/{id}") @PreAuthorize("hasRole('CLIENT')") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeOrder(Authentication a,@PathVariable String id){service.removeOrder(user(a).userId(),id);}
    @PatchMapping("/orders/{id}/status") @PreAuthorize("hasRole('SELLER')") public Order status(Authentication a,@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,@PathVariable String id,@Valid @RequestBody StatusRequest r){return service.status(user(a),authorization,id,r.status());}
    @GetMapping("/analytics/me") public Map<String,Object> analytics(Authentication a){return service.analytics(user(a));}
    private AuthenticatedUser user(Authentication a){return (AuthenticatedUser)a.getPrincipal();}
}
