package br.pucminas.eduka.backend.service.academico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.academico.Aula;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.Frequencia;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.dto.academico.AtualizarAulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaRequest;
import br.pucminas.eduka.backend.dto.academico.AulaResponse;
import br.pucminas.eduka.backend.dto.academico.AulaResumoResponse;
import br.pucminas.eduka.backend.dto.academico.ChamadaDoDiaResponse;
import br.pucminas.eduka.backend.dto.academico.ChamadaItem;
import br.pucminas.eduka.backend.dto.comum.PaginaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AulaRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.FrequenciaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Regras de aula e chamada: o professor so mexe no que esta alocado a ele,
 * a data nao e futura e a chamada e completa e so de alunos ativos na turma.
 */
@ExtendWith(MockitoExtension.class)
class AulaServiceTest {

    private static final UUID ESCOLA = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    /** 01/11/2026 15:00 em Brasilia. */
    private static final Instant AGORA = Instant.parse("2026-11-01T18:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 1);

    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private AulaRepository aulaRepository;
    @Mock
    private FrequenciaRepository frequenciaRepository;
    @Mock
    private ProfessorRepository professorRepository;
    @Mock
    private AlocacaoProfessorRepository alocacaoRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private DisciplinaRepository disciplinaRepository;
    @Mock
    private MatriculaTurmaRepository matriculaRepository;
    @Mock
    private PessoaRepository pessoaRepository;

    private Professor professor;
    private Turma turma;
    private Disciplina disciplina;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new UsuarioAutenticado(USUARIO, "PROFESSOR", ESCOLA), null, "ROLE_PROFESSOR"));
        professor = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA).usuarioId(USUARIO).ativo(true)
                .build();
        turma = Turma.builder().id(1L).nome("1A").build();
        disciplina = Disciplina.builder().id(10L).nome("Matematica").build();
        when(professorRepository.findByUsuarioIdAndEscolaId(USUARIO, ESCOLA)).thenReturn(Optional.of(professor));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private AulaService servico() {
        return servico(AGORA);
    }

    private AulaService servico(Instant agora) {
        return new AulaService(aulaRepository, frequenciaRepository, professorRepository, alocacaoRepository,
                turmaRepository, disciplinaRepository, matriculaRepository, pessoaRepository,
                new IsolamentoEscola(), Clock.fixed(agora, ZoneOffset.UTC), auditoriaService);
    }

    private void alocado(boolean alocado) {
        when(alocacaoRepository.existsByEscolaIdAndProfessorIdAndTurmaIdAndDisciplinaId(ESCOLA, professor.getId(),
                1L, 10L)).thenReturn(alocado);
    }

    private static Aluno aluno() {
        return Aluno.builder().id(UUID.randomUUID()).escolaId(ESCOLA).pessoaId(UUID.randomUUID())
                .matricula("2026-" + UUID.randomUUID().toString().substring(0, 4)).build();
    }

    private MatriculaTurma matricula(Aluno aluno, StatusMatricula status) {
        return matricula(aluno, status, HOJE.minusDays(30));
    }

    private MatriculaTurma matricula(Aluno aluno, StatusMatricula status, LocalDate dataMatricula) {
        return MatriculaTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA).aluno(aluno).turma(turma)
                .status(status).dataMatricula(dataMatricula).build();
    }

    private static Pessoa pessoa(Aluno aluno, String nome) {
        return Pessoa.builder().id(aluno.getPessoaId()).escolaId(ESCOLA).nome(nome).ativo(true).build();
    }

    private void turmaComMatriculas(MatriculaTurma... matriculas) {
        when(matriculaRepository.findAllByEscolaIdAndTurmaId(ESCOLA, 1L)).thenReturn(List.of(matriculas));
    }

    private void nomes(Pessoa... pessoas) {
        when(pessoaRepository.findAllByIdInAndEscolaId(anyList(), eq(ESCOLA))).thenReturn(List.of(pessoas));
    }

    private AulaRequest registro(LocalDate data, ChamadaItem... chamada) {
        return new AulaRequest(1L, 10L, data, " Equacoes do 1o grau ", List.of(chamada));
    }

    private static ChamadaItem presente(Aluno aluno) {
        return new ChamadaItem(aluno.getId(), true);
    }

    private static ChamadaItem falta(Aluno aluno) {
        return new ChamadaItem(aluno.getId(), false);
    }

    private Aula aula(LocalDate data) {
        return Aula.builder().id(UUID.randomUUID()).escolaId(ESCOLA).turma(turma).disciplina(disciplina)
                .professor(professor).data(data).conteudo("Fracoes").build();
    }

    private Aula aulaExistente() {
        Aula aula = aula(HOJE.minusDays(2));
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));
        alocado(true);
        return aula;
    }

    private Frequencia frequencia(Aula aula, Aluno aluno, boolean presente) {
        return Frequencia.builder().id(UUID.randomUUID()).escolaId(ESCOLA).aula(aula).aluno(aluno)
                .presente(presente).build();
    }

    /** O repositorio devolve a aula salva e, depois, as frequencias que o servico gravou. */
    private List<Frequencia> gravacaoDaAula() {
        List<Frequencia> gravadas = new ArrayList<>();
        when(aulaRepository.saveAndFlush(any(Aula.class))).thenAnswer(inv -> {
            Aula a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            when(aulaRepository.findByIdAndEscolaId(a.getId(), ESCOLA)).thenReturn(Optional.of(a));
            when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, a.getId())).thenReturn(gravadas);
            return a;
        });
        when(frequenciaRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Frequencia> lote = inv.getArgument(0);
            gravadas.addAll(lote);
            return lote;
        });
        return gravadas;
    }

    // ---- registrar ----

    @Test
    void registrarEmDisciplinaNaoAlocadaRespondeNaoEncontradoSemGravar() {
        alocado(false);

        assertThatThrownBy(() -> servico().registrar(registro(HOJE)))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Turma ou disciplina nao alocada ao professor");
        verify(aulaRepository, never()).saveAndFlush(any());
        verify(frequenciaRepository, never()).saveAll(anyList());
    }

    @Test
    void registrarGravaAulaEChamadaComEscolaEProfessorDoToken() {
        alocado(true);
        Aluno ana = aluno();
        Aluno bruno = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA), matricula(bruno, StatusMatricula.ATIVA));
        nomes(pessoa(ana, "Ana"), pessoa(bruno, "Bruno"));
        when(turmaRepository.getReferenceById(1L)).thenReturn(turma);
        when(disciplinaRepository.getReferenceById(10L)).thenReturn(disciplina);
        gravacaoDaAula();

        AulaResponse resposta = servico().registrar(registro(HOJE, falta(bruno), presente(ana)));

        ArgumentCaptor<Aula> aula = ArgumentCaptor.forClass(Aula.class);
        verify(aulaRepository).saveAndFlush(aula.capture());
        assertThat(aula.getValue().getEscolaId()).isEqualTo(ESCOLA);
        assertThat(aula.getValue().getProfessor()).isEqualTo(professor);
        assertThat(aula.getValue().getData()).isEqualTo(HOJE);
        assertThat(aula.getValue().getConteudo()).isEqualTo("Equacoes do 1o grau");
        assertThat(resposta.turmaNome()).isEqualTo("1A");
        assertThat(resposta.disciplinaNome()).isEqualTo("Matematica");
        assertThat(resposta.presentes()).isEqualTo(1);
        assertThat(resposta.faltas()).isEqualTo(1);
        assertThat(resposta.chamada()).extracting("alunoNome", "presente")
                .containsExactly(tuple("Ana", true),
                        tuple("Bruno", false));
        assertThat(resposta.chamada().get(0).situacaoMatricula()).isEqualTo(StatusMatricula.ATIVA);        // A chamada nasce com a aula: um registro so no log, nao um por aluno.
        verify(auditoriaService).registrar(OperacaoAuditoria.CRIAR, EntidadeAuditada.AULA,
                aula.getValue().getId());
        verify(auditoriaService, never()).registrar(any(), eq(EntidadeAuditada.FREQUENCIA), any());
    }

    @Test
    void registrarComDataFuturaRespondeErroSemGravar() {
        alocado(true);

        assertThatThrownBy(() -> servico().registrar(registro(HOJE.plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data da aula nao pode ser futura");
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void hojeVaiPeloFusoDaEscolaEntaoDiaDeBrasiliaAindaValeAposAMeiaNoiteUtc() {
        alocado(true);
        // 02/11 01:00 UTC = 01/11 22:00 em Brasilia: o dia da escola ainda e 01/11.
        Instant noiteEmBrasilia = Instant.parse("2026-11-02T01:00:00Z");
        turmaComMatriculas();
        when(turmaRepository.getReferenceById(1L)).thenReturn(turma);
        when(disciplinaRepository.getReferenceById(10L)).thenReturn(disciplina);
        gravacaoDaAula();

        assertThat(servico(noiteEmBrasilia).registrar(registro(LocalDate.of(2026, 11, 1))).data())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThatThrownBy(() -> servico(noiteEmBrasilia).registrar(registro(LocalDate.of(2026, 11, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data da aula nao pode ser futura");
    }

    @Test
    void registrarComAlunoAtivoForaDaListaRespondeChamadaIncompletaSemGravar() {
        alocado(true);
        Aluno ana = aluno();
        Aluno bruno = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA), matricula(bruno, StatusMatricula.ATIVA));

        assertThatThrownBy(() -> servico().registrar(registro(HOJE, presente(ana))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chamada incompleta")
                .hasMessageContaining("1 aluno(s)");
        verify(aulaRepository, never()).saveAndFlush(any());
        verify(frequenciaRepository, never()).saveAll(anyList());
    }

    @Test
    void registrarAulaAntigaNaoExigeQuemSeMatriculouDepoisDaData() {
        alocado(true);
        Aluno ana = aluno();
        Aluno novo = aluno();
        LocalDate dataDaAula = HOJE.minusDays(10);
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA),
                matricula(novo, StatusMatricula.ATIVA, dataDaAula.plusDays(1)));
        nomes(pessoa(ana, "Ana"));
        when(turmaRepository.getReferenceById(1L)).thenReturn(turma);
        when(disciplinaRepository.getReferenceById(10L)).thenReturn(disciplina);
        List<Frequencia> gravadas = gravacaoDaAula();

        AulaResponse resposta = servico().registrar(registro(dataDaAula, presente(ana)));

        assertThat(resposta.chamada()).extracting("alunoNome").containsExactly("Ana");
        assertThat(gravadas).extracting(f -> f.getAluno().getId()).containsExactly(ana.getId());
    }

    @Test
    void registrarAulaAntigaComAlunoMatriculadoDepoisDaDataRespondeErroSemGravar() {
        alocado(true);
        Aluno ana = aluno();
        Aluno novo = aluno();
        LocalDate dataDaAula = HOJE.minusDays(10);
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA),
                matricula(novo, StatusMatricula.ATIVA, dataDaAula.plusDays(1)));

        assertThatThrownBy(() -> servico().registrar(registro(dataDaAula, presente(ana), falta(novo))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno sem matricula ativa na turma da aula");
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrarExigeQuemSeMatriculouNoMesmoDiaDaAula() {
        alocado(true);
        Aluno ana = aluno();
        Aluno domesmoDia = aluno();
        LocalDate dataDaAula = HOJE.minusDays(10);
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA),
                matricula(domesmoDia, StatusMatricula.ATIVA, dataDaAula));

        assertThatThrownBy(() -> servico().registrar(registro(dataDaAula, presente(ana))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chamada incompleta")
                .hasMessageContaining("1 aluno(s)");
    }

    @Test
    void atualizarAulaAntigaNaoExigeNemAceitaQuemSeMatriculouDepoisDaData() {
        Aula aula = aulaExistente();
        Aluno ana = aluno();
        Aluno novo = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA),
                matricula(novo, StatusMatricula.ATIVA, aula.getData().plusDays(1)));
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId()))
                .thenReturn(List.of(frequencia(aula, ana, true)));
        nomes(pessoa(ana, "Ana"));

        servico().atualizar(aula.getId(), new AtualizarAulaRequest("Fracoes", List.of(presente(ana))));
        verify(aulaRepository).save(aula);

        assertThatThrownBy(() -> servico().atualizar(aula.getId(),
                new AtualizarAulaRequest("Fracoes", List.of(presente(ana), falta(novo)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno sem matricula ativa na turma da aula");
    }

    @Test
    void registrarComListaVaziaEmTurmaComAlunosRespondeChamadaIncompleta() {
        alocado(true);
        turmaComMatriculas(matricula(aluno(), StatusMatricula.ATIVA));

        assertThatThrownBy(() -> servico().registrar(registro(HOJE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chamada incompleta");
    }

    @Test
    void registrarComAlunoForaDaTurmaRespondeErroSemGravar() {
        alocado(true);
        Aluno ana = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA));

        assertThatThrownBy(() -> servico().registrar(registro(HOJE, presente(ana), presente(aluno()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno sem matricula ativa na turma da aula");
        verify(aulaRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrarComAlunoDeMatriculaNaoAtivaRespondeErro() {
        alocado(true);
        Aluno ana = aluno();
        Aluno trancado = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA), matricula(trancado, StatusMatricula.TRANCADA));

        assertThatThrownBy(() -> servico().registrar(registro(HOJE, presente(ana), presente(trancado))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno sem matricula ativa na turma da aula");
    }

    @Test
    void registrarComAlunoRepetidoRespondeErro() {
        alocado(true);
        Aluno ana = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA));

        assertThatThrownBy(() -> servico().registrar(registro(HOJE, presente(ana), falta(ana))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno repetido na chamada");
    }

    // ---- buscar ----

    @Test
    void buscarAulaInexistenteOuDeOutraEscolaRespondeNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(aulaRepository.findByIdAndEscolaId(id, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico().buscar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Aula nao encontrada");
    }

    @Test
    void buscarAulaDeDisciplinaQueNaoEDoProfessorRespondeNaoEncontrado() {
        Aula aula = aula(HOJE);
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));
        alocado(false);

        assertThatThrownBy(() -> servico().buscar(aula.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Aula nao encontrada");
        verify(frequenciaRepository, never()).findAllByEscolaIdAndAulaId(any(), any());
    }

    @Test
    void buscarMostraAAlunoQueSaiuDaTurmaSemSituacaoDeMatricula() {
        Aula aula = aulaExistente();
        Aluno ana = aluno();
        Aluno saiu = aluno();
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId()))
                .thenReturn(List.of(frequencia(aula, saiu, false), frequencia(aula, ana, true)));
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA));
        nomes(pessoa(ana, "Ana"), pessoa(saiu, "Zeca"));

        AulaResponse resposta = servico().buscar(aula.getId());

        assertThat(resposta.chamada()).extracting("alunoNome").containsExactly("Ana", "Zeca");
        assertThat(resposta.chamada().get(1).situacaoMatricula()).isNull();
        assertThat(resposta.chamada().get(1).presente()).isFalse();
    }

    // ---- atualizar ----

    @Test
    void atualizarCorrigeConteudoEPresencaSoDeQuemMudou() {
        Aula aula = aulaExistente();
        Aluno ana = aluno();
        Aluno bruno = aluno();
        Frequencia deAna = frequencia(aula, ana, true);
        Frequencia deBruno = frequencia(aula, bruno, false);
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA), matricula(bruno, StatusMatricula.ATIVA));
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId()))
                .thenReturn(List.of(deAna, deBruno));
        nomes(pessoa(ana, "Ana"), pessoa(bruno, "Bruno"));

        AulaResponse resposta = servico().atualizar(aula.getId(),
                new AtualizarAulaRequest(" Fracoes e decimais ", List.of(presente(ana), presente(bruno))));

        assertThat(aula.getConteudo()).isEqualTo("Fracoes e decimais");
        assertThat(aula.getAtualizadoEm()).isEqualTo(AGORA);
        assertThat(aula.getData()).isEqualTo(HOJE.minusDays(2));
        assertThat(deAna.isPresente()).isTrue();
        assertThat(deAna.getAtualizadoEm()).isNull();
        assertThat(deBruno.isPresente()).isTrue();
        assertThat(deBruno.getAtualizadoEm()).isEqualTo(AGORA);
        assertThat(resposta.faltas()).isZero();
        assertThat(resposta.presentes()).isEqualTo(2);        // A correcao da chamada deixa rastro por aluno: so o Bruno mudou.
        verify(auditoriaService).registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.AULA, aula.getId());
        verify(auditoriaService).registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.FREQUENCIA, deBruno.getId());
        verify(auditoriaService, never()).registrar(OperacaoAuditoria.EDITAR, EntidadeAuditada.FREQUENCIA,
                deAna.getId());
    }

    @Test
    void atualizarGravaPresencaDeAlunoMatriculadoDepoisDaAula() {
        Aula aula = aulaExistente();
        Aluno ana = aluno();
        Aluno novo = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA), matricula(novo, StatusMatricula.ATIVA));
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId()))
                .thenReturn(List.of(frequencia(aula, ana, true)));
        nomes(pessoa(ana, "Ana"), pessoa(novo, "Novo"));

        servico().atualizar(aula.getId(), new AtualizarAulaRequest("Fracoes", List.of(presente(ana), falta(novo))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Frequencia>> novas = ArgumentCaptor.forClass(List.class);
        verify(frequenciaRepository).saveAll(novas.capture());
        assertThat(novas.getValue()).singleElement().satisfies(f -> {
            assertThat(f.getAluno()).isEqualTo(novo);
            assertThat(f.isPresente()).isFalse();
            assertThat(f.getEscolaId()).isEqualTo(ESCOLA);
        });
    }

    @Test
    void atualizarAceitaAlunoQueSaiuDaTurmaMasNaoExigeEle() {
        Aula aula = aulaExistente();
        Aluno ana = aluno();
        Aluno saiu = aluno();
        Frequencia deQuemSaiu = frequencia(aula, saiu, true);
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA));
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId()))
                .thenReturn(List.of(frequencia(aula, ana, true), deQuemSaiu));
        nomes(pessoa(ana, "Ana"), pessoa(saiu, "Zeca"));

        // Sem ele na lista: vale, o registro dele fica como estava.
        servico().atualizar(aula.getId(), new AtualizarAulaRequest("Fracoes", List.of(presente(ana))));
        assertThat(deQuemSaiu.isPresente()).isTrue();

        // Com ele na lista: a falta dele pode ser corrigida.
        servico().atualizar(aula.getId(), new AtualizarAulaRequest("Fracoes", List.of(presente(ana), falta(saiu))));
        assertThat(deQuemSaiu.isPresente()).isFalse();
    }

    @Test
    void atualizarComChamadaIncompletaOuAlunoDeFora400SemMudarNada() {
        Aula aula = aulaExistente();
        Aluno ana = aluno();
        Aluno bruno = aluno();
        turmaComMatriculas(matricula(ana, StatusMatricula.ATIVA), matricula(bruno, StatusMatricula.ATIVA));
        when(frequenciaRepository.findAllByEscolaIdAndAulaId(ESCOLA, aula.getId()))
                .thenReturn(List.of(frequencia(aula, ana, true), frequencia(aula, bruno, true)));

        assertThatThrownBy(() -> servico().atualizar(aula.getId(),
                new AtualizarAulaRequest("Outro", List.of(presente(ana)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chamada incompleta");
        assertThatThrownBy(() -> servico().atualizar(aula.getId(),
                new AtualizarAulaRequest("Outro", List.of(presente(ana), presente(bruno), presente(aluno())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aluno sem matricula ativa na turma da aula");
        assertThat(aula.getConteudo()).isEqualTo("Fracoes");
        verify(aulaRepository, never()).save(any());
    }

    @Test
    void atualizarAulaDeOutraAlocacaoRespondeNaoEncontradoSemMudarNada() {
        Aula aula = aula(HOJE);
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));
        alocado(false);

        assertThatThrownBy(() -> servico().atualizar(aula.getId(), new AtualizarAulaRequest("Outro", List.of())))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(aula.getConteudo()).isEqualTo("Fracoes");
        verify(aulaRepository, never()).save(any());
    }

    // ---- excluir ----

    @Test
    void excluirApagaAChamadaEDepoisAAulaDoProfessorAlocado() {
        Aula aula = aulaExistente();

        servico().excluir(aula.getId());

        InOrder ordem = inOrder(frequenciaRepository, aulaRepository);
        ordem.verify(frequenciaRepository).apagarDaAula(ESCOLA, aula.getId());
        ordem.verify(aulaRepository).delete(aula);
    }

    @Test
    void excluirAulaDeOutroProfessorRespondeNaoEncontradoSemApagar() {
        Aula aula = aula(HOJE);
        when(aulaRepository.findByIdAndEscolaId(aula.getId(), ESCOLA)).thenReturn(Optional.of(aula));
        alocado(false);

        assertThatThrownBy(() -> servico().excluir(aula.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Aula nao encontrada");
        verify(frequenciaRepository, never()).apagarDaAula(any(), any());
        verify(aulaRepository, never()).delete(any());
    }

    @Test
    void excluirAulaInexistenteOuDeOutraEscolaRespondeNaoEncontradoSemApagar() {
        UUID id = UUID.randomUUID();
        when(aulaRepository.findByIdAndEscolaId(id, ESCOLA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico().excluir(id))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Aula nao encontrada");
        verify(frequenciaRepository, never()).apagarDaAula(any(), any());
        verify(aulaRepository, never()).delete(any());
    }

    // ---- listar ----

    @Test
    void listarEmDisciplinaNaoAlocadaRespondeNaoEncontrado() {
        alocado(false);

        assertThatThrownBy(() -> servico().listar(1L, 10L, new Paginacao(0, 20), null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(aulaRepository, never()).findAllByEscolaIdAndTurmaIdAndDisciplinaId(any(), any(), any(), any());
    }

    @Test
    void listarDevolvePaginaDasMaisRecentesComOsTotaisDaChamada() {
        alocado(true);
        Aula recente = aula(HOJE);
        Aula semChamada = aula(HOJE.minusDays(7));
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        when(aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaId(eq(ESCOLA), eq(1L), eq(10L),
                pagina.capture()))
                .thenReturn(new PageImpl<>(List.of(recente, semChamada), PageRequest.of(0, 2), 5));
        FrequenciaRepository.TotaisDaAula totais = new FrequenciaRepository.TotaisDaAula() {
            public UUID getAulaId() {
                return recente.getId();
            }

            public long getPresentes() {
                return 18;
            }

            public long getFaltas() {
                return 2;
            }
        };
        when(frequenciaRepository.contarPorAula(eq(ESCOLA), anyCollection())).thenReturn(List.of(totais));

        PaginaResponse<AulaResumoResponse> resposta = servico().listar(1L, 10L, new Paginacao(0, 2), null);

        assertThat(pagina.getValue().getSort().getOrderFor("data").getDirection()).isEqualTo(Direction.DESC);
        assertThat(pagina.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Direction.ASC);
        assertThat(resposta.totalElementos()).isEqualTo(5);
        assertThat(resposta.totalPaginas()).isEqualTo(3);
        assertThat(resposta.conteudo()).extracting("id").containsExactly(recente.getId(), semChamada.getId());
        assertThat(resposta.conteudo().get(0).presentes()).isEqualTo(18);
        assertThat(resposta.conteudo().get(0).faltas()).isEqualTo(2);
        assertThat(resposta.conteudo().get(1).presentes()).isZero();
        assertThat(resposta.conteudo().get(1).faltas()).isZero();
    }

    @Test
    void listarRespeitaADirecaoPedida() {
        alocado(true);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        when(aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaId(eq(ESCOLA), eq(1L), eq(10L),
                pagina.capture())).thenReturn(new PageImpl<>(List.of()));

        PaginaResponse<AulaResumoResponse> resposta = servico().listar(1L, 10L, new Paginacao(0, 20), Direction.ASC);

        assertThat(resposta.conteudo()).isEmpty();
        assertThat(pagina.getValue().getSort().getOrderFor("data").getDirection()).isEqualTo(Direction.ASC);
        verify(frequenciaRepository, never()).contarPorAula(any(), any());
    }

    // ---- chamada do dia ----

    @Test
    void chamadaDoDiaEmDisciplinaNaoAlocadaRespondeNaoEncontrado() {
        alocado(false);

        assertThatThrownBy(() -> servico().chamadaDoDia(1L, 10L, HOJE))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(matriculaRepository, never()).findAllByEscolaIdAndTurmaId(any(), any());
    }

    @Test
    void chamadaDeDiaFuturoRespondeErro() {
        alocado(true);

        assertThatThrownBy(() -> servico().chamadaDoDia(1L, 10L, HOJE.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chamadaDoDiaTrazQuemEstavaNaTurmaPeloNomeEAsAulasComOsTotais() {
        alocado(true);
        LocalDate dia = HOJE.minusDays(5);
        Aluno bia = aluno();
        Aluno ana = aluno();
        Aluno depois = aluno();
        Aluno trancada = aluno();
        turmaComMatriculas(matricula(bia, StatusMatricula.ATIVA), matricula(ana, StatusMatricula.ATIVA),
                matricula(depois, StatusMatricula.ATIVA, dia.plusDays(1)), matricula(trancada, StatusMatricula.TRANCADA));
        nomes(pessoa(bia, "Bia"), pessoa(ana, "Ana"));
        Aula aula = aula(dia);
        ArgumentCaptor<Sort> ordem = ArgumentCaptor.forClass(Sort.class);
        when(aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaIdAndData(eq(ESCOLA), eq(1L), eq(10L), eq(dia),
                ordem.capture())).thenReturn(List.of(aula));
        FrequenciaRepository.TotaisDaAula totais = new FrequenciaRepository.TotaisDaAula() {
            public UUID getAulaId() {
                return aula.getId();
            }

            public long getPresentes() {
                return 1;
            }

            public long getFaltas() {
                return 1;
            }
        };
        when(frequenciaRepository.contarPorAula(eq(ESCOLA), anyCollection())).thenReturn(List.of(totais));

        ChamadaDoDiaResponse resposta = servico().chamadaDoDia(1L, 10L, dia);

        assertThat(resposta.data()).isEqualTo(dia);
        assertThat(resposta.alunos()).extracting("alunoNome").containsExactly("Ana", "Bia");
        assertThat(resposta.alunos()).extracting("alunoId").containsExactly(ana.getId(), bia.getId());
        assertThat(resposta.aulas()).extracting("id").containsExactly(aula.getId());
        assertThat(resposta.aulas().get(0).presentes()).isEqualTo(1);
        assertThat(resposta.aulas().get(0).faltas()).isEqualTo(1);
        assertThat(ordem.getValue().getOrderFor("criadoEm").getDirection()).isEqualTo(Direction.ASC);
    }

    @Test
    void chamadaSemDataValeHojeEDiaSemAulaNaoContaTotais() {
        alocado(true);
        turmaComMatriculas();
        when(aulaRepository.findAllByEscolaIdAndTurmaIdAndDisciplinaIdAndData(eq(ESCOLA), eq(1L), eq(10L), eq(HOJE),
                any(Sort.class))).thenReturn(List.of());

        ChamadaDoDiaResponse resposta = servico().chamadaDoDia(1L, 10L, null);

        assertThat(resposta.data()).isEqualTo(HOJE);
        assertThat(resposta.alunos()).isEmpty();
        assertThat(resposta.aulas()).isEmpty();
        verify(frequenciaRepository, never()).contarPorAula(any(), any());
    }
}
