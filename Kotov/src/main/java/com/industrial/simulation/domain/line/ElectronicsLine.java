package com.industrial.simulation.domain.line;

import com.industrial.simulation.domain.product.ElectronicProduct;
import com.industrial.simulation.domain.product.Product;

public class ElectronicsLine extends ProductionLine<ElectronicProduct> {

    public ElectronicsLine(String lineId, double efficiency) {
        super(lineId, efficiency);
    }

    @Override
    public boolean canProduce(Product product) {
        return product instanceof ElectronicProduct;
    }
}
