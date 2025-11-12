package com.industrial.simulation.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.industrial.simulation.domain.line.MechanicalLine;
import com.industrial.simulation.domain.product.ElectronicProduct;
import com.industrial.simulation.domain.product.MechanicalProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Production Line Domain Logic")
class ProductionLineLogicTest {

  private MechanicalLine mechanicalLine;

  @BeforeEach
  void setUp() {
    mechanicalLine = new MechanicalLine("ML-TEST", 0.9);
  }

  @Test
  void shouldCreateLineSuccessfullyWithValidData() {
    assertEquals("ML-TEST", mechanicalLine.getLineId());
    assertEquals(0.9, mechanicalLine.getEfficiency());
    assertTrue(mechanicalLine.getProducts().isEmpty());
  }

  @Test
  void shouldThrowIllegalArgumentExceptionForNullId() {
    assertThrows(IllegalArgumentException.class,
        () -> {
          new MechanicalLine(null, 0.9);
        });
  }

  @Test
  void shouldThrowIllegalArgumentExceptionForEfficiencyBelowZero() {
    assertThrows(IllegalArgumentException.class,
        () -> {
          new MechanicalLine("ML-FAIL", -0.1);
        });
  }

  @Test
  void shouldThrowIllegalArgumentExceptionForEfficiencyAboveOne() {
    assertThrows(IllegalArgumentException.class,
        () -> {
          new MechanicalLine("ML-FAIL", 1.1);
        });
  }

  @Test
  void canProduceShouldReturnTrueForCompatibleProduct() {
    var compatibleProduct = new MechanicalProduct("M-1", "Gear", 20);
    assertTrue(mechanicalLine.canProduce(compatibleProduct));
  }

  @Test
  void canProduceShouldReturnFalseForIncompatibleProduct() {
    var incompatibleProduct = new ElectronicProduct("E-1", "Chip", 5);
    assertFalse(mechanicalLine.canProduce(incompatibleProduct));
  }

  @Test
  void shouldAddCompatibleProductSuccessfully() {
    var product = new MechanicalProduct("M-1", "Gear", 20);
    mechanicalLine.addProduct(product);
    assertEquals(1, mechanicalLine.getProducts().size());
    assertEquals(product, mechanicalLine.getProducts().get(0));
  }

  @Test
  void shouldThrowExceptionWhenAddingIncompatibleProduct() {
    var product = new ElectronicProduct("E-1", "Chip", 5);
    assertThrows(IllegalArgumentException.class,
        () -> {
          mechanicalLine.addProduct(product);
        });
  }
}
