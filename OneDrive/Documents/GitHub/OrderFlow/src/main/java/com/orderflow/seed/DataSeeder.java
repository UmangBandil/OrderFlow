package com.orderflow.seed;

import com.orderflow.auth.Role;
import com.orderflow.auth.User;
import com.orderflow.auth.UserRepository;
import com.orderflow.inventory.Inventory;
import com.orderflow.inventory.InventoryRepository;
import com.orderflow.inventory.Warehouse;
import com.orderflow.inventory.WarehouseRepository;
import com.orderflow.product.Category;
import com.orderflow.product.CategoryRepository;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds demo data when SEED_DATA=true. */
@Component
@Profile("seed")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            InventoryRepository inventoryRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Seed skipped: users already exist");
            return;
        }

        Warehouse warehouse = warehouseRepository.save(new Warehouse("Main DC", "Pune"));

        User admin = new User("admin@orderflow.io", passwordEncoder.encode("Admin123!"), "Site Admin", Role.ADMIN);
        User customer = new User(
                "customer@orderflow.io", passwordEncoder.encode("Customer123!"), "Test Customer", Role.CUSTOMER);
        User warehouseManager = new User(
                "warehouse@orderflow.io",
                passwordEncoder.encode("Warehouse123!"),
                "Warehouse Manager",
                Role.WAREHOUSE_MANAGER);
        User support = new User(
                "support@orderflow.io", passwordEncoder.encode("Support123!"), "Support Agent", Role.SUPPORT_AGENT);
        userRepository.save(admin);
        userRepository.save(customer);
        userRepository.save(warehouseManager);
        userRepository.save(support);

        Category electronics = categoryRepository.save(new Category("electronics"));
        Category books = categoryRepository.save(new Category("books"));
        Category home = categoryRepository.save(new Category("home"));

        Product laptop = new Product(
                "SKU-LAP-001", "Ultrabook 14", "14-inch lightweight ultrabook", new BigDecimal("999.99"), electronics);
        Product phone =
                new Product("SKU-PHN-001", "Phone X", "Flagship smartphone", new BigDecimal("699.00"), electronics);
        Product novel = new Product(
                "SKU-BOK-001", "Domain-Driven Design", "Classic software design book", new BigDecimal("45.50"), books);
        Product lamp = new Product("SKU-HOM-001", "Desk Lamp", "LED desk lamp", new BigDecimal("29.99"), home);
        productRepository.save(laptop);
        productRepository.save(phone);
        productRepository.save(novel);
        productRepository.save(lamp);

        inventoryRepository.save(new Inventory(laptop.getId(), warehouse.getId(), 25));
        inventoryRepository.save(new Inventory(phone.getId(), warehouse.getId(), 50));
        inventoryRepository.save(new Inventory(novel.getId(), warehouse.getId(), 100));
        inventoryRepository.save(new Inventory(lamp.getId(), warehouse.getId(), 8));

        log.info("Seed data created: 4 users, 4 products, warehouse + inventory");
    }
}
