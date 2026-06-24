package com.irp.HR.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class test {

    @GetMapping("/")
    public String home() {
        return "HR Backend Running Successfully!";
    }
}