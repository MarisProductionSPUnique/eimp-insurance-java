package com.uniq.eimp;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    @Value("${EIMP_EMPLOYEE_PASSWORD:}") private String password;
    @Value("${EIMP_EMPLOYEE_USERNAME:underwriter}") private String username;
    public record Login(String username,String password) {}
    public record Employee(String username,String role) {}
    private int failures=0;
    private Instant blockedUntil=Instant.EPOCH;
    @PostMapping("/login")
    public synchronized Employee login(@RequestBody Login input,HttpServletRequest request) {
        if(password.length()<16) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Employee access not configured");
        if(Instant.now().isBefore(blockedUntil)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        if(input.username()==null || input.password()==null || input.password().length()>256 || !username.equals(input.username()) || !MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),input.password().getBytes(StandardCharsets.UTF_8))) {
            if(++failures>=5){blockedUntil=Instant.now().plusSeconds(60);failures=0;}
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        failures=0;
        var old=request.getSession(false);if(old!=null)old.invalidate();
        var session=request.getSession(true);session.setMaxInactiveInterval(1800);
        session.setAttribute("employeeRole","UNDERWRITER");session.setAttribute("employeeExpiry",Instant.now().plusSeconds(3600));
        return new Employee(username,"UNDERWRITER");
    }
    @GetMapping("/me")
    public Employee me(HttpServletRequest request){
        var session=request.getSession(false);
        if(session==null || !"UNDERWRITER".equals(session.getAttribute("employeeRole")) || !(session.getAttribute("employeeExpiry") instanceof Instant expiry) || !Instant.now().isBefore(expiry)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return new Employee(username,"UNDERWRITER");
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request){me(request);request.getSession(false).invalidate();}
}
