package br.pucminas.eduka.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health")
@SecurityRequirements
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Verifica se a API esta no ar", description = "Responde `UP`. Rota publica.")
    public String health() {
        return "UP";
    }

}
