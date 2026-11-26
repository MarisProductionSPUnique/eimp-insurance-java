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
 private final ApiMetricsFilter apiMetrics;
 public SupportController(ApiMetricsFilter apiMetrics){this.apiMetrics=apiMetrics;}
 @GetMapping("/api-metrics") public java.util.List<Map<String,Object>> apiMetrics(HttpServletRequest request){authorize(request);return apiMetrics.snapshot();}
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

 @GetMapping("/metrics")
 public Map<String,Object> metrics(HttpServletRequest request){
  authorize(request);
  var result=new java.util.LinkedHashMap<String,Object>();
  var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
  var threads=ManagementFactory.getThreadMXBean();
  result.put("heapUsedBytes",heap.getUsed());result.put("heapMaxBytes",heap.getMax());
  result.put("heapCommittedBytes",heap.getCommitted());
  result.put("threads",threads.getThreadCount());result.put("peakThreads",threads.getPeakThreadCount());
  result.put("processors",Runtime.getRuntime().availableProcessors());
  var os=ManagementFactory.getOperatingSystemMXBean();
  if(os instanceof com.sun.management.OperatingSystemMXBean ext){
   result.put("systemCpuPercent",percent(ext.getCpuLoad()));
   result.put("processCpuPercent",percent(ext.getProcessCpuLoad()));
   result.put("memoryTotalBytes",ext.getTotalMemorySize());
   result.put("memoryFreeBytes",ext.getFreeMemorySize());
  }
  try{var fs=java.nio.file.Files.getFileStore(java.nio.file.Path.of("/"));result.put("diskTotalBytes",fs.getTotalSpace());result.put("diskUsableBytes",fs.getUsableSpace());}catch(java.io.IOException|SecurityException e){result.put("diskTotalBytes",null);result.put("diskUsableBytes",null);}
  result.put("scope","JVM-visible runtime and filesystem; not physical host metrics");
  result.put("timestamp",Instant.now().toString());return result;
 }
 private Double percent(double value){return value<0||!Double.isFinite(value)?null:Math.round(value*10000)/100.0;}
}
