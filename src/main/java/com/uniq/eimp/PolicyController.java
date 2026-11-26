package com.uniq.eimp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.math.BigDecimal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    public record Policy(String policyId, String customerId, String productCode, long coverage, int age, BigDecimal annualPremium, String status, Instant createdAt) {}
    private final Map<String,Policy> policies = new ConcurrentHashMap<>();
    private final CustomerController customers;
    private final ProductController products;
    public PolicyController(CustomerController customers, ProductController products) {
        this.customers = customers; this.products = products;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public synchronized Policy apply(@RequestBody ProductController.QuoteRequest request, HttpServletRequest http) {
        var customer = customers.me(http);
        if(policies.size() >= 5000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Training capacity reached");
        var quote = products.quote(request);
        var policy = new Policy("POL-"+UUID.randomUUID(),customer.customerId(),quote.productCode(),quote.coverage(),quote.age(),quote.annualPremium(),"PENDING_UNDERWRITING",Instant.now());
        policies.put(policy.policyId(),policy);
        return policy;
    }
    @GetMapping
    public List<Policy> list(HttpServletRequest http) {
        var customer = customers.me(http);
        return policies.values().stream().filter(p -> p.customerId().equals(customer.customerId())).sorted(Comparator.comparing(Policy::createdAt).reversed()).toList();
    }
    @GetMapping("/{id}")
    public Policy get(@PathVariable String id,HttpServletRequest http) {
        var customer = customers.me(http);
        var policy = policies.get(id);
        if(policy == null || !policy.customerId().equals(customer.customerId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Policy not found");
        return policy;
    }
}
