package com.uniq.eimp;
import java.util.*;
import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController
@RequestMapping("/api/support/batches")
public class BatchMonitorController {
 private static final List<String> GROUPS=List.of("CUSTOMER_POLICY","PREMIUM","CLAIMS","SETTLEMENT","RECON_REPORT");
 private static final List<String> STEPS=List.of("FILE_RECEIVE","VALIDATE","STAGING_LOAD","BUSINESS_PROCESS","RECONCILE","OUTBOUND_REPORT");
 private record Job(String id,String name,String dependency,String status,Instant updatedAt){}
 private final Map<String,Job> jobs=new LinkedHashMap<>();
 public BatchMonitorController(){for(int g=1;g<=5;g++)for(int j=1;j<=6;j++){String id=id(g,j);jobs.put(id,new Job(id,GROUPS.get(g-1)+"_"+STEPS.get(j-1),j==1?"":id(g,j-1),"WAITING",Instant.now()));}}
 private static String id(int g,int j){return "EIMP_BATCH_%02d_JOB_%02d".formatted(g,j);}
 private void authorize(HttpServletRequest request){var s=request.getSession(false);if(s==null||!(s.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 @GetMapping public synchronized Map<String,Object> list(HttpServletRequest request){authorize(request);return Map.of("simulation",true,"jobs",List.copyOf(jobs.values()));}
 @PostMapping("/{id}/{action}") public synchronized Map<String,Object> action(@PathVariable String id,@PathVariable String action,HttpServletRequest request){
 authorize(request);var j=jobs.get(id);if(j==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
 String next=switch(action){
 case "start" -> {if(!Set.of("WAITING","FAILED","STOPPED").contains(j.status())||(!j.dependency().isEmpty()&&!"SUCCESS".equals(jobs.get(j.dependency()).status())))throw new ResponseStatusException(HttpStatus.CONFLICT,"Dependency or state not ready");yield "RUNNING";}
 case "success","fail","stop" -> {if(!"RUNNING".equals(j.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Job must be running");yield switch(action){case "success"->"SUCCESS";case "fail"->"FAILED";default->"STOPPED";};}
 default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
 };var updated=new Job(j.id(),j.name(),j.dependency(),next,Instant.now());jobs.put(id,updated);return Map.of("simulation",true,"job",updated);
 }
}
