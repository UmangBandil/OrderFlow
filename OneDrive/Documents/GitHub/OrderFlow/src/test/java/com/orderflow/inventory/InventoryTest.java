package com.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.common.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryTest {

    private Inventory stock(int available) {
        return new Inventory(101L, 1L, available);
    }

    @Test
    @DisplayName("reserve decreases available and increases reserved")
    void reserveMovesQuantities() {
        Inventory inventory = stock(10);
        inventory.reserve(7);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("reserve beyond available throws INSUFFICIENT_INVENTORY and keeps stock unchanged")
    void reserveBeyondAvailableThrows() {
        Inventory inventory = stock(10);
        assertThatThrownBy(() -> inventory.reserve(11))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unavailable");
        assertThat(inventory.getAvailableQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("available_quantity can never go negative")
    void availableNeverNegative() {
        Inventory inventory = stock(5);
        inventory.reserve(5);
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThatThrownBy(() -> inventory.reserve(1)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("release returns reserved stock to available")
    void releaseReturnsStock() {
        Inventory inventory = stock(10);
        inventory.reserve(7);
        inventory.release(2);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
        assertThat(inventory.getReservedQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("release more than reserved is rejected")
    void releaseMoreThanReservedRejected() {
        Inventory inventory = stock(10);
        inventory.reserve(3);
        assertThatThrownBy(() -> inventory.release(4)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reserve and release require positive quantity")
    void quantitiesMustBePositive() {
        Inventory inventory = stock(10);
        assertThatThrownBy(() -> inventory.reserve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.reserve(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.release(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("increaseAvailable restocks")
    void restock() {
        Inventory inventory = stock(0);
        inventory.increaseAvailable(9);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(9);
    }

    @Test
    @DisplayName("reduceAvailable refuses to go negative")
    void reduceAvailableGuards() {
        Inventory inventory = stock(4);
        inventory.reduceAvailable(4);
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThatThrownBy(() -> inventory.reduceAvailable(1)).isInstanceOf(ApiException.class);
    }
}
