package com.uniq.eimp;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/support/business")
public class BusinessMonitorController {
 private final PolicyController policies;
 private final ClaimController claims;
 public BusinessMonitorController(PolicyController policies,ClaimController claims){this.policies=policies;this.claims=claims;}
 @GetMapping
 public Map<String,Object> summary(HttpServletRequest request){
  var session=request.getSession(false);
  if(session==null||!(session.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  return Map.of("policies",policies.businessSummary(),"claims",claims.businessSummary(),"timestamp",Instant.now().toString(),"storage","IN_MEMORY");
 }
}
