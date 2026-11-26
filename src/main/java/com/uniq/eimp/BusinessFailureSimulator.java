package com.uniq.eimp;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/simulator/business")
public class BusinessFailureSimulator {
 public record Failure(Instant timestamp,String category,String reason){}
 private final Deque<Failure> events=new ArrayDeque<>();
 private Instant last=Instant.EPOCH;
 @PostMapping("/{category}/{reason}")
 public synchronized ResponseEntity<Map<String,Object>> simulate(@PathVariable String category,@PathVariable String reason,HttpServletRequest request){
 var session=request.getSession(false);
 if(session==null||!(session.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
 if(!Set.of("payment","settlement").contains(category)||!Set.of("timeout","unavailable","database").contains(reason))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
 var now=Instant.now();if(now.isBefore(last.plusSeconds(1)))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);last=now;
 events.addFirst(new Failure(now,category,reason));while(events.size()>2000)events.removeLast();
 return ResponseEntity.status(reason.equals("timeout")?504:reason.equals("unavailable")?503:500).body(Map.of("simulation",true,"category",category,"reason",reason,"status","FAILED"));
 }
 public synchronized long count(String category,int seconds){var cutoff=Instant.now().minusSeconds(Math.max(1,seconds));return events.stream().filter(e->e.category().equals(category)&&!e.timestamp().isBefore(cutoff)).count();}
}
