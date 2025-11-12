package com.industrial.simulation.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.industrial.simulation.domain.product.ElectronicProduct;
import com.industrial.simulation.domain.product.MechanicalProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Product Domain Logic")
class ProductValidationTest {

  @Test
  void shouldCreateProductSuccessfullyWithValidData() {
    var product = new ElectronicProduct("E-1", "Laptop", 60);
    assertEquals("E-1", product.getId());
    assertEquals("Laptop", product.getName());
    assertEquals(60, product.getProductionTimeMinutes());
    assertEquals("Electronics", product.getCategory());
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenIdIsNull() {
    assertThrows(IllegalArgumentException.class,
        () -> {
          new MechanicalProduct(null, "Engine", 300);
        });
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenIdIsBlank() {
    assertThrows(IllegalArgumentException.class,
        () -> {
          new MechanicalProduct("  ", "Engine", 300);
        });
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenProductionTimeIsZero() {
    assertThrows(IllegalArgumentException.class,
        () -> {
          new ElectronicProduct("E-2", "Mouse", 0);
        });
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenProductionTimeIsNegative() {
    assertThrows(IllegalArgumentException.class,
        () -> {
          new ElectronicProduct("E-2", "Mouse", -10);
        });
  }
}
