package com.orderflow.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import com.orderflow.product.ProductStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartServiceTest {

    private CartRepository cartRepository;
    private ProductRepository productRepository;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        productRepository = mock(ProductRepository.class);
        cartService = new CartService(cartRepository, productRepository);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(c, "id", 1L);
            return c;
        });
    }

    @Test
    @DisplayName("getOrCreateCart creates a cart lazily")
    void lazyCartCreation() {
        Cart cart = cartService.getOrCreateCart(7L);
        assertThat(cart.getUserId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("addItem with active product succeeds")
    void addItemActiveProduct() {
        Product product = new Product("SKU-1", "P", "d", BigDecimal.TEN, null);
        product.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));

        Cart cart = cartService.addItem(7L, 101L, 2);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("adding same product twice merges quantities")
    void sameProductMerges() {
        Product product = new Product("SKU-1", "P", "d", BigDecimal.TEN, null);
        product.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));

        Cart first = cartService.addItem(7L, 101L, 2);
        org.springframework.test.util.ReflectionTestUtils.setField(first, "id", 1L);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(first));

        Cart cart = cartService.addItem(7L, 101L, 3);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("inactive product cannot be added")
    void inactiveProductRejected() {
        Product product = new Product("SKU-1", "P", "d", BigDecimal.TEN, null);
        product.setStatus(ProductStatus.INACTIVE);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(7L, 101L, 1))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("non-positive quantity is rejected")
    void nonPositiveQuantityRejected() {
        Product product = new Product("SKU-1", "P", "d", BigDecimal.TEN, null);
        product.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(7L, 101L, 0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("clear removes all items")
    void clearEmpties() {
        Product product = new Product("SKU-1", "P", "d", BigDecimal.TEN, null);
        product.setStatus(ProductStatus.ACTIVE);
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));
        Cart cart = cartService.addItem(7L, 101L, 2);
        org.springframework.test.util.ReflectionTestUtils.setField(cart, "id", 1L);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));

        cartService.clear(7L);
        assertThat(cartService.getOrCreateCart(7L).getItems()).isEmpty();
    }
}
