package com.brahyam.storagemanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
    // Only UI routes receive the SPA shell. API and missing asset routes keep their own responses.
    @GetMapping({"/", "/acceso", "/registro", "/inventario", "/perfil", "/objetos/{id:[0-9]+}"})
    public String application() {
        return "forward:/index.html";
    }

    @GetMapping("/manifest.webmanifest")
    @ResponseBody
    public ResponseEntity<Resource> manifest() {
        Resource manifest = new ClassPathResource("static/manifest.webmanifest");
        if (!manifest.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/manifest+json"))
                .body(manifest);
    }
}
