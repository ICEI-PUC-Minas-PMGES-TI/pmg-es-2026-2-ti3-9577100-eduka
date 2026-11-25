package br.pucminas.eduka.backend.service.academico;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.academico.Aula;
import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.Frequencia;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.dto.academico.AlunoDaChamadaResponse;
import br.pucminas.eduka.backend.dto.academico.AtualizarAulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaResponse;
import br.pucminas.eduka.backend.dto.academico.AulaResumoResponse;
import br.pucminas.eduka.backend.dto.academico.ChamadaDoDiaResponse;
import br.pucminas.eduka.backend.dto.academico.MesDeAulasResponse;
import br.pucminas.eduka.backend.dto.academico.ChamadaItem;
import br.pucminas.eduka.backend.dto.academico.FrequenciaAlunoResponse;
import br.pucminas.eduka.backend.dto.comum.Ordenacao;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AulaRepository;
import br.pucminas.eduka.backend.repository.academico.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Aulas e chamada, pelo professor autenticado (portal do professor).
 *
 * <p>
 * O professor e a escola saem do token. Ele so enxerga e altera aulas das
 * disciplinas em que esta alocado hoje (ver {@link AlocacaoProfessorService});
 * fora disso, a turma/disciplina ou a aula respondem 404, como se nao
 * existissem. Assim, quando a secretaria troca o professor de uma disciplina,
 * as aulas passam para o novo professor.
 * </p>
 *
 * <p>
 * Regras da chamada:
 * </p>
 * <ul>
 * <li>A data da aula nao pode ser futura (dia de hoje no fuso da escola).</li>
 * <li>A chamada e completa: todo aluno com matricula ATIVA na turma, feita ate
 * o dia da aula, entra na lista, uma vez cada. Faltar aluno na lista e 400,
 * para a frequencia nao ficar com buracos que distorcem o percentual de
 * faltas.</li>
 * <li>Aluno sem matricula ativa na turma, ou matriculado depois do dia da
 * aula, e 400 (nao estava na turma naquele dia, entao nao ganha falta). Ao
 * corrigir uma aula, quem ja
 * tinha presenca registrada nela e saiu da turma depois ainda pode ser
 * corrigido, mas deixa de ser exigido.</li>
 * <li>Corrigir substitui o conteudo e a presenca de cada aluno enviado; a data
 * nao muda.</li>
 * <li>Aula com data errada ou em duplicidade se desfaz excluindo-a, com a
 * chamada dela.</li>
 * </ul>
 */
@Service
public class AulaService {

    /** A escola conta o dia no horario de Brasilia, nao em UTC. */
    private static final ZoneId FUSO_ESCOLA = ZoneId.of("America/Sao_Paulo");
    /** Pontas abertas do filtro de datas do historico. */
    private static final LocalDate PRIMEIRO_DIA = LocalDate.of(1900, 1, 1);
    private static final LocalDate ULTIMO_DIA = LocalDate.of(9999, 12, 31);
    /** Tamanho maximo do texto da busca por conteudo do historico. */
    static final int MAX_BUSCA = 100;

    private final AulaRepository aulaRepository;
    private final FrequenciaRepository frequenciaRepository;
    private final ProfessorRepository professorRepository;
    private final AlocacaoProfessorRepository alocacaoRepository;
    private final HorarioTurmaRepository horarioRepository;
    private final TurmaRepository turmaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final MatriculaTurmaRepository matriculaRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;
    private final AuditoriaService auditoriaService;

    public AulaService(AulaRepository aulaRepository, FrequenciaRepository frequenciaRepository,
            ProfessorRepository professorRepository, AlocacaoProfessorRepository alocacaoRepository,
            HorarioTurmaRepository horarioRepository, TurmaRepository turmaRepository, DisciplinaRepository disciplinaRepository,
            MatriculaTurmaRepository matriculaRepository, PessoaRepository pessoaRepository,
            IsolamentoEscola isolamentoEscola, Clock clock, AuditoriaService auditoriaService) {
        this.aulaRepository = aulaRepository;
        this.frequenciaRepository = frequenciaRepository;
        this.professorRepository = professorRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.horarioRepository = horarioRepository;
        this.turmaRepository = turmaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.matriculaRepository = matriculaRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Pagina das aulas da disciplina na turma, por data (a mais recente
     * primeiro, salvo {@code direcao}), cada uma com os totais da chamada.
     * {@code de} e {@code ate} (inclusive) limitam as datas e {@code busca}, o
     * conteudo (trecho, sem diferenciar maiusculas); nulos, nao limitam.
     *
     * @throws IllegalArgumentException {@code de} depois de {@code ate} ou busca longa demais
     */
    @Transactional(readOnly = true)
    public PaginaResponse<AulaResumoResponse> listar(Long turmaId, Long disciplinaId, LocalDate de, LocalDate ate,
            String busca, Paginacao paginacao, Direction direcao) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirAlocacao(professorAtual(escolaId), escolaId, turmaId, disciplinaId);
        validarFiltro(de, ate, busca);

        var ordem = paginacao.comOrdem(Ordenacao.noBanco(Ordenacao.ou(direcao, Direction.DESC), "data"));
        var pagina = de == null && ate == null && semBusca(busca)
                ? aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaId(escolaId, turmaId, disciplinaId, ordem)
                // Uma ponta so: a outra fica aberta, num limite que o DATE do Postgres aceita.
                : aulaRepository.filtrar(escolaId, turmaId, disciplinaId, de != null ? de : PRIMEIRO_DIA,
                        ate != null ? ate : ULTIMO_DIA, padraoDaBusca(busca), ordem);
        if (pagina.isEmpty()) {
            return PaginaResponse.de(pagina, aula -> AulaResumoResponse.de(aula, 0, 0));
        }
        Map<UUID, FrequenciaRepository.TotaisDaAula> totais = totaisPorAula(escolaId, pagina.getContent());
        return PaginaResponse.de(pagina, aula -> resumo(aula, totais));
    }

    /**
     * Os meses com aula no historico da disciplina na turma (o mais recente
     * primeiro), com os mesmos filtros de {@link #listar}: quantas aulas e o
     * total de presencas e faltas de cada mes. A tela carrega o historico mes a
     * mes e soma estes totais para o resumo.
     *
     * @throws IllegalArgumentException {@code de} depois de {@code ate} ou busca longa demais
     */
    @Transactional(readOnly = true)
    public List<MesDeAulasResponse> meses(Long turmaId, Long disciplinaId, LocalDate de, LocalDate ate,
            String busca) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirAlocacao(professorAtual(escolaId), escolaId, turmaId, disciplinaId);
        validarFiltro(de, ate, busca);
        return aulaRepository.contarPorMes(escolaId, turmaId, disciplinaId, de != null ? de : PRIMEIRO_DIA,
                ate != null ? ate : ULTIMO_DIA, padraoDaBusca(busca)).stream()
                .map(MesDeAulasResponse::de)
                .toList();
    }

    private static void validarFiltro(LocalDate de, LocalDate ate, String busca) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("A data inicial nao pode ser depois da final");
        }
        if (busca != null && busca.trim().length() > MAX_BUSCA) {
            throw new IllegalArgumentException("A busca pode ter no maximo " + MAX_BUSCA + " caracteres");
        }
    }

    private static boolean semBusca(String busca) {
        return busca == null || busca.isBlank();
    }

    /** "Logaritmo" vira "%logaritmo%"; %, _ e \\ digitados valem como texto. Sem busca, "%". */
    static String padraoDaBusca(String busca) {
        if (semBusca(busca)) {
            return "%";
        }
        String texto = busca.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + texto + "%";
    }

    /**
     * O que a tela de chamada precisa para um dia: se ele e dia de aula da
     * disciplina na grade da turma, quem entra na chamada (as mesmas regras do
     * registro) e as aulas ja registradas nele. Sem {@code data}, vale hoje no
     * fuso da escola.
     *
     * @throws IllegalArgumentException data futura
     */
    @Transactional(readOnly = true)
    public ChamadaDoDiaResponse chamadaDoDia(Long turmaId, Long disciplinaId, LocalDate data) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        exigirAlocacao(professorAtual(escolaId), escolaId, turmaId, disciplinaId);
        LocalDate dia = data != null ? data : hoje();
        if (dia.isAfter(hoje())) {
            throw new IllegalArgumentException("A data da aula nao pode ser futura");
        }

        List<Aula> aulas = aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaIdAndData(escolaId, turmaId,
                disciplinaId, dia, Ordenacao.noBanco(Direction.ASC, "criadoEm"));
        Map<UUID, FrequenciaRepository.TotaisDaAula> totais = aulas.isEmpty() ? Map.of()
                : totaisPorAula(escolaId, aulas);
        return new ChamadaDoDiaResponse(dia, diaDeAula(escolaId, turmaId, disciplinaId, dia),
                alunosDaChamada(escolaId, turmaId, dia),
                aulas.stream().map(aula -> resumo(aula, totais)).toList());
    }

    @Transactional(readOnly = true)
    public AulaResponse buscar(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aula aula = buscarAula(id, escolaId, professorAtual(escolaId));
        return AulaResponse.de(aula, montarChamada(aula, escolaId));
    }

    /**
     * Registra a aula com a chamada, tudo ou nada.
     *
     * @throws IllegalArgumentException data futura, dia sem aula da disciplina
     *                                  na grade da turma, aluno repetido,
     *                                  aluno sem matricula ativa na turma ou
     *                                  aluno ativo fora da chamada
     */
    @Transactional
    public AulaResponse registrar(AulaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Professor professor = professorAtual(escolaId);
        exigirAlocacao(professor, escolaId, request.turmaId(), request.disciplinaId());
        if (request.data().isAfter(hoje())) {
            throw new IllegalArgumentException("A data da aula nao pode ser futura");
        }
        if (!diaDeAula(escolaId, request.turmaId(), request.disciplinaId(), request.data())) {
            throw new IllegalArgumentException("Nao ha aula desta disciplina na turma neste dia da semana");
        }
        Map<UUID, MatriculaTurma> ativos = matriculasAtivas(escolaId, request.turmaId(), request.data());
        validarChamada(request.chamada(), ativos, Set.of());

        Aula aula = aulaRepository.saveAndFlush(Aula.builder()
                .escolaId(escolaId)
                .turma(turmaRepository.getReferenceById(request.turmaId()))
                .disciplina(disciplinaRepository.getReferenceById(request.disciplinaId()))
                .professor(professor)
                .data(request.data())
                .conteudo(request.conteudo().trim())
                .build());
        frequenciaRepository.saveAll(request.chamada().stream()
                .map(item -> Frequencia.builder()
                        .escolaId(escolaId)
                        .aula(aula)
                        .aluno(ativos.get(item.alunoId()).getAluno())
                        .presente(item.presente())
                        .build())
                .toList());
        frequenciaRepository.flush();
        // A chamada nasce com a aula: um registro so, nao um por aluno.
        auditoriaService.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.AULA, aula.getId());
        return resposta(aula.getId(), escolaId);
    }

    /**
     * Corrige o conteudo e a chamada de uma aula.
     *
     * @throws IllegalArgumentException aluno repetido, aluno sem matricula
     *                                  ativa na turma (e sem presenca ja
     *                                  registrada nesta aula) ou aluno ativo
     *                                  fora da chamada
     */
    @Transactional
    public AulaResponse atualizar(UUID id, AtualizarAulaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aula aula = buscarAula(id, escolaId, professorAtual(escolaId));

        Map<UUID, MatriculaTurma> ativos = matriculasAtivas(escolaId, aula.getTurma().getId(), aula.getData());
        Map<UUID, Frequencia> registradas = frequenciaRepository.findAllByEscolaIdAndAulaId(escolaId, id).stream()
                .collect(Collectors.toMap(f -> f.getAluno().getId(), Function.identity()));
        validarChamada(request.chamada(), ativos, registradas.keySet());

        Instant agora = clock.instant();
        aula.setConteudo(request.conteudo().trim());
        aula.setAtualizadoEm(agora);
        aulaRepository.save(aula);

        List<Frequencia> novas = new ArrayList<>();
        List<Frequencia> corrigidas = new ArrayList<>();
        for (ChamadaItem item : request.chamada()) {
            Frequencia existente = registradas.get(item.alunoId());
            if (existente == null) {
                novas.add(Frequencia.builder()
                        .escolaId(escolaId)
                        .aula(aula)
                        .aluno(ativos.get(item.alunoId()).getAluno())
                        .presente(item.presente())
                        .build());
            } else if (existente.isPresente() != item.presente()) {
                existente.setPresente(item.presente());
                existente.setAtualizadoEm(agora);
                corrigidas.add(existente);
            }
        }
        frequenciaRepository.saveAll(novas);
        frequenciaRepository.flush();
        // A correcao fica por aluno: quem teve a presenca trocada e quem entrou na chamada agora.
        auditoriaService.registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.AULA, aula.getId());
        corrigidas.forEach(f -> auditoriaService.registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.FREQUENCIA,
                f.getId()));
        novas.forEach(f -> auditoriaService.registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.FREQUENCIA,
                f.getId()));
        return resposta(id, escolaId);
    }

    /**
     * Apaga a aula e a chamada dela, na mesma transacao. E o jeito de desfazer
     * uma aula registrada com data errada ou em duplicidade, que senao
     * distorceria para sempre o percentual de faltas dos alunos.
     */
    @Transactional
    public void excluir(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aula aula = buscarAula(id, escolaId, professorAtual(escolaId));
        frequenciaRepository.apagarDaAula(escolaId, id);
        aulaRepository.delete(aula);
        auditoriaService.registrar(OperacaoAuditoria.EXCLUIR, EntidadeAuditada.AULA, id);
    }

    /**
     * A chamada vale se traz cada aluno uma vez, so alunos da turma (ativos, ou
     * com presenca ja registrada nesta aula) e todos os ativos.
     */
    private static void validarChamada(List<ChamadaItem> chamada, Map<UUID, MatriculaTurma> ativos,
            Set<UUID> jaRegistrados) {
        Set<UUID> enviados = new HashSet<>();
        for (ChamadaItem item : chamada) {
            if (!enviados.add(item.alunoId())) {
                throw new IllegalArgumentException("Aluno repetido na chamada");
            }
            if (!ativos.containsKey(item.alunoId()) && !jaRegistrados.contains(item.alunoId())) {
                throw new IllegalArgumentException("Aluno sem matricula ativa na turma da aula");
            }
        }
        long ausentes = ativos.keySet().stream().filter(aluno -> !enviados.contains(aluno)).count();
        if (ausentes > 0) {
            throw new IllegalArgumentException(
                    "Chamada incompleta: %d aluno(s) com matricula ativa na turma ficaram fora da lista"
                            .formatted(ausentes));
        }
    }

    private Map<UUID, FrequenciaRepository.TotaisDaAula> totaisPorAula(UUID escolaId, List<Aula> aulas) {
        return frequenciaRepository.contarPorAula(escolaId, aulas.stream().map(Aula::getId).toList()).stream()
                .collect(Collectors.toMap(FrequenciaRepository.TotaisDaAula::getAulaId, Function.identity()));
    }

    private static AulaResumoResponse resumo(Aula aula, Map<UUID, FrequenciaRepository.TotaisDaAula> totais) {
        FrequenciaRepository.TotaisDaAula total = totais.get(aula.getId());
        return AulaResumoResponse.de(aula, total != null ? total.getPresentes() : 0,
                total != null ? total.getFaltas() : 0);
    }

    /** Quem entra na chamada do dia, pelo nome. */
    private List<AlunoDaChamadaResponse> alunosDaChamada(UUID escolaId, Long turmaId, LocalDate dia) {
        Collection<MatriculaTurma> matriculas = matriculasAtivas(escolaId, turmaId, dia).values();
        if (matriculas.isEmpty()) {
            return List.of();
        }
        List<UUID> pessoaIds = matriculas.stream().map(m -> m.getAluno().getPessoaId()).distinct().toList();
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));
        return matriculas.stream()
                .map(m -> {
                    Pessoa pessoa = pessoasPorId.get(m.getAluno().getPessoaId());
                    return new AlunoDaChamadaResponse(m.getAluno().getId(), pessoa != null ? pessoa.getNome() : null,
                            m.getAluno().getMatricula());
                })
                .sorted(Comparator.comparing(AlunoDaChamadaResponse::alunoNome, Paginacao.ORDEM_DE_NOME))
                .toList();
    }

    private AulaResponse resposta(UUID aulaId, UUID escolaId) {
        // Relida com o EntityGraph para devolver nome da turma e da disciplina.
        Aula aula = aulaRepository.findByIdAndEscolaId(aulaId, escolaId).orElseThrow(AulaService::naoEncontrada);
        return AulaResponse.de(aula, montarChamada(aula, escolaId));
    }

    /** A chamada da aula, pelo nome do aluno: quem tem presenca registrada, esteja ou nao ainda na turma. */
    private List<FrequenciaAlunoResponse> montarChamada(Aula aula, UUID escolaId) {
        List<Frequencia> frequencias = frequenciaRepository.findAllByEscolaIdAndAulaId(escolaId, aula.getId());
        if (frequencias.isEmpty()) {
            return List.of();
        }
        Map<UUID, MatriculaTurma> matriculasPorAluno = matriculaRepository
                .findAllByEscolaIdAndTurmaId(escolaId, aula.getTurma().getId()).stream()
                .collect(Collectors.toMap(m -> m.getAluno().getId(), Function.identity()));
        List<UUID> pessoaIds = frequencias.stream().map(f -> f.getAluno().getPessoaId()).distinct().toList();
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository.findAllByIdInAndEscolaId(pessoaIds, escolaId).stream()
                .collect(Collectors.toMap(Pessoa::getId, Function.identity()));

        return frequencias.stream()
                .map(f -> {
                    Pessoa pessoa = pessoasPorId.get(f.getAluno().getPessoaId());
                    MatriculaTurma matricula = matriculasPorAluno.get(f.getAluno().getId());
                    return new FrequenciaAlunoResponse(f.getAluno().getId(), pessoa != null ? pessoa.getNome() : null,
                            f.getAluno().getMatricula(), matricula != null ? matricula.getStatus() : null,
                            f.isPresente());
                })
                .sorted(Comparator.comparing(FrequenciaAlunoResponse::alunoNome, Paginacao.ORDEM_DE_NOME))
                .toList();
    }

    /**
     * Quem entra na chamada de uma aula: matricula ATIVA hoje e feita ate o dia
     * da aula. Quem se matriculou depois nao estava na turma naquele dia, e uma
     * falta ali distorceria o percentual de faltas dele.
     */
    private Map<UUID, MatriculaTurma> matriculasAtivas(UUID escolaId, Long turmaId, LocalDate dataDaAula) {
        return matriculaRepository.findAllByEscolaIdAndTurmaId(escolaId, turmaId).stream()
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .filter(m -> !m.getDataMatricula().isAfter(dataDaAula))
                .collect(Collectors.toMap(m -> m.getAluno().getId(), Function.identity()));
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO_ESCOLA));
    }

    /** Professor dono do login. 404 se o login nao e de um professor da escola. */
    private Professor professorAtual(UUID escolaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        return professorRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor nao encontrado"));
    }

    /**
     * O dia da semana da data tem aula da disciplina na grade da turma
     * (HORARIO_TURMA). Domingo nunca tem; sem grade montada, nenhum dia tem.
     */
    private boolean diaDeAula(UUID escolaId, Long turmaId, Long disciplinaId, LocalDate data) {
        return DiaSemana.de(data)
                .map(dia -> horarioRepository.existsByEscolaIdAndAlocacao_Turma_IdAndAlocacao_Disciplina_IdAndDiaSemana(
                        escolaId, turmaId, disciplinaId, dia))
                .orElse(false);
    }

    private void exigirAlocacao(Professor professor, UUID escolaId, Long turmaId, Long disciplinaId) {
        if (turmaId == null || disciplinaId == null || !alocacaoRepository
                .existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(escolaId, professor.getId(), turmaId,
                        disciplinaId)) {
            throw new RecursoNaoEncontradoException("Turma ou disciplina nao alocada ao professor");
        }
    }

    /** Aula da escola, de uma turma/disciplina em que o professor esta alocado. */
    private Aula buscarAula(UUID id, UUID escolaId, Professor professor) {
        Aula aula = aulaRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(AulaService::naoEncontrada);
        if (!alocacaoRepository.existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(escolaId, professor.getId(),
                aula.getTurma().getId(), aula.getDisciplina().getId())) {
            throw naoEncontrada();
        }
        return aula;
    }

    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Aula nao encontrada");
    }
}
