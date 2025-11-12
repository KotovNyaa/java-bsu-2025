package com.industrial.simulation.domain.product;

public abstract class Product {
    private final String id;
    private final String name;
    private final int productionTimeMinutes;

    public Product(String id, String name, int productionTimeMinutes) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be null or blank.");
        }
        if (productionTimeMinutes <= 0) {
            throw new IllegalArgumentException("Production time must be positive.");
        }
        this.id = id;
        this.name = name;
        this.productionTimeMinutes = productionTimeMinutes;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getProductionTimeMinutes() {
        return productionTimeMinutes;
    }

    public abstract String getCategory();
}
