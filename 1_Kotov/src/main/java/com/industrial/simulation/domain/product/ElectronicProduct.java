package com.industrial.simulation.domain.product;

public class ElectronicProduct extends Product {
    public ElectronicProduct(String id, String name, int productionTimeMinutes) {
        super(id, name, productionTimeMinutes);
    }

    @Override
    public String getCategory() {
        return "Electronics";
    }
}
