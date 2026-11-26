package com.uniq.eimp;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/simulator/java")
public class ExceptionSimulatorController {
 private long lastRun;
 @PostMapping("/{type}")
 public synchronized ResponseEntity<Map<String,Object>> simulate(@PathVariable String type,HttpServletRequest request){
  var session=request.getSession(false);
  if(session==null||!(session.getAttribute("supportExpiry") instanceof Instant expiry)||!Instant.now().isBefore(expiry))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  Exception error=switch(type){
   case "null-pointer" -> new NullPointerException("Training: missing customer object");
   case "sql" -> new java.sql.SQLException("Training: database operation failed");
   case "connection" -> new java.net.ConnectException("Training: connection refused");
   case "timeout" -> new java.net.SocketTimeoutException("Training: upstream timeout");
   case "number-format" -> new NumberFormatException("Training: invalid premium value");
   case "illegal-argument" -> new IllegalArgumentException("Training: invalid policy argument");
   case "file-not-found" -> new java.io.FileNotFoundException("Training: missing batch input");
   case "arithmetic" -> new ArithmeticException("Training: division by zero");
   default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  };
  long now=System.nanoTime();
  if(lastRun!=0&&now-lastRun<1000000000L)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
  lastRun=now;
  try{throw error;}catch(Exception e){
   request.setAttribute("eimp.exceptionType",e.getClass().getSimpleName());
   return ResponseEntity.status(500).body(Map.of("simulation",true,"exceptionType",e.getClass().getSimpleName(),"message",e.getMessage()));
  }
 }
}
