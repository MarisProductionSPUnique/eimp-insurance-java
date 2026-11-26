package com.uniq.eimp;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class AccessLogFilter extends OncePerRequestFilter {
 public record Entry(Instant timestamp,String level,String method,String endpoint,int status,long durationMs,String correlationId){}
 private final Deque<Entry> entries=new ArrayDeque<>();
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  if(!req.getRequestURI().startsWith("/api/")||req.getRequestURI().startsWith("/api/support/")){chain.doFilter(req,res);return;}
  String id=UUID.randomUUID().toString();res.setHeader("X-Correlation-ID",id);
  long start=System.nanoTime();boolean failed=true;
  try{chain.doFilter(req,res);failed=false;}finally{
   int status=failed?500:res.getStatus();
   Object pattern=req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
   String method=req.getMethod();
   if(!Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS").contains(method))method="OTHER";
   append(new Entry(Instant.now(),status>=500?"ERROR":status>=400?"WARN":"INFO",method,pattern==null?"UNMATCHED":pattern.toString(),status,(System.nanoTime()-start)/1000000,id));
  }
 }
 private synchronized void append(Entry e){entries.addFirst(e);while(entries.size()>2000)entries.removeLast();}
 public synchronized List<Entry> search(String keyword,String level,int minutes){
  Instant cutoff=Instant.now().minusSeconds(Math.max(1,Math.min(1440,minutes))*60L);
  String q=keyword==null?"":keyword.toLowerCase(Locale.ROOT);
  return entries.stream().filter(e->!e.timestamp().isBefore(cutoff)).filter(e->level==null||level.isBlank()||level.equalsIgnoreCase(e.level())).filter(e->e.toString().toLowerCase(Locale.ROOT).contains(q)).limit(500).toList();
 }
}
