package br.pucminas.eduka.backend.dto.escola;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.escola.ModuloEscola;
import br.pucminas.eduka.backend.domain.escola.PlanoEscola;
import br.pucminas.eduka.backend.dto.comum.EnderecoDto;

public record EscolaResponse(
        UUID id,
        String nome,
        String cnpjCpf,
        EnderecoDto endereco,
        String email,
        String telefone,
        PlanoEscola plano,
        boolean ativo,
        LocalDate inicioVigencia,
        LocalDate fimVigencia,
        Integer limiteUsuarios,
        List<ModuloEscola> modulos,
        Instant criadoEm) {

    public static EscolaResponse from(Escola escola) {
        return new EscolaResponse(
                escola.getId(),
                escola.getNome(),
                escola.getCnpjCpf(),
                EnderecoDto.from(escola.getEndereco()),
                escola.getEmail(),
                escola.getTelefone(),
                escola.getPlano(),
                escola.isAtivo(),
                escola.getInicioVigencia(),
                escola.getFimVigencia(),
                escola.getLimiteUsuarios(),
                // Ordem do enum, para a resposta nao variar entre chamadas.
                escola.getModulos().stream().sorted().toList(),
                escola.getCriadoEm());
    }
}
