package com.industrial.simulation;

import com.industrial.simulation.domain.line.ChemicalLine;
import com.industrial.simulation.domain.line.ElectronicsLine;
import com.industrial.simulation.domain.line.MechanicalLine;
import com.industrial.simulation.domain.line.ProductionLine;
import com.industrial.simulation.domain.product.ChemicalProduct;
import com.industrial.simulation.domain.product.ElectronicProduct;
import com.industrial.simulation.domain.product.MechanicalProduct;
import com.industrial.simulation.domain.product.Product;
import com.industrial.simulation.service.ProductAnalysisService;
import com.industrial.simulation.service.impl.DefaultProductAnalysisService;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Application {

    public static void main(String[] args) {
        ElectronicsLine elLine1 = new ElectronicsLine("EL-001", 0.95);
        MechanicalLine mlLine1 = new MechanicalLine("ML-001", 0.82);
        ChemicalLine clLine1 = new ChemicalLine("CL-001", 0.91);

        elLine1.addProduct(new ElectronicProduct("E-1", "Смартфон", 20));
        elLine1.addProduct(new ElectronicProduct("E-2", "Планшет", 35));

        mlLine1.addProduct(new MechanicalProduct("M-1", "Редуктор", 150));
        mlLine1.addProduct(new MechanicalProduct("M-2", "Вал", 90));
        mlLine1.addProduct(new MechanicalProduct("M-3", "Подшипник", 45));
        clLine1.addProduct(new ChemicalProduct("C-1", "Полиэтилен", 240));

        try {
            System.out.println("--- Попытка добавить несовместимый продукт ---");
            mlLine1.addProduct(new ElectronicProduct("E-FAIL", "Процессор", 10));
        } catch (IllegalArgumentException e) {
            System.out.println("Успешно поймали ошибку: " + e.getMessage());
        }

        List<ProductionLine<? extends Product>> allLines = List.of(elLine1, mlLine1, clLine1);
        ProductAnalysisService analysisService = new DefaultProductAnalysisService(allLines);

        System.out.println("\n--- 3. Запуск анализа и вывод результатов ---");

        double threshold = 0.9;
        List<String> highEfficiencyLines = analysisService.getHighEfficiencyLines(threshold);
        System.out.println("\nЛинии с эффективностью > " + threshold + ": " +
                highEfficiencyLines);

        Map<String, Long> counts = analysisService.countProductsByCategory();
        System.out.println("\nКоличество продуктов по категориям: " + counts);

        Optional<ProductionLine<? extends Product>> mostLoadedLine = analysisService.findMostLoadedLine();
        mostLoadedLine.ifPresent(
                line -> System.out.println("\nСамая загруженная линия: " + line.getLineId() +
                        " (" + line.getProducts().size() +
                        " продуктов)"));

        List<Product> allProducts = analysisService.getAllProductsFromLines();
        System.out.println("\nВсего продуктов со всех линий: " +
                allProducts.size());

        double totalTime = analysisService.calculateTotalProductionTime();
        System.out.println(
                "\nОбщее время производства всех продуктов: " + totalTime + " минут");
    }
}
