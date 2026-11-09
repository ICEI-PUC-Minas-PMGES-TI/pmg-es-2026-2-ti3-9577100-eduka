package br.pucminas.eduka.backend.service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.DiaSemana;
import br.pucminas.eduka.backend.domain.HorarioTurma;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.Professor;
import br.pucminas.eduka.backend.domain.Turma;
import br.pucminas.eduka.backend.dto.AtualizarHorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.HorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.HorarioTurmaResponse;
import br.pucminas.eduka.backend.exception.HorarioConflitanteException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.ProfessorRepository;
import br.pucminas.eduka.backend.repository.TurmaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Grade semanal das turmas (IU26), montada a partir das alocacoes de
 * professor. Turma e alocacao sao sempre buscadas com a escola do token; as
 * de outra escola respondem 404.
 *
 * <p>
 * Regras: a alocacao precisa ser da turma da URL (400 se for de outra turma);
 * o inicio vem antes do fim; no mesmo dia nao pode haver sobreposicao na
 * turma nem para o professor, em qualquer turma (409 com {@code codigo}
 * proprio). Aulas que so se encostam (fim de uma = inicio da outra) nao
 * conflitam.
 * </p>
 *
 * <p>
 * Nao ha constraint de sobreposicao no banco (turma e professor estao na
 * alocacao, nao na linha do horario). Para duas gravacoes simultaneas nao
 * passarem juntas pela conferencia, quem grava trava a turma e o professor,
 * sempre nessa ordem.
 * </p>
 */
@Service
public class HorarioTurmaService {

    /** Segunda a sabado, depois pela hora de inicio. */
    private static final Comparator<HorarioTurma> ORDEM_DA_GRADE = Comparator
            .comparing(HorarioTurma::getDiaSemana)
            .thenComparing(HorarioTurma::getHoraInicio);

    private final HorarioTurmaRepository horarioRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final TurmaRepository turmaRepository;
    private final ProfessorRepository professorRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public HorarioTurmaService(HorarioTurmaRepository horarioRepository,
            AlocacaoProfessorRepository alocacaoRepository, TurmaRepository turmaRepository,
            ProfessorRepository professorRepository, PessoaRepository pessoaRepository,
            IsolamentoEscola isolamentoEscola) {
        this.horarioRepository = horarioRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.turmaRepository = turmaRepository;
        this.professorRepository = professorRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /** A grade da turma, de segunda a sabado e por hora de inicio. */
    @Transactional(readOnly = true)
    public List<HorarioTurmaResponse> listar(Long turmaId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Turma turma = turmaRepository.findByIdAndEscolaId(turmaId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma nao encontrada"));
        List<HorarioTurma> aulas = horarioRepository.findAllByEscolaIdAndAlocacao_Turma_Id(escolaId, turma.getId())
                .stream().sorted(ORDEM_DA_GRADE).toList();
        return responder(aulas, escolaId);
    }

    /**
     * Acrescenta uma aula na grade da turma.
     *
     * @throws HorarioConflitanteException se sobrepoe outra aula da turma ou do professor
     */
    @Transactional
    public HorarioTurmaResponse criar(Long turmaId, HorarioTurmaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Turma turma = travarTurma(turmaId, escolaId);
        validarIntervalo(request.horaInicio(), request.horaFim());
        AlocacaoProfessor alocacao = alocacaoRepository.findByIdAndEscolaId(request.alocacaoId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao nao encontrada"));
        if (!alocacao.getTurma().getId().equals(turma.getId())) {
            throw new IllegalArgumentException("Alocacao nao pertence a turma");
        }
        Professor professor = travarProfessor(alocacao, escolaId);
        conferirSobreposicao(escolaId, turma.getId(), professor.getId(), request.diaSemana(), request.horaInicio(),
                request.horaFim(), null);

        HorarioTurma aula = horarioRepository.saveAndFlush(HorarioTurma.builder()
                .escolaId(escolaId)
                .alocacao(alocacao)
                .diaSemana(request.diaSemana())
                .horaInicio(request.horaInicio())
                .horaFim(request.horaFim())
                .build());
        return responder(List.of(aula), escolaId).get(0);
    }

    /** Muda o dia e o horario de uma aula; disciplina e professor ficam os mesmos. */
    @Transactional
    public HorarioTurmaResponse atualizar(Long turmaId, UUID horarioId, AtualizarHorarioTurmaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Turma turma = travarTurma(turmaId, escolaId);
        HorarioTurma aula = buscarAula(horarioId, turma, escolaId);
        validarIntervalo(request.horaInicio(), request.horaFim());
        Professor professor = travarProfessor(aula.getAlocacao(), escolaId);
        conferirSobreposicao(escolaId, turma.getId(), professor.getId(), request.diaSemana(), request.horaInicio(),
                request.horaFim(), aula.getId());

        aula.setDiaSemana(request.diaSemana());
        aula.setHoraInicio(request.horaInicio());
        aula.setHoraFim(request.horaFim());
        aula.setAtualizadoEm(Instant.now());
        return responder(List.of(horarioRepository.saveAndFlush(aula)), escolaId).get(0);
    }

    @Transactional
    public void remover(Long turmaId, UUID horarioId) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Turma turma = turmaRepository.findByIdAndEscolaId(turmaId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma nao encontrada"));
        horarioRepository.delete(buscarAula(horarioId, turma, escolaId));
    }

    private Turma travarTurma(Long turmaId, UUID escolaId) {
        return turmaRepository.findComLockByIdAndEscolaId(turmaId, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma nao encontrada"));
    }

    private Professor travarProfessor(AlocacaoProfessor alocacao, UUID escolaId) {
        return professorRepository.findComLockByIdAndEscolaId(alocacao.getProfessor().getId(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));
    }

    private HorarioTurma buscarAula(UUID horarioId, Turma turma, UUID escolaId) {
        return horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(horarioId, escolaId, turma.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aula nao encontrada"));
    }

    private static void validarIntervalo(LocalTime inicio, LocalTime fim) {
        if (!inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O horario de inicio deve ser anterior ao de fim");
        }
    }

    /**
     * Confere as aulas do mesmo dia da turma e do professor. {@code ignorarId}
     * e a aula que esta sendo editada, que nao conflita consigo mesma.
     */
    private void conferirSobreposicao(UUID escolaId, Long turmaId, UUID professorId, DiaSemana dia,
            LocalTime inicio, LocalTime fim, UUID ignorarId) {
        boolean conflitoNaTurma = horarioRepository
                .findAllByEscolaIdAndAlocacao_Turma_IdAndDiaSemana(escolaId, turmaId, dia).stream()
                .anyMatch(outra -> sobrepoe(outra, inicio, fim, ignorarId));
        if (conflitoNaTurma) {
            throw HorarioConflitanteException.daTurma();
        }
        boolean conflitoDoProfessor = horarioRepository
                .findAllByEscolaIdAndAlocacao_Professor_IdAndDiaSemana(escolaId, professorId, dia).stream()
                .anyMatch(outra -> sobrepoe(outra, inicio, fim, ignorarId));
        if (conflitoDoProfessor) {
            throw HorarioConflitanteException.doProfessor();
        }
    }

    /** Intervalos abertos nas pontas: fim igual ao inicio da outra aula nao e sobreposicao. */
    private static boolean sobrepoe(HorarioTurma outra, LocalTime inicio, LocalTime fim, UUID ignorarId) {
        if (outra.getId().equals(ignorarId)) {
            return false;
        }
        return inicio.isBefore(outra.getHoraFim()) && outra.getHoraInicio().isBefore(fim);
    }

    private List<HorarioTurmaResponse> responder(List<HorarioTurma> aulas, UUID escolaId) {
        if (aulas.isEmpty()) {
            return List.of();
        }
        List<UUID> pessoaIds = aulas.stream()
                .map(aula -> aula.getAlocacao().getProfessor().getPessoaId())
                .distinct()
                .toList();
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));
        return aulas.stream()
                .map(aula -> HorarioTurmaResponse.de(aula,
                        pessoasPorId.get(aula.getAlocacao().getProfessor().getPessoaId())))
                .toList();
    }
}
