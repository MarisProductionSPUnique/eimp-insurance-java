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
 public record Run(String runId,String jobId,int attempt,Instant startedAt,Instant endedAt,Long durationSeconds,String status,String errorCode,String reason,int inputRecords,int processedRecords,int rejectedRecords,int pendingRecords){}
 @org.springframework.beans.factory.annotation.Value("${EIMP_BATCH_SLA_SECONDS:60}") private long slaSeconds;
 @GetMapping("/sla") public synchronized Map<String,Object> sla(HttpServletRequest request){
 authorize(request);long limit=Math.max(1,slaSeconds);var now=Instant.now();var rows=new ArrayList<Map<String,Object>>();var runs=new ArrayList<Run>(active.values());runs.addAll(history);
 for(var run:runs){long elapsed=run.durationSeconds()==null?Math.max(0,java.time.Duration.between(run.startedAt(),now).getSeconds()):run.durationSeconds();rows.add(Map.of("runId",run.runId(),"jobId",run.jobId(),"status",run.status(),"elapsedSeconds",elapsed,"slaSeconds",limit,"breached",elapsed>limit));}
 return Map.of("simulation",true,"slaSeconds",limit,"runs",rows,"breaches",rows.stream().filter(row->Boolean.TRUE.equals(row.get("breached"))).count());
 }
 private final Map<String,Run> active=new HashMap<>();
 private final Map<String,Integer> attempts=new HashMap<>();
 private final Deque<Run> history=new ArrayDeque<>();
 @GetMapping("/history") public synchronized List<Run> history(HttpServletRequest request){authorize(request);var result=new ArrayList<Run>(active.values());result.addAll(history);return result;}
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
 };var updated=new Job(j.id(),j.name(),j.dependency(),next,Instant.now());jobs.put(id,updated);
 if("start".equals(action)){active.put(id,new Run("RUN-"+UUID.randomUUID(),id,attempts.merge(id,1,Integer::sum),updated.updatedAt(),null,null,"RUNNING","","",100,0,0,100));}
 else{var run=active.remove(id);if(run!=null){int step=Integer.parseInt(id.substring(id.length()-2));String error="FAILED".equals(next)?List.of("SIM-FILE-MISSING","SIM-INVALID-FORMAT","SIM-DB-CONNECTION","SIM-PROCESSING","SIM-RECON-MISMATCH","SIM-PERMISSION").get(step-1):"";String reason=switch(next){case "FAILED"->List.of("Simulated missing inbound file; check source transfer and file path","Simulated invalid delimiter count; validate input format","Simulated database connection failure; check listener and connectivity","Simulated business validation failure; check input records and rules","Simulated reconciliation mismatch; compare source and processed totals","Simulated outbound permission denied; check directory ownership and permissions").get(step-1);case "STOPPED"->"Stopped by support operator";default->"Simulated execution completed";};history.addFirst(new Run(run.runId(),id,run.attempt(),run.startedAt(),updated.updatedAt(),Math.max(0,java.time.Duration.between(run.startedAt(),updated.updatedAt()).getSeconds()),next,error,reason,100,"SUCCESS".equals(next)?100:0,"FAILED".equals(next)&&step==2?10:0,"SUCCESS".equals(next)?0:("FAILED".equals(next)&&step==2?90:100)));while(history.size()>2000)history.removeLast();}}
 return Map.of("simulation",true,"job",updated);
 }
}
