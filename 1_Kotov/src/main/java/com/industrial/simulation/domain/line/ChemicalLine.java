package com.industrial.simulation.domain.line;

import com.industrial.simulation.domain.product.ChemicalProduct;
import com.industrial.simulation.domain.product.Product;

public class ChemicalLine extends ProductionLine<ChemicalProduct> {

    public ChemicalLine(String lineId, double efficiency) {
        super(lineId, efficiency);
    }

    @Override
    public boolean canProduce(Product product) {
        return product instanceof ChemicalProduct;
    }
}
