package br.pucminas.eduka.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Regras de aluno com isolamento por escola (RF05, RNF03).
 *
 * <p>
 * A escola nunca e parametro: sai de {@link IsolamentoEscola#escolaIdAtual()}
 * e entra em toda consulta como filtro. Um aluno de outra escola e, para o
 * usuario atual, indistinguivel de um aluno inexistente (404 nos dois casos).
 * {@code SUPER_ADMIN} e a unica excecao e enxerga todas as escolas.
 * </p>
 */
@Service
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final IsolamentoEscola isolamentoEscola;

    public AlunoService(AlunoRepository alunoRepository, IsolamentoEscola isolamentoEscola) {
        this.alunoRepository = alunoRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    public List<Aluno> listar() {
        if (isolamentoEscola.usuarioAtual().isSuperAdmin()) {
            return alunoRepository.findAll();
        }
        return alunoRepository.findAllByEscolaId(isolamentoEscola.escolaIdAtual());
    }

    public Aluno buscarPorId(UUID id) {
        if (isolamentoEscola.usuarioAtual().isSuperAdmin()) {
            return alunoRepository.findById(id).orElseThrow(AlunoService::naoEncontrado);
        }
        return alunoRepository.findByIdAndEscolaId(id, isolamentoEscola.escolaIdAtual())
                .orElseThrow(AlunoService::naoEncontrado);
    }

    /**
     * Cadastra na escola do usuario autenticado. O request nao carrega
     * escola: mesmo que carregasse, seria ignorado.
     */
    public Aluno cadastrar(AlunoRequest request) {
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do aluno e obrigatorio");
        }
        if (request.matricula() == null || request.matricula().isBlank()) {
            throw new IllegalArgumentException("Matricula do aluno e obrigatoria");
        }
        Aluno aluno = Aluno.builder()
                .escolaId(isolamentoEscola.escolaIdAtual())
                .nome(request.nome())
                .matricula(request.matricula())
                .emailResponsavel(request.emailResponsavel())
                .telefoneResponsavel(request.telefoneResponsavel())
                .genero(request.genero())
                .build();
        return alunoRepository.save(aluno);
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Aluno nao encontrado");
    }
}
