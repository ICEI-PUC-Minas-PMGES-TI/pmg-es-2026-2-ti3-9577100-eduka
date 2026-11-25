package br.pucminas.eduka.backend.controller.escola;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.eduka.backend.dto.escola.ContatoEscolaResponse;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.Perfis;
import br.pucminas.eduka.backend.service.escola.EscolaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Contato da escola do token, para a Ajuda: o aluno, o professor e a equipe
 * sabem a quem recorrer na propria escola. A escola vem do token, nunca de
 * parametro.
 */
@RestController
@Tag(name = "Minha escola", description = "A escola do proprio usuario. Perfis da escola.")
public class ContatoEscolaController {

    private final EscolaService escolaService;
    private final IsolamentoEscola isolamentoEscola;

    public ContatoEscolaController(EscolaService escolaService, IsolamentoEscola isolamentoEscola) {
        this.escolaService = escolaService;
        this.isolamentoEscola = isolamentoEscola;
    }

    @GetMapping("/minha-escola/contato")
    @PreAuthorize(Perfis.DA_ESCOLA)
    @Operation(summary = "Contato da minha escola",
            description = "Nome, e-mail, telefone, cidade e estado da escola do token, para a Ajuda. Campos sem "
                    + "cadastro vem nulos. Perfis: MASTER, SECRETARIA, FINANCEIRO, PROFESSOR e ALUNO.")
    public ContatoEscolaResponse contato() {
        return escolaService.contato(isolamentoEscola.escolaIdAtual());
    }
}
