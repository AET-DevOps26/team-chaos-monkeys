package com.foundflow.auth;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class HelloController {

    @GetMapping(value = "/greet", produces = MediaType.TEXT_PLAIN_VALUE)
    public String greet() {
        return "Hello from FoundFlow!";
    }
}
