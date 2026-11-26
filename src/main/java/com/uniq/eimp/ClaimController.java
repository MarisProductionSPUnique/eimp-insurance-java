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
    private final EmployeeController employees;
    public ClaimController(PolicyController policies,CustomerController customers,EmployeeController employees){this.policies=policies;this.customers=customers;this.employees=employees;}
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

    public record Action(String status,String reason) {}
    public record Event(String claimId,String status,String reason,String employee,Instant at) {}
    public record Settlement(String receiptId,String claimId,BigDecimal amount,Instant paidAt) {}
    private final List<Event> history=new ArrayList<>();
    private final Map<String,Settlement> settlements=new HashMap<>();
    @GetMapping("/employee")
    public List<Claim> employeeList(HttpServletRequest http){
        employees.me(http);return claims.values().stream().sorted(Comparator.comparing(Claim::createdAt).reversed()).toList();
    }
    @GetMapping("/employee/history")
    public synchronized List<Event> history(HttpServletRequest http){employees.me(http);return List.copyOf(history);}
    @PostMapping("/employee/{id}")
    public synchronized Claim action(@PathVariable String id,@RequestBody Action input,HttpServletRequest http){
        var employee=employees.me(http);
        var c=claims.get(id);
        if(c==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(input.status()==null || input.reason()==null || input.reason().isBlank() || input.reason().length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reason required");
        var allowed=switch(c.status()){
            case "SUBMITTED" -> Set.of("UNDER_VERIFICATION");
            case "UNDER_VERIFICATION" -> Set.of("VERIFIED","REJECTED");
            case "VERIFIED" -> Set.of("APPROVED","REJECTED");
            case "APPROVED" -> Set.of("SETTLEMENT_PENDING");
            case "SETTLEMENT_PENDING" -> Set.of("SETTLED");
            default -> Set.<String>of();
        };
        if(!allowed.contains(input.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid transition");
        var now=Instant.now();
        if("SETTLED".equals(input.status()))settlements.put(id,new Settlement("STL-"+UUID.randomUUID(),id,c.amount(),now));
        var updated=new Claim(c.claimId(),c.policyId(),c.customerId(),c.amount(),c.description(),input.status(),c.createdAt());
        claims.put(id,updated);history.add(new Event(id,input.status(),input.reason().strip(),employee.username(),now));return updated;
    }
    @GetMapping("/{id}/settlement")
    public synchronized Settlement receipt(@PathVariable String id,HttpServletRequest http){
        var customer=customers.me(http);var c=claims.get(id);
        if(c==null || !c.customerId().equals(customer.customerId()) || !settlements.containsKey(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return settlements.get(id);
    }
public synchronized Map<String,Object> businessSummary(){var counts=new TreeMap<String,Long>();for(var c:claims.values())counts.merge(c.status(),1L,Long::sum);return Map.of("total",claims.size(),"statuses",counts,"settlements",settlements.size(),"settlementAmount",settlements.values().stream().map(Settlement::amount).reduce(BigDecimal.ZERO,BigDecimal::add));}
}
