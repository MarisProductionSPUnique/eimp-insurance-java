package com.uniq.eimp;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class ApiMetricsFilter extends OncePerRequestFilter {
 private final Map<String,Stats> routes=new LinkedHashMap<>();
 private static class Stats {
  long count,success,clientErrors,serverErrors,slow,totalMs,maxMs;
 }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String path=req.getRequestURI();
  if(!path.startsWith("/api/")||path.startsWith("/api/support/")){chain.doFilter(req,res);return;}
  long start=System.nanoTime();boolean failed=true;
  try{chain.doFilter(req,res);failed=false;}finally{
   long ms=(System.nanoTime()-start)/1000000;
   Object pattern=req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
   String method=req.getMethod();
   if(!Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS").contains(method))method="OTHER";
   record(method+" "+(pattern==null?"UNMATCHED":pattern.toString()),failed?500:res.getStatus(),ms);
  }
 }
 private synchronized void record(String key,int status,long ms){
  if(!routes.containsKey(key)&&routes.size()>=100)key="OTHER";
  Stats s=routes.computeIfAbsent(key,k->new Stats());s.count++;s.totalMs+=ms;s.maxMs=Math.max(s.maxMs,ms);
  if(status>=200&&status<300)s.success++;
  if(status>=400&&status<500)s.clientErrors++;
  if(status>=500)s.serverErrors++;
  if(ms>=1000)s.slow++;
 }
 public synchronized List<Map<String,Object>> snapshot(){
  List<Map<String,Object>> result=new ArrayList<>();
  routes.forEach((key,s)->result.add(Map.of("endpoint",key,"requests",s.count,"success",s.success,"clientErrors",s.clientErrors,"serverErrors",s.serverErrors,"slow",s.slow,"averageMs",Math.round(10.0*s.totalMs/s.count)/10.0,"maxMs",s.maxMs)));
  return result;
 }
}
