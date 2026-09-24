package com.orderflow.cart;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.error.ResourceNotFoundException;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import com.orderflow.product.ProductStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> cartRepository.save(new Cart(userId)));
    }

    @Transactional
    public Cart addItem(Long userId, Long productId, int quantity) {
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Product is not available for purchase");
        }
        if (quantity <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Quantity must be positive");
        }

        Cart cart = getOrCreateCart(userId);
        cart.findItemByProduct(productId)
                .ifPresentOrElse(
                        item -> item.setQuantity(item.getQuantity() + quantity),
                        () -> cart.addItem(new CartItem(productId, quantity)));
        return cart;
    }

    @Transactional
    public Cart updateItem(Long userId, Long itemId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cart.findItem(itemId).orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
        item.setQuantity(quantity); // validates quantity > 0
        return cart;
    }

    @Transactional
    public void removeItem(Long userId, Long itemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cart.findItem(itemId).orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
        cart.removeItem(item);
    }

    @Transactional
    public void clear(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
    }

    @Transactional(readOnly = true)
    public List<CartItem> getItems(Long userId) {
        return getOrCreateCart(userId).getItems();
    }
}
