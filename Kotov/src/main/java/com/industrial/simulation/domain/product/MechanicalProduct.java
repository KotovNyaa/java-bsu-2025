package com.industrial.simulation.domain.product;

public class MechanicalProduct extends Product {
    public MechanicalProduct(String id, String name, int productionTimeMinutes) {
        super(id, name, productionTimeMinutes);
    }

    @Override
    public String getCategory() {
        return "Mechanical";
    }
}
