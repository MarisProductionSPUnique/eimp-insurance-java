package com.uniq.eimp;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/support/alerts/config")
public class AlertConfigurationController {
 public record Rule(String id,String name,boolean active,double threshold,int durationSeconds,String priority){}
 public record Update(boolean active,double threshold,int durationSeconds,String priority){}
 public record Controls(boolean enabled,boolean maintenance){}
 private Controls controls=new Controls(false,false);
 private final Map<String,Rule> rules=new LinkedHashMap<>();
 private final Deque<Map<String,Object>> audit=new ArrayDeque<>();
 public AlertConfigurationController(){
 add("CPU","High CPU",90,300,"P2");add("MEMORY","High memory",90,300,"P2");add("DISK","Disk utilization",95,60,"P2");add("APP_DOWN","Application unavailable",1,60,"P1");add("DB_DOWN","Database disconnected",1,60,"P1");add("HEAP","JVM heap",90,300,"P2");add("HTTP_500","HTTP 500 errors",5,300,"P2");add("JAVA_EXCEPTION","Java exceptions",5,300,"P3");add("BATCH_FAILURE","Batch failure",1,0,"P2");add("BATCH_SLA","Batch SLA breach",60,0,"P3");add("PAYMENT_FAILURE","Payment failures",5,300,"P2");add("SETTLEMENT_FAILURE","Settlement failure",1,0,"P2");
 }
 private void add(String id,String name,double threshold,int duration,String priority){rules.put(id,new Rule(id,name,false,threshold,duration,priority));}
 private void authorize(HttpServletRequest r){var s=r.getSession(false);if(s==null||!(s.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 private void record(String target,Object before,Object after){audit.addFirst(Map.of("timestamp",Instant.now(),"actor","support","target",target,"before",before,"after",after));while(audit.size()>500)audit.removeLast();}
 @GetMapping public synchronized Map<String,Object> get(HttpServletRequest r){authorize(r);return Map.of("controls",controls,"rules",List.copyOf(rules.values()),"audit",List.copyOf(audit),"storage","IN_MEMORY","integrationReady",false);}
 @PutMapping("/controls") public synchronized Controls controls(@RequestBody Controls update,HttpServletRequest r){authorize(r);record("CONTROLS",controls,update);controls=update;return controls;}
 @PutMapping("/rules/{id}") public synchronized Rule update(@PathVariable String id,@RequestBody Update u,HttpServletRequest r){authorize(r);var old=rules.get(id);if(old==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);boolean percent=Set.of("CPU","MEMORY","DISK","HEAP").contains(id);if(!Double.isFinite(u.threshold())||u.threshold()<=0||u.threshold()>(percent?100:86400)||u.durationSeconds()<0||u.durationSeconds()>86400||u.priority()==null||!Set.of("P1","P2","P3","P4").contains(u.priority()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid rule configuration");var next=new Rule(id,old.name(),u.active(),u.threshold(),u.durationSeconds(),u.priority());record(id,old,next);rules.put(id,next);return next;}
}
