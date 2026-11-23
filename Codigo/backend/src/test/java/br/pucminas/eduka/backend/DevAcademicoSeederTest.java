package br.pucminas.eduka.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import br.pucminas.eduka.backend.domain.academico.Disciplina;
import br.pucminas.eduka.backend.domain.academico.Turma;
import br.pucminas.eduka.backend.domain.acesso.Usuario;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.repository.academico.CursoRepository;
import br.pucminas.eduka.backend.repository.academico.DisciplinaRepository;
import br.pucminas.eduka.backend.repository.academico.PeriodoLetivoRepository;
import br.pucminas.eduka.backend.repository.academico.TurmaRepository;
import br.pucminas.eduka.backend.repository.acesso.UsuarioRepository;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;

@ExtendWith(MockitoExtension.class)
class DevAcademicoSeederTest {

    private static final String LOGIN_SECRETARIA_DEV = "secretaria@dev.eduka.com.br";
    private static final UUID ESCOLA_DEV = UUID.randomUUID();

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EscolaRepository escolaRepository;
    @Mock
    private PeriodoLetivoRepository periodoLetivoRepository;
    @Mock
    private CursoRepository cursoRepository;
    @Mock
    private DisciplinaRepository disciplinaRepository;
    @Mock
    private TurmaRepository turmaRepository;

    private DevAcademicoSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevAcademicoSeeder(usuarioRepository, escolaRepository, periodoLetivoRepository,
                cursoRepository, disciplinaRepository, turmaRepository);
    }

    private void secretariaDevExiste() {
        when(usuarioRepository.findByLogin(LOGIN_SECRETARIA_DEV))
                .thenReturn(Optional.of(Usuario.builder().escolaId(ESCOLA_DEV).build()));
    }

    @Test
    void criaTurmasComGradeNaEscolaDev() {
        Escola escolaDev = mock(Escola.class);
        secretariaDevExiste();
        when(turmaRepository.findAllByEscolaId(ESCOLA_DEV)).thenReturn(List.of());
        when(escolaRepository.getReferenceById(ESCOLA_DEV)).thenReturn(escolaDev);
        when(periodoLetivoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cursoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(disciplinaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        seeder.run(null);

        ArgumentCaptor<Turma> turmaCaptor = ArgumentCaptor.forClass(Turma.class);
        verify(turmaRepository, times(5)).save(turmaCaptor.capture());
        List<Turma> turmas = turmaCaptor.getAllValues();
        assertThat(turmas).extracting(Turma::getEscola).containsOnly(escolaDev);
        assertThat(turmas).extracting(Turma::getAtiva).containsOnly(true);
        assertThat(turmas).allSatisfy(turma -> assertThat(turma.getDisciplinas()).hasSize(5));
        assertThat(turmas.get(0).getNome()).isEqualTo("2º ano A");
        assertThat(turmas.get(0).getDisciplinas()).extracting(Disciplina::getNome)
                .contains("Física").doesNotContain("Ciências");
    }

    @Test
    void naoSemeiaDeNovoQuandoAEscolaJaTemTurma() {
        secretariaDevExiste();
        when(turmaRepository.findAllByEscolaId(ESCOLA_DEV)).thenReturn(List.of(new Turma()));

        seeder.run(null);

        verify(periodoLetivoRepository, never()).save(any());
        verify(turmaRepository, never()).save(any());
    }

    @Test
    void semEscolaDevNaoFazNada() {
        when(usuarioRepository.findByLogin(LOGIN_SECRETARIA_DEV)).thenReturn(Optional.empty());

        seeder.run(null);

        verify(turmaRepository, never()).findAllByEscolaId(any());
        verify(turmaRepository, never()).save(any());
    }
}
