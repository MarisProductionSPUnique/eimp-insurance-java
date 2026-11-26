package com.uniq.eimp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    public record Product(String code, String name, BigDecimal annualRate, long minCover, long maxCover) {}
    public record QuoteRequest(String productCode, Long coverage, Integer age) {}
    public record Quote(String productCode, long coverage, int age, BigDecimal annualPremium, String currency, String mode) {}
    private final List<Product> products = List.of(
        new Product("HEALTH", "Health Insurance", new BigDecimal("0.025"), 100000, 2000000),
        new Product("LIFE", "Life Insurance", new BigDecimal("0.008"), 500000, 10000000),
        new Product("MOTOR", "Motor Insurance", new BigDecimal("0.035"), 50000, 3000000),
        new Product("TRAVEL", "Travel Insurance", new BigDecimal("0.005"), 50000, 1000000)
    );
    @GetMapping
    public List<Product> list() { return products; }

    @PostMapping("/quote")
    public Quote quote(@RequestBody QuoteRequest request) {
        Product product = products.stream().filter(p -> p.code().equalsIgnoreCase(request.productCode()))
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product code"));
        if (request.coverage() == null || request.coverage() < product.minCover() || request.coverage() > product.maxCover())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coverage outside product limits");
        if (request.age() == null || request.age() < 18 || request.age() > 75)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Age must be between 18 and 75");
        BigDecimal factor = request.age() >= 60 ? new BigDecimal("1.5") : request.age() >= 45 ? new BigDecimal("1.2") : BigDecimal.ONE;
        BigDecimal premium = BigDecimal.valueOf(request.coverage()).multiply(product.annualRate()).multiply(factor).setScale(2, RoundingMode.HALF_UP);
        return new Quote(product.code(), request.coverage(), request.age(), premium, "INR", "TRAINING_ONLY");
    }
}
