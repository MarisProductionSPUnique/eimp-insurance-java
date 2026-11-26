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
    private final EmployeeController employees;
    public record Decision(String policyId,String decision,String reason,String employee,Instant decidedAt) {}
    private final Map<String,Decision> decisions=new ConcurrentHashMap<>();
    public PolicyController(CustomerController customers, ProductController products, EmployeeController employees) {
        this.customers = customers; this.products = products; this.employees=employees;
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

    @GetMapping("/underwriting")
    public List<Policy> underwriting(HttpServletRequest http){
        employees.me(http);
        return policies.values().stream().sorted(Comparator.comparing(Policy::createdAt).reversed()).toList();
    }
    public record DecisionRequest(String decision,String reason) {}
    @PostMapping("/underwriting/{id}")
    public synchronized Decision decide(@PathVariable String id,@RequestBody DecisionRequest input,HttpServletRequest http){
        var employee=employees.me(http);
        if(!"APPROVED".equals(input.decision()) && !"REJECTED".equals(input.decision())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid decision");
        if(input.reason()==null || input.reason().isBlank() || input.reason().length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reason required");
        var p=policies.get(id);
        if(p==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!"PENDING_UNDERWRITING".equals(p.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Already decided");
        var d=new Decision(id,input.decision(),input.reason().strip(),employee.username(),Instant.now());
        policies.put(id,new Policy(p.policyId(),p.customerId(),p.productCode(),p.coverage(),p.age(),p.annualPremium(),input.decision(),p.createdAt()));
        decisions.put(id,d);
        return d;
    }
    @GetMapping("/underwriting/history")
    public List<Decision> history(HttpServletRequest http){
        employees.me(http);
        return decisions.values().stream().sorted(Comparator.comparing(Decision::decidedAt).reversed()).toList();
    }

    public record Payment(String receiptId,String policyId,BigDecimal amount,String status,Instant paidAt) {}
    private final Map<String,Payment> payments=new ConcurrentHashMap<>();
    @PostMapping("/{id}/payment")
    public synchronized Payment pay(@PathVariable String id,HttpServletRequest http){
        var p=get(id,http);
        var existing=payments.get(id);
        if(existing!=null)return existing;
        if(!"APPROVED".equals(p.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Policy must be approved");
        var receipt=new Payment("PAY-"+UUID.randomUUID(),id,p.annualPremium(),"SUCCESS",Instant.now());
        payments.put(id,receipt);
        policies.put(id,new Policy(p.policyId(),p.customerId(),p.productCode(),p.coverage(),p.age(),p.annualPremium(),"ACTIVE",p.createdAt()));
        return receipt;
    }
    @GetMapping("/{id}/payment")
    public synchronized Payment receipt(@PathVariable String id,HttpServletRequest http){
        get(id,http);
        var payment=payments.get(id);
        if(payment==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No payment");
        return payment;
    }
public synchronized Map<String,Object> businessSummary(){var counts=new TreeMap<String,Long>();for(var p:policies.values())counts.merge(p.status(),1L,Long::sum);return Map.of("total",policies.size(),"statuses",counts,"payments",payments.size(),"premiumCollected",payments.values().stream().map(Payment::amount).reduce(BigDecimal.ZERO,BigDecimal::add));}
}
