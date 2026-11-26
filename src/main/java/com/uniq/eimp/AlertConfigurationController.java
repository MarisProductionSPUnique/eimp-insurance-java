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
 private final BatchMonitorController batches;
 private final AccessLogFilter logs;
 private final BusinessFailureSimulator business;
 public AlertConfigurationController(BatchMonitorController batches,AccessLogFilter logs,BusinessFailureSimulator business){this.batches=batches;this.logs=logs;this.business=business;
 add("CPU","High CPU",90,300,"P2");add("MEMORY","High memory",90,300,"P2");add("DISK","Disk utilization",95,60,"P2");add("APP_DOWN","Application unavailable",1,60,"P1");add("DB_DOWN","Database disconnected",1,60,"P1");add("HEAP","JVM heap",90,300,"P2");add("HTTP_500","HTTP 500 errors",5,300,"P2");add("JAVA_EXCEPTION","Java exceptions",5,300,"P3");add("BATCH_FAILURE","Batch failure",1,0,"P2");add("BATCH_SLA","Batch SLA breach",60,0,"P3");add("PAYMENT_FAILURE","Payment failures",5,300,"P2");add("SETTLEMENT_FAILURE","Settlement failure",1,0,"P2");
 }
 private void add(String id,String name,double threshold,int duration,String priority){rules.put(id,new Rule(id,name,false,threshold,duration,priority));}
 private void authorize(HttpServletRequest r){var s=r.getSession(false);if(s==null||!(s.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 private void record(String target,Object before,Object after){audit.addFirst(Map.of("timestamp",Instant.now(),"actor","support","target",target,"before",before,"after",after));while(audit.size()>500)audit.removeLast();}
 @GetMapping public synchronized Map<String,Object> get(HttpServletRequest r){authorize(r);return Map.of("controls",controls,"rules",List.copyOf(rules.values()),"audit",List.copyOf(audit),"storage","IN_MEMORY","integrationReady",false);}
 @PutMapping("/controls") public synchronized Controls controls(@RequestBody Controls update,HttpServletRequest r){authorize(r);record("CONTROLS",controls,update);controls=update;return controls;}
 @PutMapping("/rules/{id}") public synchronized Rule update(@PathVariable String id,@RequestBody Update u,HttpServletRequest r){authorize(r);var old=rules.get(id);if(old==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);boolean percent=Set.of("CPU","MEMORY","DISK","HEAP").contains(id);if(!Double.isFinite(u.threshold())||u.threshold()<=0||u.threshold()>(percent?100:86400)||u.durationSeconds()<0||u.durationSeconds()>86400||u.priority()==null||!Set.of("P1","P2","P3","P4").contains(u.priority()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid rule configuration");var next=new Rule(id,old.name(),u.active(),u.threshold(),u.durationSeconds(),u.priority());record(id,old,next);rules.put(id,next);return next;}
 private final Map<String,Instant> exceededSince=new HashMap<>();
 private final Map<String,Map<String,Object>> evaluations=new LinkedHashMap<>();
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=10000)
 public synchronized void evaluateResources(){
 evaluateBatches();evaluateLogs();evaluateBusiness();
 var os=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
 var heap=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
 if(os instanceof com.sun.management.OperatingSystemMXBean m){double cpu=m.getCpuLoad();evaluate("CPU",cpu<0?Double.NaN:cpu*100);long total=m.getTotalMemorySize();evaluate("MEMORY",total>0?100.0*(total-m.getFreeMemorySize())/total:Double.NaN);}
 evaluate("HEAP",heap.getMax()>0?100.0*heap.getUsed()/heap.getMax():Double.NaN);
 try{var fs=java.nio.file.Files.getFileStore(java.nio.file.Path.of("/"));long total=fs.getTotalSpace();evaluate("DISK",total>0?100.0*(total-fs.getUsableSpace())/total:Double.NaN);}catch(java.io.IOException e){evaluate("DISK",Double.NaN);}
 }
 private void evaluate(String id,double value){var rule=rules.get(id);var now=Instant.now();boolean known=Double.isFinite(value);boolean exceeded=known&&value>=rule.threshold();if(!exceeded)exceededSince.remove(id);else exceededSince.putIfAbsent(id,now);long seconds=exceeded?java.time.Duration.between(exceededSince.get(id),now).getSeconds():0;boolean alert=exceeded&&seconds>=rule.durationSeconds();var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("value",known?value:null);row.put("status",!known?"UNKNOWN":alert?"ALERT":exceeded?"PENDING":"NORMAL");row.put("durationSeconds",seconds);row.put("incidentEligible",alert&&rule.active()&&controls.enabled()&&!controls.maintenance());row.put("timestamp",now);evaluations.put(id,row);}
 @GetMapping("/evaluations") public synchronized Map<String,Object> evaluations(HttpServletRequest r){authorize(r);return Map.of("scope","JVM-visible resources","incidentIntegration",false,"evaluations",List.copyOf(evaluations.values()));}
 private void evaluateBatches(){var now=Instant.now();var runs=batches.monitoringRuns();var latest=new HashMap<String,BatchMonitorController.Run>();for(var run:runs)latest.merge(run.jobId(),run,(a,b)->a.startedAt().isAfter(b.startedAt())?a:b);long failures=latest.values().stream().filter(x->"FAILED".equals(x.status())).count();long max=latest.values().stream().mapToLong(x->x.durationSeconds()==null?Math.max(0,java.time.Duration.between(x.startedAt(),now).getSeconds()):x.durationSeconds()).max().orElse(0);evaluate("BATCH_FAILURE",failures);evaluate("BATCH_SLA",max);}
 private void evaluateLogs(){for(String id:List.of("HTTP_500","JAVA_EXCEPTION")){var rule=rules.get(id);int window=Math.max(1,rule.durationSeconds());var cutoff=Instant.now().minusSeconds(window);long count=logs.search("","",Math.min(1440,(window+59)/60)).stream().filter(x->!x.timestamp().isBefore(cutoff)).filter(x->id.equals("HTTP_500")?x.status()==500:x.exceptionType()!=null).count();var row=new LinkedHashMap<String,Object>();boolean alert=count>=rule.threshold();row.put("id",id);row.put("value",count);row.put("status",alert?"ALERT":"NORMAL");row.put("durationSeconds",window);row.put("incidentEligible",alert&&rule.active()&&controls.enabled()&&!controls.maintenance());row.put("timestamp",Instant.now());evaluations.put(id,row);}}
 private void evaluateBusiness(){for(String id:List.of("PAYMENT_FAILURE","SETTLEMENT_FAILURE")){var rule=rules.get(id);int window=rule.durationSeconds()==0?60:rule.durationSeconds();long count=business.count(id.equals("PAYMENT_FAILURE")?"payment":"settlement",window);boolean alert=count>=rule.threshold();var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("value",count);row.put("status",alert?"ALERT":"NORMAL");row.put("durationSeconds",window);row.put("incidentEligible",alert&&rule.active()&&controls.enabled()&&!controls.maintenance());row.put("timestamp",Instant.now());evaluations.put(id,row);}}
}
