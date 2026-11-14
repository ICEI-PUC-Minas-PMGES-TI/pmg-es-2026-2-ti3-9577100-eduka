package br.pucminas.eduka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import br.pucminas.eduka.backend.domain.academico.AlocacaoProfessor;
import br.pucminas.eduka.backend.domain.academico.Avaliacao;
import br.pucminas.eduka.backend.domain.academico.Curso;
import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.MatriculaTurma;
import br.pucminas.eduka.backend.domain.academico.Nota;
import br.pucminas.eduka.backend.domain.academico.PeriodoLetivo;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.domain.pessoas.Aluno;
import br.pucminas.eduka.backend.domain.pessoas.Pessoa;
import br.pucminas.eduka.backend.domain.pessoas.Professor;
import br.pucminas.eduka.backend.repository.academico.AlocacaoProfessorRepository;
import br.pucminas.eduka.backend.repository.academico.AvaliacaoRepository;
import br.pucminas.eduka.backend.repository.academico.CursoRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.MatriculaTurmaRepository;
import br.pucminas.eduka.backend.repository.academico.NotaRepository;
import br.pucminas.eduka.backend.repository.academico.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.repository.pessoas.AlunoRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.pessoas.PessoaRepository;
import br.pucminas.eduka.backend.repository.pessoas.ProfessorRepository;

@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

    private static final String LOGIN_SUPER_ADMIN = "admin@eduka.com.br";
    private static final String LOGIN_PROFESSOR = "professor@dev.eduka.com.br";
    private static final String LOGIN_ALUNO = "aluno@dev.eduka.com.br";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PessoaRepository pessoaRepository;
    @Mock
    private PessoaPapelRepository pessoaPapelRepository;
    @Mock
    private EscolaRepository escolaRepository;
    @Mock
    private ProfessorRepository professorRepository;
    @Mock
    private PeriodoLetivoRepository periodoLetivoRepository;
    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private DisciplinaRepository disciplinaRepository;
    @Mock
    private TurmaRepository turmaRepository;
    @Mock
    private AlocacaoProfessorRepository alocacaoRepository;
    @Mock
    private AlunoRepository alunoRepository;
    @Mock
    private MatriculaTurmaRepository matriculaRepository;
    @Mock
    private AvaliacaoRepository avaliacaoRepository;
    @Mock
    private NotaRepository notaRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PlatformTransactionManager transactionManager;

    private DevDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevDataSeeder(usuarioRepository, pessoaRepository, pessoaPapelRepository, escolaRepository,
                professorRepository, periodoLetivoRepository, cursoRepository, disciplinaRepository,
                turmaRepository, alocacaoRepository, alunoRepository, matriculaRepository, avaliacaoRepository,
                notaRepository, passwordEncoder, transactionManager);
    }

    @Test
    void criaSuperAdminQuandoAindaNaoExiste() {
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);
        when(usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)).thenReturn(false);
        salvarPessoaComId();
        when(passwordEncoder.encode("Eduka-admin123")).thenReturn("{bcrypt}hash");

        seeder.run(null);

        verify(pessoaRepository).save(any(Pessoa.class));
        verify(pessoaPapelRepository).save(any());
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getLogin()).isEqualTo(LOGIN_SUPER_ADMIN);
        assertThat(usuarioCaptor.getValue().getSenhaHash()).isEqualTo("{bcrypt}hash");
        assertThat(usuarioCaptor.getValue().getEscolaId()).isNull();
    }

    @Test
    void naoRecriaQuandoTodosJaExistem() {
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);

        seeder.run(null);

        verify(pessoaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
        verify(escolaRepository, never()).save(any());
    }

    @Test
    void criaEscolaDevEUmUsuarioPorPerfilDeEscola() {
        UUID escolaDevId = UUID.randomUUID();
        Escola escolaDev = mock(Escola.class);
        when(escolaDev.getId()).thenReturn(escolaDevId);
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(false);
        when(usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)).thenReturn(true);
        when(usuarioRepository.findByLogin(anyString())).thenReturn(Optional.empty());
        when(escolaRepository.save(any(Escola.class))).thenReturn(escolaDev);
        salvarPessoaComId();
        when(passwordEncoder.encode("Eduka-dev123")).thenReturn("{bcrypt}dev");

        seeder.run(null);

        verify(escolaRepository).save(any(Escola.class));
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(5)).save(usuarioCaptor.capture());
        List<Usuario> criados = usuarioCaptor.getAllValues();
        assertThat(criados).extracting(Usuario::getPerfil).containsExactly(PerfilUsuario.MASTER,
                PerfilUsuario.SECRETARIA, PerfilUsuario.FINANCEIRO, PerfilUsuario.PROFESSOR, PerfilUsuario.ALUNO);
        assertThat(criados).extracting(Usuario::getEscolaId).containsOnly(escolaDevId);
        assertThat(criados).extracting(Usuario::getSenhaHash).containsOnly("{bcrypt}dev");
    }

    @Test
    void reaproveitaEscolaDevDeUsuarioJaCriado() {
        UUID escolaDevId = UUID.randomUUID();
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(false);
        when(usuarioRepository.existsByLogin(LOGIN_SUPER_ADMIN)).thenReturn(true);
        when(usuarioRepository.existsByLogin("master@dev.eduka.com.br")).thenReturn(true);
        when(usuarioRepository.findByLogin("master@dev.eduka.com.br"))
                .thenReturn(Optional.of(Usuario.builder().escolaId(escolaDevId).build()));
        salvarPessoaComId();

        seeder.run(null);

        verify(escolaRepository, never()).save(any());
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(4)).save(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getAllValues()).extracting(Usuario::getEscolaId).containsOnly(escolaDevId);
    }

    @Test
    void criaCadastroDoProfessorDevETurmasAlocadas() {
        Usuario professorDev = professorDev();
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);
        when(usuarioRepository.findByLogin(LOGIN_PROFESSOR)).thenReturn(Optional.of(professorDev));
        when(professorRepository.findByUsuarioIdAndEscolaId(professorDev.getId(), professorDev.getEscolaId()))
                .thenReturn(Optional.empty());
        when(professorRepository.save(any(Professor.class))).thenAnswer(inv -> {
            Professor professor = inv.getArgument(0);
            professor.setId(UUID.randomUUID());
            return professor;
        });
        when(alocacaoRepository.findAllByEscolaIdAndProfessorId(any(), any())).thenReturn(List.of());
        when(periodoLetivoRepository.save(any(PeriodoLetivo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cursoRepository.save(any(Curso.class))).thenAnswer(inv -> inv.getArgument(0));
        when(disciplinaRepository.save(any(Disciplina.class))).thenAnswer(inv -> inv.getArgument(0));
        when(turmaRepository.saveAndFlush(any(Turma.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.run(null);

        ArgumentCaptor<Professor> professorCaptor = ArgumentCaptor.forClass(Professor.class);
        verify(professorRepository).save(professorCaptor.capture());
        assertThat(professorCaptor.getValue().getUsuarioId()).isEqualTo(professorDev.getId());
        assertThat(professorCaptor.getValue().getPessoaId()).isEqualTo(professorDev.getPessoaId());
        assertThat(professorCaptor.getValue().getEscolaId()).isEqualTo(professorDev.getEscolaId());
        verify(turmaRepository, times(3)).saveAndFlush(any(Turma.class));
        ArgumentCaptor<AlocacaoProfessor> alocacaoCaptor = ArgumentCaptor.forClass(AlocacaoProfessor.class);
        verify(alocacaoRepository, times(4)).save(alocacaoCaptor.capture());
        assertThat(alocacaoCaptor.getAllValues()).extracting(AlocacaoProfessor::getProfessor)
                .containsOnly(professorCaptor.getValue());
    }

    @Test
    void naoRecriaTurmasQuandoProfessorDevJaTemAlocacao() {
        Usuario professorDev = professorDev();
        Professor professor = Professor.builder().id(UUID.randomUUID()).build();
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);
        when(usuarioRepository.findByLogin(LOGIN_PROFESSOR)).thenReturn(Optional.of(professorDev));
        when(professorRepository.findByUsuarioIdAndEscolaId(professorDev.getId(), professorDev.getEscolaId()))
                .thenReturn(Optional.of(professor));
        when(alocacaoRepository.findAllByEscolaIdAndProfessorId(professorDev.getEscolaId(), professor.getId()))
                .thenReturn(List.of(AlocacaoProfessor.builder().build()));

        seeder.run(null);

        verify(professorRepository, never()).save(any());
        verify(turmaRepository, never()).saveAndFlush(any());
        verify(alocacaoRepository, never()).save(any());
    }

    @Test
    void matriculaOAlunoDevNaTurmaDoProfessorComAvaliacoesENotas() {
        Usuario alunoDev = alunoDev();
        UUID escolaId = alunoDev.getEscolaId();
        Turma turma = Turma.builder().id(1L).codigo("EM-1A").build();
        Professor professor = Professor.builder().id(UUID.randomUUID()).build();
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);
        // O seeder procura o professor dev antes do aluno: aqui ele nao existe.
        when(usuarioRepository.findByLogin(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByLogin(LOGIN_ALUNO)).thenReturn(Optional.of(alunoDev));
        when(alunoRepository.findByUsuarioIdAndEscolaId(alunoDev.getId(), escolaId)).thenReturn(Optional.empty());
        when(turmaRepository.findAllByEscolaId(escolaId)).thenReturn(List.of(
                Turma.builder().id(2L).codigo("EM-2B").build(), turma));
        when(alocacaoRepository.findAllByEscolaIdAndTurmaId(escolaId, 1L)).thenReturn(List.of(
                alocacao(escolaId, professor, turma, "MAT"), alocacao(escolaId, professor, turma, "FIS")));
        when(alunoRepository.save(any(Aluno.class))).thenAnswer(inv -> inv.getArgument(0));
        when(avaliacaoRepository.save(any(Avaliacao.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.run(null);

        ArgumentCaptor<Aluno> alunoCaptor = ArgumentCaptor.forClass(Aluno.class);
        verify(alunoRepository).save(alunoCaptor.capture());
        assertThat(alunoCaptor.getValue().getUsuarioId()).isEqualTo(alunoDev.getId());
        assertThat(alunoCaptor.getValue().getPessoaId()).isEqualTo(alunoDev.getPessoaId());
        ArgumentCaptor<MatriculaTurma> matriculaCaptor = ArgumentCaptor.forClass(MatriculaTurma.class);
        verify(matriculaRepository).save(matriculaCaptor.capture());
        assertThat(matriculaCaptor.getValue().getTurma()).isSameAs(turma);
        // Matematica: 3 avaliacoes (2 com nota); Fisica: 2 (1 com nota).
        verify(avaliacaoRepository, times(5)).save(any(Avaliacao.class));
        verify(notaRepository, times(3)).save(any(Nota.class));
    }

    @Test
    void naoRecriaOAlunoDevQuandoJaTemCadastro() {
        Usuario alunoDev = alunoDev();
        when(usuarioRepository.existsByLogin(anyString())).thenReturn(true);
        // O seeder procura o professor dev antes do aluno: aqui ele nao existe.
        when(usuarioRepository.findByLogin(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByLogin(LOGIN_ALUNO)).thenReturn(Optional.of(alunoDev));
        when(alunoRepository.findByUsuarioIdAndEscolaId(alunoDev.getId(), alunoDev.getEscolaId()))
                .thenReturn(Optional.of(Aluno.builder().id(UUID.randomUUID()).build()));

        seeder.run(null);

        verify(alunoRepository, never()).save(any());
        verify(matriculaRepository, never()).save(any());
        verify(avaliacaoRepository, never()).save(any());
    }

    private static AlocacaoProfessor alocacao(UUID escolaId, Professor professor, Turma turma, String codigo) {
        return AlocacaoProfessor.builder().escolaId(escolaId).professor(professor).turma(turma)
                .disciplina(Disciplina.builder().codigo(codigo).build()).build();
    }

    private static Usuario alunoDev() {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(UUID.randomUUID())
                .pessoaId(UUID.randomUUID())
                .login(LOGIN_ALUNO)
                .perfil(PerfilUsuario.ALUNO)
                .build();
    }

    private static Usuario professorDev() {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .escolaId(UUID.randomUUID())
                .pessoaId(UUID.randomUUID())
                .login(LOGIN_PROFESSOR)
                .perfil(PerfilUsuario.PROFESSOR)
                .build();
    }

    private void salvarPessoaComId() {
        when(pessoaRepository.save(any(Pessoa.class))).thenAnswer(inv -> {
            Pessoa pessoa = inv.getArgument(0);
            pessoa.setId(UUID.randomUUID());
            return pessoa;
        });
    }
}
