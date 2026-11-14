package br.pucminas.eduka.backend.controller.escola;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.escola.UsoEscolaResponse;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.escola.UsoEscolaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Uso de cada escola no Painel de Escolas (IU02). Fica sob {@code /escolas},
 * entao so o SUPER_ADMIN entra; ve so contagens, nenhum dado de aluno ou
 * cobranca.
 */
@RestController
@PreAuthorize(Perfis.SUPER_ADMIN)
@Tag(name = "Escolas", description = "Escolas contratantes (tenants). Perfil: SUPER_ADMIN.")
public class UsoEscolaController {

    private final UsoEscolaService usoEscolaService;

    public UsoEscolaController(UsoEscolaService usoEscolaService) {
        this.usoEscolaService = usoEscolaService;
    }

    @GetMapping("/escolas/uso")
    @Operation(summary = "Uso de cada escola",
            description = "Um item por escola: alunos cadastrados, usuarios ativos, cobrancas lancadas no mes "
                    + "atual (fuso da escola) e a ultima atividade (ultimo login ou ultima acao de um usuario da "
                    + "escola). Perfil: SUPER_ADMIN.")
    public List<UsoEscolaResponse> listar() {
        return usoEscolaService.listar();
    }
}
