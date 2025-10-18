package com.industrial.simulation.service;

import com.industrial.simulation.domain.line.ProductionLine;
import com.industrial.simulation.domain.product.Product;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductAnalysisService {

  List<String> getHighEfficiencyLines(double threshold);

  Map<String, Long> countProductsByCategory();

  Optional<ProductionLine<? extends Product>> findMostLoadedLine();

  List<Product> getAllProductsFromLines();

  double calculateTotalProductionTime();
}
