package com.industrial.simulation.domain.line;

import com.industrial.simulation.domain.product.Product;
import java.util.ArrayList;
import java.util.List;

public abstract class ProductionLine<T extends Product> {
    private final String lineId;
    private final double efficiency;
    private final List<T> products;

    public ProductionLine(String lineId, double efficiency) {
        if (lineId == null || lineId.isBlank()) {
            throw new IllegalArgumentException("Line ID cannot be null or blank.");
        }
        if (efficiency < 0.0 || efficiency > 1.0) {
            throw new IllegalArgumentException(
                    "Efficiency must be between 0.0 and 1.0.");
        }

        this.lineId = lineId;
        this.efficiency = efficiency;
        this.products = new ArrayList<>();
    }

    public String getLineId() {
        return lineId;
    }

    public List<T> getProducts() {
        return products;
    }

    public double getEfficiency() {
        return efficiency;
    }

    public abstract boolean canProduce(Product product);

    public void addProduct(Product product) {
        if (!canProduce(product)) {
            throw new IllegalArgumentException(
                    "Product type " + product.getClass().getSimpleName() +
                            " is not compatible with line " + this.getClass().getSimpleName());
        }

        @SuppressWarnings("unchecked")
        T compatibleProduct = (T) product;
        this.products.add(compatibleProduct);
    }
}
