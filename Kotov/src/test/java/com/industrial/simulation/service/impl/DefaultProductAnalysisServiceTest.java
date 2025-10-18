package com.industrial.simulation.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.industrial.simulation.domain.line.ChemicalLine;
import com.industrial.simulation.domain.line.ElectronicsLine;
import com.industrial.simulation.domain.line.MechanicalLine;
import com.industrial.simulation.domain.line.ProductionLine;
import com.industrial.simulation.domain.product.ChemicalProduct;
import com.industrial.simulation.domain.product.ElectronicProduct;
import com.industrial.simulation.domain.product.MechanicalProduct;
import com.industrial.simulation.domain.product.Product;
import com.industrial.simulation.service.ProductAnalysisService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultProductAnalysisService")
class DefaultProductAnalysisServiceTest {

  @Nested
  @DisplayName("When analyzing a set of production lines")
  class WhenAnalyzingLines {

    private ProductAnalysisService analysisService;

    @BeforeEach
    void setUp() {
      ElectronicsLine elLine = new ElectronicsLine("EL-001", 0.95);
      elLine.addProduct(new ElectronicProduct("E-1", "Смартфон", 20));
      elLine.addProduct(new ElectronicProduct("E-2", "Планшет", 30));

      MechanicalLine mlLine = new MechanicalLine("ML-001", 0.80);
      mlLine.addProduct(new MechanicalProduct("M-1", "Редуктор", 100));
      mlLine.addProduct(new MechanicalProduct("M-2", "Вал", 50));
      mlLine.addProduct(new MechanicalProduct("M-3", "Подшипник", 25));

      ChemicalLine clLineEmpty = new ChemicalLine("CL-001", 0.91);

      List<ProductionLine<? extends Product>> allLines = List.of(elLine, mlLine, clLineEmpty);
      analysisService = new DefaultProductAnalysisService(allLines);
    }

    @Test
    void getHighEfficiencyLines_shouldReturnOnlyLinesAboveThreshold() {
      List<String> result = analysisService.getHighEfficiencyLines(0.9);
      assertEquals(2, result.size());
      assertTrue(result.containsAll(List.of("EL-001", "CL-001")));
    }

    @Test
    void getHighEfficiencyLines_shouldReturnEmptyListWhenNoneMatch() {
      List<String> result = analysisService.getHighEfficiencyLines(0.99);
      assertTrue(result.isEmpty());
    }

    @Test
    void countProductsByCategory_shouldReturnCorrectCounts() {
      var result = analysisService.countProductsByCategory();
      assertEquals(2, result.size());
      assertEquals(2L, result.get("Electronics"));
      assertEquals(3L, result.get("Mechanical"));
      assertNull(result.get("Chemical"));
    }

    @Test
    void findMostLoadedLine_shouldReturnTheLineWithMostProducts() {
      var result = analysisService.findMostLoadedLine();
      assertTrue(result.isPresent());
      assertEquals("ML-001", result.get().getLineId());
    }

    @Test
    void getAllProductsFromLines_shouldReturnAllProductsFromAllLines() {
      List<Product> result = analysisService.getAllProductsFromLines();
      assertEquals(5, result.size());
    }

    @Test
    void calculateTotalProductionTime_shouldReturnTheSumOfAllProductTimes() {
      double totalTime = analysisService.calculateTotalProductionTime();
      assertEquals(225.0, totalTime); // (20 + 30) + (100 + 50 + 25)
    }
  }

  @Nested
  @DisplayName("When initialized with an empty list of lines")
  class WhenNoLinesExist {

    private ProductAnalysisService emptyService;

    @BeforeEach
    void setUp() {
      emptyService = new DefaultProductAnalysisService(Collections.emptyList());
    }

    @Test
    void getHighEfficiencyLines_shouldReturnEmptyList() {
      assertTrue(emptyService.getHighEfficiencyLines(0.5).isEmpty());
    }

    @Test
    void countProductsByCategory_shouldReturnEmptyMap() {
      assertTrue(emptyService.countProductsByCategory().isEmpty());
    }

    @Test
    void findMostLoadedLine_shouldReturnEmptyOptional() {
      assertTrue(emptyService.findMostLoadedLine().isEmpty());
    }

    @Test
    void getAllProductsFromLines_shouldReturnEmptyList() {
      assertTrue(emptyService.getAllProductsFromLines().isEmpty());
    }

    @Test
    void calculateTotalProductionTime_shouldReturnZero() {
      assertEquals(0.0, emptyService.calculateTotalProductionTime());
    }
  }
}
