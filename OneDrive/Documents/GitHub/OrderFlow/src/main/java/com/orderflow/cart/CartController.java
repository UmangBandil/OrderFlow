package com.orderflow.cart;

import com.orderflow.common.security.AuthFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Customer shopping cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    public record AddItemRequest(@NotNull Long productId, @Min(1) int quantity) {}

    public record UpdateItemRequest(@Min(1) int quantity) {}

    public record CartItemDto(Long itemId, Long productId, int quantity) {

        static CartItemDto from(CartItem item) {
            return new CartItemDto(item.getId(), item.getProductId(), item.getQuantity());
        }
    }

    public record CartDto(Long cartId, List<CartItemDto> items) {}

    @GetMapping
    @Operation(summary = "Get the current user's cart")
    public CartDto getCart() {
        Long userId = AuthFacade.currentUserId();
        Cart cart = cartService.getOrCreateCart(userId);
        return new CartDto(
                cart.getId(), cart.getItems().stream().map(CartItemDto::from).toList());
    }

    @PostMapping("/items")
    @Operation(summary = "Add an item to the cart")
    public ResponseEntity<CartDto> addItem(@Valid @RequestBody AddItemRequest request) {
        Long userId = AuthFacade.currentUserId();
        cartService.addItem(userId, request.productId(), request.quantity());
        Cart cart = cartService.getOrCreateCart(userId);
        return ResponseEntity.ok(new CartDto(
                cart.getId(), cart.getItems().stream().map(CartItemDto::from).toList()));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update the quantity of a cart item")
    public CartDto updateItem(@PathVariable Long itemId, @Valid @RequestBody UpdateItemRequest request) {
        Long userId = AuthFacade.currentUserId();
        cartService.updateItem(userId, itemId, request.quantity());
        Cart cart = cartService.getOrCreateCart(userId);
        return new CartDto(
                cart.getId(), cart.getItems().stream().map(CartItemDto::from).toList());
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove an item from the cart")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId) {
        cartService.removeItem(AuthFacade.currentUserId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Clear the cart")
    public ResponseEntity<Void> clear() {
        cartService.clear(AuthFacade.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
