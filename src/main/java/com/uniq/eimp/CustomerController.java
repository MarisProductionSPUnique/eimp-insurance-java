package com.uniq.eimp;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    record Registration(String name, String email, String password) {}
    record Login(String email, String password) {}
    record Profile(String customerId, String name, String email) {}
    record Account(Profile profile, byte[] salt, byte[] hash) {}
    private final Map<String,Account> accounts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private byte[] hash(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private String email(String value) {
        if(value == null || value.length()>254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw bad("Invalid email");
        return value.toLowerCase(Locale.ROOT);
    }
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Profile register(@RequestBody Registration r) {
        String key = email(r.email());
        if(r.name()==null || r.name().isBlank() || r.name().length()>100) throw bad("Invalid name");
        if(r.password()==null || r.password().length()<12 || r.password().length()>128) throw bad("Password must contain 12 to 128 characters");
        if(accounts.size()>=1000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Training capacity reached");
        byte[] salt = new byte[16]; random.nextBytes(salt);
        Profile p = new Profile("EIMP-"+UUID.randomUUID(),r.name().strip(),key);
        if(accounts.putIfAbsent(key,new Account(p,salt,hash(r.password(),salt)))!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Registration unavailable");
        return p;
    }
    @PostMapping("/login")
    public Profile login(@RequestBody Login r, HttpServletRequest request) {
        String key = email(r.email());
        Account a = accounts.get(key);
        if(r.password()==null || r.password().length()>128) throw bad("Invalid credentials");
        byte[] salt = a==null ? new byte[16] : a.salt();
        byte[] candidate = hash(r.password(),salt);
        if(a==null || !MessageDigest.isEqual(a.hash(),candidate))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
        var old = request.getSession(false);
        if(old!=null) old.invalidate();
        var session = request.getSession(true);
        session.setMaxInactiveInterval(1800);
        session.setAttribute("customerEmail",key);
        session.setAttribute("expires",Instant.now().plusSeconds(3600));
        return a.profile();
    }
    @GetMapping("/me")
    public Profile me(HttpServletRequest request) {
        var session = request.getSession(false);
        if(session!=null && session.getAttribute("expires") instanceof Instant expires && expires.isAfter(Instant.now()) && session.getAttribute("customerEmail") instanceof String key) {
            Account a = accounts.get(key);
            if(a!=null) return a.profile();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login required");
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if(session!=null) session.invalidate();
    }
}
