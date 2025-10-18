package com.industrial.simulation.domain.line;

import com.industrial.simulation.domain.product.MechanicalProduct;
import com.industrial.simulation.domain.product.Product;

public class MechanicalLine extends ProductionLine<MechanicalProduct> {

    public MechanicalLine(String lineId, double efficiency) {
        super(lineId, efficiency);
    }

    @Override
    public boolean canProduce(Product product) {
        return product instanceof MechanicalProduct;
    }
}
