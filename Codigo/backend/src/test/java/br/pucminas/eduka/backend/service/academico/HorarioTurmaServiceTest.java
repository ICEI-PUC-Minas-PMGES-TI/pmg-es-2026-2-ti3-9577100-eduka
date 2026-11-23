package br.pucminas.eduka.backend.service.academico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.DiaSemana;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.HorarioTurma;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.dto.academico.AtualizarHorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.HorarioTurmaRequest;
import br.pucminas.eduka.backend.dto.academico.HorarioTurmaResponse;
import br.pucminas.eduka.backend.exception.academico.HorarioConflitanteException;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.HorarioTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Regras da grade semanal: alocacao da turma e da escola, inicio antes do fim
 * e sem sobreposicao no mesmo dia (na turma e para o professor), com a borda
 * que so encosta liberada.
 */
@ExtendWith(MockitoExtension.class)
class HorarioTurmaServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();

    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private HorarioTurmaRepository horarioRepository;
    @Mock
    private AlocacaoProfessorRepository alocacaoRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private ProfessorRepository professorRepository;
    @Mock
    private PessoaRepository pessoaRepository;

    private HorarioTurmaService horarioService;

    private Pessoa pessoaAna;
    private Professor ana;
    private Disciplina matematica;
    private Turma turma1A;
    private Turma turma2B;
    private AlocacaoProfessor alocacaoDa1A;
    private AlocacaoProfessor alocacaoDa2B;

    @BeforeEach
    void setUp() {
        horarioService = new HorarioTurmaService(horarioRepository, alocacaoRepository, turmaRepository,
                professorRepository, pessoaRepository, new IsolamentoEscola(), auditoriaService);
        autenticarComo("SECRETARIA", ESCOLA_A);

        pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        ana = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId()).ativo(true)
                .build();
        matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        turma1A = Turma.builder().id(1L).nome("1A").disciplinas(Set.of(matematica)).build();
        turma2B = Turma.builder().id(2L).nome("2B").disciplinas(Set.of(matematica)).build();
        alocacaoDa1A = AlocacaoProfessor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).professor(ana)
                .turma(turma1A).disciplina(matematica).build();
        alocacaoDa2B = AlocacaoProfessor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).professor(ana)
                .turma(turma2B).disciplina(matematica).build();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticarComo(String perfil, UUID escolaId) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), perfil, escolaId);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(usuario, null, "ROLE_" + perfil));
    }

    private static LocalTime h(int hora, int minuto) {
        return LocalTime.of(hora, minuto);
    }

    private static HorarioTurma aula(AlocacaoProfessor alocacao, DiaSemana dia, LocalTime inicio, LocalTime fim) {
        return HorarioTurma.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).alocacao(alocacao).diaSemana(dia)
                .horaInicio(inicio).horaFim(fim).build();
    }

    /** Turma e professor existem e a alocacao da 1A e achada; nenhuma outra aula no dia. */
    private void prepararCriacaoNaTurma1A() {
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(alocacaoRepository.findByIdAndEscolaId(alocacaoDa1A.getId(), ESCOLA_A))
                .thenReturn(Optional.of(alocacaoDa1A));
        when(professorRepository.findComLockByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
    }

    private void aulasDaTurmaNoDia(DiaSemana dia, HorarioTurma... aulas) {
        when(horarioRepository.findAllByEscolaIdAndAlocacao_Turma_IdAndDiaSemana(ESCOLA_A, 1L, dia))
                .thenReturn(List.of(aulas));
    }

    private void aulasDoProfessorNoDia(DiaSemana dia, HorarioTurma... aulas) {
        when(horarioRepository.findAllByEscolaIdAndAlocacao_Professor_IdAndDiaSemana(ESCOLA_A, ana.getId(), dia))
                .thenReturn(List.of(aulas));
    }

    private HorarioTurmaRequest pedido(DiaSemana dia, LocalTime inicio, LocalTime fim) {
        return new HorarioTurmaRequest(alocacaoDa1A.getId(), dia, inicio, fim);
    }

    private void gravarDevolvendoOMesmo() {
        when(horarioRepository.saveAndFlush(any(HorarioTurma.class))).thenAnswer(inv -> {
            HorarioTurma aula = inv.getArgument(0);
            aula.setId(UUID.randomUUID());
            return aula;
        });
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(pessoaAna.getId()), ESCOLA_A))
                .thenReturn(List.of(pessoaAna));
    }

    // --- criar ---------------------------------------------------------------

    @Test
    void criaAulaNaGradeComDisciplinaEProfessorDaAlocacao() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA);
        aulasDoProfessorNoDia(DiaSemana.SEGUNDA);
        gravarDevolvendoOMesmo();

        HorarioTurmaResponse resposta = horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 0), h(9, 0)));

        ArgumentCaptor<HorarioTurma> gravada = ArgumentCaptor.forClass(HorarioTurma.class);
        verify(horarioRepository).saveAndFlush(gravada.capture());
        assertThat(gravada.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(gravada.getValue().getAlocacao()).isSameAs(alocacaoDa1A);
        assertThat(resposta.turmaId()).isEqualTo(1L);
        assertThat(resposta.diaSemana()).isEqualTo(DiaSemana.SEGUNDA);
        assertThat(resposta.horaInicio()).isEqualTo(h(8, 0));
        assertThat(resposta.horaFim()).isEqualTo(h(9, 0));
        assertThat(resposta.disciplinaNome()).isEqualTo("Matematica");
        assertThat(resposta.professorNome()).isEqualTo("Ana");
    }

    @Test
    void sobreposicaoParcialNaTurmaResponde409ComCodigoDaTurma() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0)));

        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 30), h(9, 30))))
                .isInstanceOfSatisfying(HorarioConflitanteException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(HorarioConflitanteException.TURMA));
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void aulaQueEnvolveOutraNaTurmaConflita() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 15), h(8, 45)));

        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 0), h(9, 0))))
                .isInstanceOf(HorarioConflitanteException.class);
    }

    @Test
    void aulaDentroDeOutraNaTurmaConflita() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(10, 0)));

        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 30), h(9, 0))))
                .isInstanceOf(HorarioConflitanteException.class);
    }

    @Test
    void mesmoIntervaloNaTurmaConflita() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0)));

        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 0), h(9, 0))))
                .isInstanceOf(HorarioConflitanteException.class);
    }

    @Test
    void aulaQueSoEncostaDepoisDeOutraNaoConflita() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0)));
        aulasDoProfessorNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0)));
        gravarDevolvendoOMesmo();

        // comeca exatamente quando a outra termina
        horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(9, 0), h(10, 0)));

        verify(horarioRepository).saveAndFlush(any(HorarioTurma.class));
    }

    @Test
    void aulaQueSoEncostaAntesDeOutraNaoConflita() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(9, 0), h(10, 0)));
        aulasDoProfessorNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(9, 0), h(10, 0)));
        gravarDevolvendoOMesmo();

        // termina exatamente quando a outra comeca
        horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 0), h(9, 0)));

        verify(horarioRepository).saveAndFlush(any(HorarioTurma.class));
    }

    @Test
    void professorComAulaSobrepostaEmOutraTurmaResponde409ComCodigoDoProfessor() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA);
        aulasDoProfessorNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa2B, DiaSemana.SEGUNDA, h(8, 30), h(9, 30)));

        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 0), h(9, 0))))
                .isInstanceOfSatisfying(HorarioConflitanteException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(HorarioConflitanteException.PROFESSOR));
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void professorComAulaEncostadaEmOutraTurmaNaoConflita() {
        prepararCriacaoNaTurma1A();
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA);
        aulasDoProfessorNoDia(DiaSemana.SEGUNDA, aula(alocacaoDa2B, DiaSemana.SEGUNDA, h(9, 0), h(10, 0)));
        gravarDevolvendoOMesmo();

        horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 0), h(9, 0)));

        verify(horarioRepository).saveAndFlush(any(HorarioTurma.class));
    }

    @Test
    void aulaDeOutroDiaNaoConflita() {
        prepararCriacaoNaTurma1A();
        // so a segunda esta ocupada; a consulta de terca volta vazia
        aulasDaTurmaNoDia(DiaSemana.TERCA);
        aulasDoProfessorNoDia(DiaSemana.TERCA);
        gravarDevolvendoOMesmo();

        horarioService.criar(1L, pedido(DiaSemana.TERCA, h(8, 0), h(9, 0)));

        verify(horarioRepository).findAllByEscolaIdAndAlocacao_Turma_IdAndDiaSemana(ESCOLA_A, 1L, DiaSemana.TERCA);
        verify(horarioRepository, never()).findAllByEscolaIdAndAlocacao_Turma_IdAndDiaSemana(ESCOLA_A, 1L,
                DiaSemana.SEGUNDA);
        verify(horarioRepository).saveAndFlush(any(HorarioTurma.class));
    }

    @Test
    void alocacaoDeOutraTurmaResponde400() {
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(alocacaoRepository.findByIdAndEscolaId(alocacaoDa2B.getId(), ESCOLA_A))
                .thenReturn(Optional.of(alocacaoDa2B));

        assertThatThrownBy(() -> horarioService.criar(1L,
                new HorarioTurmaRequest(alocacaoDa2B.getId(), DiaSemana.SEGUNDA, h(8, 0), h(9, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alocacao nao pertence a turma");
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void alocacaoInexistenteOuDeOutraEscolaResponde404() {
        UUID desconhecida = UUID.randomUUID();
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(alocacaoRepository.findByIdAndEscolaId(desconhecida, ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> horarioService.criar(1L,
                new HorarioTurmaRequest(desconhecida, DiaSemana.SEGUNDA, h(8, 0), h(9, 0))))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Alocacao nao encontrada");
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void turmaDeOutraEscolaResponde404() {
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(8, 0), h(9, 0))))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Turma nao encontrada");
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void inicioIgualOuDepoisDoFimResponde400() {
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));

        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(9, 0), h(9, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> horarioService.criar(1L, pedido(DiaSemana.SEGUNDA, h(10, 0), h(9, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(horarioRepository, never()).saveAndFlush(any());
    }

    // --- atualizar -----------------------------------------------------------

    @Test
    void aulaNaoConflitaComElaMesmaAoMudarDeHorario() {
        HorarioTurma existente = aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0));
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(existente.getId(), ESCOLA_A, 1L))
                .thenReturn(Optional.of(existente));
        when(professorRepository.findComLockByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        aulasDaTurmaNoDia(DiaSemana.SEGUNDA, existente);
        aulasDoProfessorNoDia(DiaSemana.SEGUNDA, existente);
        when(horarioRepository.saveAndFlush(existente)).thenReturn(existente);
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(pessoaAna.getId()), ESCOLA_A))
                .thenReturn(List.of(pessoaAna));

        HorarioTurmaResponse resposta = horarioService.atualizar(1L, existente.getId(),
                new AtualizarHorarioTurmaRequest(DiaSemana.SEGUNDA, h(8, 30), h(9, 30)));

        assertThat(resposta.horaInicio()).isEqualTo(h(8, 30));
        assertThat(resposta.horaFim()).isEqualTo(h(9, 30));
        assertThat(existente.getAtualizadoEm()).isNotNull();
    }

    @Test
    void mudarParaHorarioOcupadoPorOutraAulaDaTurmaResponde409() {
        HorarioTurma existente = aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0));
        HorarioTurma outra = aula(alocacaoDa1A, DiaSemana.TERCA, h(10, 0), h(11, 0));
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(existente.getId(), ESCOLA_A, 1L))
                .thenReturn(Optional.of(existente));
        when(professorRepository.findComLockByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        aulasDaTurmaNoDia(DiaSemana.TERCA, outra);

        assertThatThrownBy(() -> horarioService.atualizar(1L, existente.getId(),
                new AtualizarHorarioTurmaRequest(DiaSemana.TERCA, h(10, 30), h(11, 30))))
                .isInstanceOfSatisfying(HorarioConflitanteException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(HorarioConflitanteException.TURMA));
        verify(horarioRepository, never()).saveAndFlush(any());
        assertThat(existente.getDiaSemana()).isEqualTo(DiaSemana.SEGUNDA);
    }

    @Test
    void atualizarAulaDeOutraTurmaOuInexistenteResponde404() {
        UUID desconhecida = UUID.randomUUID();
        when(turmaRepository.findComLockByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(desconhecida, ESCOLA_A, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> horarioService.atualizar(1L, desconhecida,
                new AtualizarHorarioTurmaRequest(DiaSemana.SEGUNDA, h(8, 0), h(9, 0))))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Aula nao encontrada");
    }

    // --- remover -------------------------------------------------------------

    @Test
    void removeAulaDaGrade() {
        HorarioTurma existente = aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0));
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(existente.getId(), ESCOLA_A, 1L))
                .thenReturn(Optional.of(existente));

        horarioService.remover(1L, existente.getId());

        verify(horarioRepository).delete(existente);
    }

    @Test
    void removerAulaInexistenteResponde404() {
        UUID desconhecida = UUID.randomUUID();
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(horarioRepository.findByIdAndEscolaIdAndAlocacao_Turma_Id(desconhecida, ESCOLA_A, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> horarioService.remover(1L, desconhecida))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(horarioRepository, never()).delete(any());
    }

    // --- listar --------------------------------------------------------------

    @Test
    void listaAGradeOrdenadaPorDiaEHora() {
        HorarioTurma sextaCedo = aula(alocacaoDa1A, DiaSemana.SEXTA, h(7, 0), h(8, 0));
        HorarioTurma segundaTarde = aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(10, 0), h(11, 0));
        HorarioTurma segundaCedo = aula(alocacaoDa1A, DiaSemana.SEGUNDA, h(8, 0), h(9, 0));
        HorarioTurma sabado = aula(alocacaoDa1A, DiaSemana.SABADO, h(8, 0), h(9, 0));
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(horarioRepository.findAllByEscolaIdAndAlocacao_Turma_Id(ESCOLA_A, 1L))
                .thenReturn(List.of(sabado, sextaCedo, segundaTarde, segundaCedo));
        when(pessoaRepository.findAllByIdInAndEscolaId(List.of(pessoaAna.getId()), ESCOLA_A))
                .thenReturn(List.of(pessoaAna));

        List<HorarioTurmaResponse> grade = horarioService.listar(1L);

        assertThat(grade).extracting(HorarioTurmaResponse::id)
                .containsExactly(segundaCedo.getId(), segundaTarde.getId(), sextaCedo.getId(), sabado.getId());
        assertThat(grade).allSatisfy(item -> {
            assertThat(item.disciplinaNome()).isEqualTo("Matematica");
            assertThat(item.professorNome()).isEqualTo("Ana");
        });
    }

    @Test
    void gradeVaziaNaoConsultaPessoas() {
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.of(turma1A));
        when(horarioRepository.findAllByEscolaIdAndAlocacao_Turma_Id(ESCOLA_A, 1L)).thenReturn(List.of());

        assertThat(horarioService.listar(1L)).isEmpty();
        verify(pessoaRepository, never()).findAllByIdInAndEscolaId(any(), any());
    }

    @Test
    void listarTurmaDeOutraEscolaResponde404() {
        when(turmaRepository.findByIdAndEscolaId(1L, ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> horarioService.listar(1L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
