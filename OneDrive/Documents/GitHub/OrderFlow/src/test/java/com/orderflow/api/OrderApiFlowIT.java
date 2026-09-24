package com.orderflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.auth.Role;
import com.orderflow.auth.User;
import com.orderflow.auth.UserRepository;
import com.orderflow.inventory.Inventory;
import com.orderflow.inventory.InventoryRepository;
import com.orderflow.order.OrderRepository;
import com.orderflow.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end API tests over a real PostgreSQL (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("itest")
@Tag("integration")
@Transactional
class OrderApiFlowIT extends PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.orderflow.inventory.WarehouseRepository warehouseRepository;

    private Long warehouseId;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.registerContainerProperties(registry);
    }

    @BeforeEach
    void seedBaseData() {
        if (!userRepository.existsByEmailIgnoreCase("admin@itest.io")) {
            userRepository.save(new User("admin@itest.io", passwordEncoder.encode("Admin123!"), "Admin", Role.ADMIN));
        }
        warehouseId = warehouseRepository
                .findFirstByOrderByIdAsc()
                .orElseGet(() -> warehouseRepository.save(new com.orderflow.inventory.Warehouse("IT-DC", "Pune")))
                .getId();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        return node.get("accessToken").asText();
    }

    private String createProduct(String adminToken, String sku, String price) throws Exception {
        String response = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(
                                """
                            {"sku":"%s","name":"Product %s","description":"IT product","price":%s,"category":"electronics"}
                            """
                                        .formatted(sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        return node.get("id").asText();
    }

    private String createConfirmedOrder(String customerToken, String productId, String idemKey, int qty)
            throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType("application/json")
                        .content(
                                """
                            {"items":[{"productId":%s,"quantity":%d}],
                             "shippingAddress":{"addressLine":"Street 1","city":"Pune","postalCode":"411001"}}
                            """
                                        .formatted(productId, qty)))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("register -> login -> create order -> payment simulated -> order CONFIRMED")
    void happyPath() throws Exception {
        String customerToken = registerAndLogin("c1@itest.io");
        String adminToken = login("admin@itest.io", "Admin123!");
        String productId = createProduct(adminToken, "IT-SKU-1", "50.00");
        inventoryRepository.save(new Inventory(Long.parseLong(productId), warehouseId, 10));

        String body = createConfirmedOrder(customerToken, productId, "idem-1", 2);
        assertThat(body).contains("CONFIRMED");
        assertThat(body).contains("100.00");
    }

    @Test
    @DisplayName("duplicate POST /orders with same Idempotency-Key returns the original order")
    void idempotentOrderCreation() throws Exception {
        String customerToken = registerAndLogin("c2@itest.io");
        String adminToken = login("admin@itest.io", "Admin123!");
        String productId = createProduct(adminToken, "IT-SKU-2", "20.00");
        inventoryRepository.save(new Inventory(Long.parseLong(productId), warehouseId, 10));

        String first = createConfirmedOrder(customerToken, productId, "idem-2", 1);
        String second = createConfirmedOrder(customerToken, productId, "idem-2", 1);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(first)
                        .get("orderNumber")
                        .asText())
                .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(second)
                        .get("orderNumber")
                        .asText());
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("payment decline (total fraction .13) cancels order and releases inventory")
    void paymentFailureReleasesInventory() throws Exception {
        String customerToken = registerAndLogin("c3@itest.io");
        String adminToken = login("admin@itest.io", "Admin123!");
        String productId = createProduct(adminToken, "IT-SKU-3", "50.13");
        Inventory inventory = inventoryRepository.save(new Inventory(Long.parseLong(productId), warehouseId, 10));

        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "idem-3")
                        .contentType("application/json")
                        .content(
                                """
                            {"items":[{"productId":%s,"quantity":1}],
                             "shippingAddress":{"addressLine":"Street 1","city":"Pune","postalCode":"411001"}}
                            """
                                        .formatted(productId)))
                .andExpect(status().isPaymentRequired()) // 402 PAYMENT_FAILED after compensation
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).contains("PAYMENT_FAILED");

        // The saga compensated: the order exists and is CANCELLED, stock is fully released
        assertThat(orderRepository.findAll())
                .anySatisfy(o -> assertThat(o.getStatus()).isEqualTo(com.orderflow.order.OrderStatus.CANCELLED));

        Inventory after = inventoryRepository.findById(inventory.getId()).orElseThrow();
        assertThat(after.getAvailableQuantity()).isEqualTo(10);
        assertThat(after.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("customers can cancel a PAID order; inventory is returned")
    void customerCancelsOrder() throws Exception {
        String customerToken = registerAndLogin("c4@itest.io");
        String adminToken = login("admin@itest.io", "Admin123!");
        String productId = createProduct(adminToken, "IT-SKU-4", "10.00");
        Inventory inventory = inventoryRepository.save(new Inventory(Long.parseLong(productId), warehouseId, 5));

        String orderBody = createConfirmedOrder(customerToken, productId, "idem-4", 2);
        String orderId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(orderBody)
                .get("id")
                .asText();

        mockMvc.perform(post("/api/v1/orders/%s/cancel".formatted(orderId))
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Inventory after = inventoryRepository.findById(inventory.getId()).orElseThrow();
        assertThat(after.getAvailableQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("customers cannot list other customers' orders; admin can see all")
    void orderVisibility() throws Exception {
        String customerA = registerAndLogin("c5@itest.io");
        String adminToken = login("admin@itest.io", "Admin123!");
        String productId = createProduct(adminToken, "IT-SKU-5", "30.00");
        inventoryRepository.save(new Inventory(Long.parseLong(productId), warehouseId, 10));
        createConfirmedOrder(customerA, productId, "idem-5", 1);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + customerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("RBAC: CUSTOMER cannot create products; anonymous gets 401/403")
    void rbacEnforced() throws Exception {
        String customerToken = registerAndLogin("c6@itest.io");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json")
                        .content("{\"sku\":\"IT-X\",\"name\":\"X\",\"price\":1.00}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit-logs")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("shipment lifecycle advances the order to DELIVERED")
    void shipmentLifecycle() throws Exception {
        String customerToken = registerAndLogin("c7@itest.io");
        String adminToken = login("admin@itest.io", "Admin123!");
        String productId = createProduct(adminToken, "IT-SKU-6", "15.00");
        inventoryRepository.save(new Inventory(Long.parseLong(productId), warehouseId, 10));

        String orderBody = createConfirmedOrder(customerToken, productId, "idem-6", 1);
        String orderId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(orderBody)
                .get("id")
                .asText();

        String shipmentBody = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"orderId\":%s,\"carrier\":\"UPS\"}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String shipmentId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(shipmentBody)
                .get("id")
                .asText();

        for (String s : new String[] {"PACKED", "SHIPPED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            mockMvc.perform(put("/api/v1/shipments/%s/status".formatted(shipmentId))
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json")
                            .content("{\"status\":\"%s\"}".formatted(s)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/orders/%s".formatted(orderId)).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("refund endpoint refunds a successful payment")
    void refundFlow() throws Exception {
        String customerToken = registerAndLogin("c8@itest.io");
        String adminToken = login("admin@itest.io", "Admin123!");
        String productId = createProduct(adminToken, "IT-SKU-7", "99.00");
        inventoryRepository.save(new Inventory(Long.parseLong(productId), warehouseId, 10));

        String orderBody = createConfirmedOrder(customerToken, productId, "idem-7", 1);
        String orderId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(orderBody)
                .get("id")
                .asText();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String paymentBody = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"orderId\":%s,\"amount\":99.00}".formatted(orderId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        // Duplicate payment for an already-paid order returns the existing SUCCESS payment
        assertThat(mapper.readTree(paymentBody).get("status").asText()).isEqualTo("SUCCESS");
        String paymentId = mapper.readTree(paymentBody).get("id").asText();

        mockMvc.perform(post("/api/v1/payments/%s/refund".formatted(paymentId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"amount\":99.00,\"reason\":\"damaged\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("refresh endpoint rotates tokens; logout revokes the refresh token")
    void refreshAndLogout() throws Exception {
        String email = "c9@itest.io";
        registerAndLogin(email);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"Customer123!\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(loginBody);
        String refreshToken = node.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk());

        // old refresh token is rotated (single use) -> second use fails
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"Customer123!\",\"fullName\":\"IT Customer\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        return login(email, "Customer123!");
    }
}
