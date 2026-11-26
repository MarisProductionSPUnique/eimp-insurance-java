package com.uniq.eimp;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.Map;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/support")
public class SupportController {
 @Value("${EIMP_SUPPORT_USERNAME:support}") private String username;
 @Value("${EIMP_SUPPORT_PASSWORD:}") private String password;
 public record Login(String username,String password){}
 private int failures;
 private Instant blocked=Instant.EPOCH;
 @PostMapping("/login")
 public synchronized Map<String,String> login(@RequestBody Login input,HttpServletRequest request){
  if(password.length()<16)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Support account not configured");
  if(Instant.now().isBefore(blocked))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
  if(!username.equals(input.username())||input.password()==null||input.password().length()>256||!MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),input.password().getBytes(StandardCharsets.UTF_8))){
   if(++failures>=5){blocked=Instant.now().plusSeconds(60);failures=0;}
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }
  failures=0;var old=request.getSession(false);if(old!=null)old.invalidate();
  var session=request.getSession(true);session.setMaxInactiveInterval(1800);session.setAttribute("supportExpiry",Instant.now().plusSeconds(3600));
  return Map.of("username",username,"role","SUPPORT");
 }
 private void authorize(HttpServletRequest request){
  var session=request.getSession(false);
  if(session==null||!(session.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
 }
 @GetMapping("/health")
 public Map<String,Object> health(HttpServletRequest request){
  authorize(request);
  return Map.of("application","EIMP","status","UP","uptimeSeconds",ManagementFactory.getRuntimeMXBean().getUptime()/1000,"database","NOT_CONFIGURED","timestamp",Instant.now().toString(),"username",username);
 }
 @PostMapping("/logout")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void logout(HttpServletRequest request){authorize(request);request.getSession(false).invalidate();}
}
