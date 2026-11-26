package com.uniq.eimp;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    public record ClaimRequest(String policyId, BigDecimal amount,String description) {}
    public record Claim(String claimId,String policyId,String customerId,BigDecimal amount,String description,String status,Instant createdAt) {}
    private final Map<String,Claim> claims=new java.util.concurrent.ConcurrentHashMap<>();
    private final PolicyController policies;
    private final CustomerController customers;
    public ClaimController(PolicyController policies,CustomerController customers){this.policies=policies;this.customers=customers;}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public synchronized Claim register(@RequestBody ClaimRequest input,HttpServletRequest http){
        var customer=customers.me(http);
        if(input.policyId()==null || input.amount()==null || input.amount().signum()<=0 || input.amount().scale()>2 || input.description()==null || input.description().isBlank() || input.description().length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid claim details");
        var policy=policies.get(input.policyId(),http);
        if(!"ACTIVE".equals(policy.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Active policy required");
        var reserved=claims.values().stream().filter(c->c.policyId().equals(policy.policyId()) && !"REJECTED".equals(c.status())).map(Claim::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(reserved.add(input.amount()).compareTo(BigDecimal.valueOf(policy.coverage()))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Available coverage exceeded");
        if(claims.size()>=5000)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Training capacity reached");
        var c=new Claim("CLM-"+UUID.randomUUID(),policy.policyId(),customer.customerId(),input.amount(),input.description().strip(),"SUBMITTED",Instant.now());
        claims.put(c.claimId(),c);return c;
    }
    @GetMapping
    public List<Claim> list(HttpServletRequest http){
        var customer=customers.me(http);
        return claims.values().stream().filter(c->c.customerId().equals(customer.customerId())).sorted(Comparator.comparing(Claim::createdAt).reversed()).toList();
    }
}
