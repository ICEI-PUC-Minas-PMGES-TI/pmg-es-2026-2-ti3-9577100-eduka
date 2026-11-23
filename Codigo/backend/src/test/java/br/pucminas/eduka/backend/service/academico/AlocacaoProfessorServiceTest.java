package br.pucminas.eduka.backend.service.academico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.dto.academico.AlocacaoProfessorRequest;
import br.pucminas.eduka.backend.dto.academico.AlocacaoProfessorResponse;
import br.pucminas.eduka.backend.exception.academico.AlocacaoJaExisteException;
import br.pucminas.eduka.backend.exception.comum.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

/**
 * Regras de alocacao de professor em disciplina de turma: isolamento por
 * escola, professor e turma ativos, disciplina na grade da turma e um
 * professor por disciplina em cada turma.
 */
@ExtendWith(MockitoExtension.class)
class AlocacaoProfessorServiceTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();

    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private AlocacaoProfessorRepository alocacaoRepository;
    @Mock
    private ProfessorRepository professorRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private DisciplinaRepository disciplinaRepository;

    private AlocacaoProfessorService alocacaoService;

    private Pessoa pessoaAna;
    private Professor ana;
    private Disciplina matematica;
    private Turma turma;

    @BeforeEach
    void setUp() {
        alocacaoService = new AlocacaoProfessorService(alocacaoRepository, professorRepository, pessoaRepository,
                turmaRepository, disciplinaRepository, new IsolamentoEscola(), auditoriaService);
        autenticarComo("SECRETARIA", ESCOLA_A);

        pessoaAna = Pessoa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Ana").ativo(true).build();
        ana = Professor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).pessoaId(pessoaAna.getId()).ativo(true)
                .build();
        matematica = Disciplina.builder().id(10L).nome("Matematica").cargaHoraria(80).build();
        turma = Turma.builder().id(1L).nome("1A").turno(Turno.MATUTINO).vagas(30)
                .periodoLetivo(PeriodoLetivo.builder().id(5L).nome("2026").build())
                .disciplinas(Set.of(matematica)).build();
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

    private void stubBuscas() {
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(turmaRepository.findByIdAndEscolaId(turma.getId(), ESCOLA_A)).thenReturn(Optional.of(turma));
    }

    @Test
    void alocaComAEscolaDoTokenEDevolveNomes() {
        stubBuscas();
        when(disciplinaRepository.findByIdAndEscolaId(matematica.getId(), ESCOLA_A))
                .thenReturn(Optional.of(matematica));
        when(pessoaRepository.findByIdAndEscolaId(pessoaAna.getId(), ESCOLA_A)).thenReturn(Optional.of(pessoaAna));
        when(alocacaoRepository.saveAndFlush(any(AlocacaoProfessor.class))).thenAnswer(inv -> {
            AlocacaoProfessor alocacao = inv.getArgument(0);
            alocacao.setId(UUID.randomUUID());
            return alocacao;
        });

        AlocacaoProfessorResponse resposta = alocacaoService.alocar(ana.getId(),
                new AlocacaoProfessorRequest(turma.getId(), matematica.getId()));

        ArgumentCaptor<AlocacaoProfessor> captor = ArgumentCaptor.forClass(AlocacaoProfessor.class);
        verify(alocacaoRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(resposta.professorNome()).isEqualTo("Ana");
        assertThat(resposta.turmaNome()).isEqualTo("1A");
        assertThat(resposta.disciplinaNome()).isEqualTo("Matematica");
        assertThat(resposta.periodoLetivoNome()).isEqualTo("2026");
    }

    @Test
    void professorInativoNaoEAlocado() {
        ana.setAtivo(false);
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> alocacaoService.alocar(ana.getId(),
                new AlocacaoProfessorRequest(turma.getId(), matematica.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void turmaInativaNaoRecebeAlocacao() {
        turma.setAtiva(false);
        stubBuscas();

        assertThatThrownBy(() -> alocacaoService.alocar(ana.getId(),
                new AlocacaoProfessorRequest(turma.getId(), matematica.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void disciplinaForaDaGradeDaTurmaERecusada() {
        stubBuscas();
        Disciplina historia = Disciplina.builder().id(20L).nome("Historia").cargaHoraria(40).build();
        when(disciplinaRepository.findByIdAndEscolaId(historia.getId(), ESCOLA_A)).thenReturn(Optional.of(historia));

        assertThatThrownBy(() -> alocacaoService.alocar(ana.getId(),
                new AlocacaoProfessorRequest(turma.getId(), historia.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao faz parte da turma");
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void disciplinaJaComProfessorNaTurmaResponde409() {
        stubBuscas();
        when(disciplinaRepository.findByIdAndEscolaId(matematica.getId(), ESCOLA_A))
                .thenReturn(Optional.of(matematica));
        when(alocacaoRepository.existsByEscolaIdAndTurmaIdAndDisciplinaId(ESCOLA_A, turma.getId(),
                matematica.getId())).thenReturn(true);

        assertThatThrownBy(() -> alocacaoService.alocar(ana.getId(),
                new AlocacaoProfessorRequest(turma.getId(), matematica.getId())))
                .isInstanceOf(AlocacaoJaExisteException.class);
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void alocacaoSimultaneaBarradaPeloBancoViraConflito() {
        stubBuscas();
        when(disciplinaRepository.findByIdAndEscolaId(matematica.getId(), ESCOLA_A))
                .thenReturn(Optional.of(matematica));
        when(alocacaoRepository.saveAndFlush(any(AlocacaoProfessor.class)))
                .thenThrow(new DataIntegrityViolationException("uk_alocacao_turma_disciplina"));

        assertThatThrownBy(() -> alocacaoService.alocar(ana.getId(),
                new AlocacaoProfessorRequest(turma.getId(), matematica.getId())))
                .isInstanceOf(AlocacaoJaExisteException.class);
    }

    @Test
    void turmaOuDisciplinaDeOutraEscolaResponde404() {
        when(professorRepository.findByIdAndEscolaId(ana.getId(), ESCOLA_A)).thenReturn(Optional.of(ana));
        when(turmaRepository.findByIdAndEscolaId(99L, ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alocacaoService.alocar(ana.getId(), new AlocacaoProfessorRequest(99L, 10L)))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Turma nao encontrada");

        when(turmaRepository.findByIdAndEscolaId(turma.getId(), ESCOLA_A)).thenReturn(Optional.of(turma));
        when(disciplinaRepository.findByIdAndEscolaId(99L, ESCOLA_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alocacaoService.alocar(ana.getId(),
                new AlocacaoProfessorRequest(turma.getId(), 99L)))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Disciplina nao encontrada");
        verify(alocacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void listarPorTurmaTrazONomeDeCadaProfessor() {
        when(turmaRepository.findByIdAndEscolaId(turma.getId(), ESCOLA_A)).thenReturn(Optional.of(turma));
        AlocacaoProfessor alocacao = AlocacaoProfessor.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A)
                .professor(ana).turma(turma).disciplina(matematica).build();
        when(alocacaoRepository.findAllByEscolaIdAndTurmaId(ESCOLA_A, turma.getId())).thenReturn(List.of(alocacao));
        when(pessoaRepository.findAllByIdInAndEscolaId(any(), eq(ESCOLA_A))).thenReturn(List.of(pessoaAna));

        List<AlocacaoProfessorResponse> resultado = alocacaoService.listarPorTurma(turma.getId());

        assertThat(resultado).singleElement().satisfies(r -> {
            assertThat(r.professorId()).isEqualTo(ana.getId());
            assertThat(r.professorNome()).isEqualTo("Ana");
            assertThat(r.disciplinaId()).isEqualTo(matematica.getId());
        });
    }

    @Test
    void desalocarDeOutroProfessorOuEscolaResponde404() {
        UUID alocacaoId = UUID.randomUUID();
        when(alocacaoRepository.findByIdAndEscolaIdAndProfessorId(alocacaoId, ESCOLA_A, ana.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> alocacaoService.desalocar(ana.getId(), alocacaoId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(alocacaoRepository, never()).delete(any());
    }
}
