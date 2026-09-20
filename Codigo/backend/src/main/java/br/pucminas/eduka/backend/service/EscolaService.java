package br.pucminas.eduka.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domains.enums.Status;
import br.pucminas.eduka.backend.domains.model.Escola;
import br.pucminas.eduka.backend.dto.EscolaRequest;
import br.pucminas.eduka.backend.exception.CnpjJaCadastradoException;
import br.pucminas.eduka.backend.repository.EscolaRepository;

/**
 * Cadastro de escolas (tenants). So o super admin da plataforma cria escolas
 * novas; o isolamento por tenant nao se aplica aqui, porque o recurso e a
 * propria escola, nao um dado dentro dela.
 */
@Service
public class EscolaService {

    private final EscolaRepository escolaRepository;

    public EscolaService(EscolaRepository escolaRepository) {
        this.escolaRepository = escolaRepository;
    }

    @Transactional
    public Escola cadastrar(EscolaRequest request) {
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome da escola e obrigatorio");
        }

        String cnpj = vazioParaNulo(request.cnpj());
        if (cnpj != null && escolaRepository.existsByCnpj(cnpj)) {
            throw new CnpjJaCadastradoException(cnpj);
        }

        Escola escola = Escola.builder()
                .nome(request.nome().trim())
                .cnpj(cnpj)
                .endereco(vazioParaNulo(request.endereco()))
                .email(vazioParaNulo(request.email()))
                .telefone(vazioParaNulo(request.telefone()))
                .status(request.status() != null ? request.status() : Status.ATIVO)
                .build();
        return escolaRepository.save(escola);
    }

    private static String vazioParaNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
