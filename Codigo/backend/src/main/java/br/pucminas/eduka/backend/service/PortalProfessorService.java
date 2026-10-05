package br.pucminas.eduka.backend.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse;
import br.pucminas.eduka.backend.dto.TurmaDoProfessorResponse.DisciplinaDoProfessor;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Area pessoal do professor: o que ele enxerga e sempre o que esta alocado
 * a ele (ver {@link AlocacaoProfessorService}).
 *
 * <p>
 * O professor nunca e parametro: sai do login do token, assim como a escola.
 * Nao ha como pedir as turmas de outro professor por aqui.
 * </p>
 */
@Service
public class PortalProfessorService {

    private final ProfessorRepository professorRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final IsolamentoEscola isolamentoEscola;

    public PortalProfessorService(ProfessorRepository professorRepository,
            AlocacaoProfessorRepository alocacaoRepository, IsolamentoEscola isolamentoEscola) {
        this.professorRepository = professorRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /**
     * Turmas em que o professor autenticado leciona, cada uma so com as
     * disciplinas dele. Ordenado por nome da turma e da disciplina; sem
     * alocacao, lista vazia.
     *
     * @throws RecursoNaoEncontradoException se o login nao e de um professor
     *         cadastrado na escola
     */
    @Transactional(readOnly = true)
    public List<TurmaDoProfessorResponse> listarMinhasTurmas() {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        UUID escolaId = usuario.escolaId();
        Professor professor = professorRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));

        Map<Long, List<AlocacaoProfessor>> porTurma = alocacaoRepository
                .findAllComTurmaEDisciplinaByEscolaIdAndProfessorId(escolaId, professor.getId()).stream()
                .collect(Collectors.groupingBy(alocacao -> alocacao.getTurma().getId(), LinkedHashMap::new,
                        Collectors.toList()));

        return porTurma.values().stream()
                .map(PortalProfessorService::paraResposta)
                .sorted(Comparator.comparing(TurmaDoProfessorResponse::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static TurmaDoProfessorResponse paraResposta(List<AlocacaoProfessor> alocacoesDaTurma) {
        Turma turma = alocacoesDaTurma.get(0).getTurma();
        List<DisciplinaDoProfessor> disciplinas = alocacoesDaTurma.stream()
                .map(alocacao -> DisciplinaDoProfessor.de(alocacao.getDisciplina()))
                .sorted(Comparator.comparing(DisciplinaDoProfessor::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return TurmaDoProfessorResponse.de(turma, disciplinas);
    }
}
