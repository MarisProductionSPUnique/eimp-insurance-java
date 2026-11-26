package com.uniq.eimp;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class HealthController {
 @GetMapping("/welcome") public Map<String,String> welcome(){return Map.of("application","Enterprise Insurance Management Platform","message","Welcome to EIMP","mode","TRAINING");}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("application","EIMP","status","UP","database","NOT_CONFIGURED");}
}
