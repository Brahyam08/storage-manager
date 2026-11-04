package com.brahyam.storagemanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
    // Only UI routes receive the SPA shell. API and missing asset routes keep their own responses.
    @GetMapping({"/", "/acceso", "/registro", "/inventario"})
    public String application() {
        return "forward:/index.html";
    }
}
