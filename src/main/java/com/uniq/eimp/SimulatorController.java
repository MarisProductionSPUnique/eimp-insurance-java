package com.uniq.eimp;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {
 private static final Set<Integer> CODES=Set.of(400,401,403,404,405,409,500,502,503,504);
 private long lastRun;
 @PostMapping("/http/{code}")
 public synchronized ResponseEntity<Map<String,Object>> simulate(@PathVariable int code,HttpServletRequest request){
  var session=request.getSession(false);
  if(session==null||!(session.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  if(!CODES.contains(code))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  long now=System.nanoTime();
  if(lastRun!=0&&now-lastRun<1000000000L)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
  lastRun=now;
  return ResponseEntity.status(code).body(Map.of("simulation",true,"status",code,"message","Controlled training response; no customer transaction or infrastructure affected"));
 }
}
