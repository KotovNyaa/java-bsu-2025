package com.industrial.simulation.service.impl;

import com.industrial.simulation.domain.line.ProductionLine;
import com.industrial.simulation.domain.product.Product;
import com.industrial.simulation.service.ProductAnalysisService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultProductAnalysisService implements ProductAnalysisService {

  private final List<ProductionLine<? extends Product>> allLines;

  public DefaultProductAnalysisService(
      List<ProductionLine<? extends Product>> allLines) {
    this.allLines = List.copyOf(allLines);
  }

  @Override
  public List<String> getHighEfficiencyLines(double threshold) {
    return allLines.stream()
        .filter(line -> line.getEfficiency() > threshold)
        .map(ProductionLine::getLineId)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Long> countProductsByCategory() {
    return allLines.stream()
        .flatMap(line -> line.getProducts().stream())
        .collect(
            Collectors.groupingBy(Product::getCategory, Collectors.counting()));
  }

  @Override
  public Optional<ProductionLine<? extends Product>> findMostLoadedLine() {
    return allLines.stream().max(
        Comparator.comparingInt(line -> line.getProducts().size()));
  }

  @Override
  public List<Product> getAllProductsFromLines() {
    return allLines.stream()
        .flatMap(line -> line.getProducts().stream())
        .collect(Collectors.toList());
  }

  @Override
  public double calculateTotalProductionTime() {
    return allLines.stream()
        .flatMap(line -> line.getProducts().stream())
        .mapToDouble(Product::getProductionTimeMinutes)
        .sum();
  }
}
