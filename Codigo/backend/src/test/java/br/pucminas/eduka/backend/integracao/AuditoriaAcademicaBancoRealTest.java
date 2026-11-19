package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.academico.Turno;
import br.pucminas.eduka.backend.dto.academico.CursoRequest;
import br.pucminas.eduka.backend.dto.academico.CursoResponse;
import br.pucminas.eduka.backend.dto.academico.DisciplinaRequest;
import br.pucminas.eduka.backend.dto.academico.DisciplinaResponse;
import br.pucminas.eduka.backend.dto.academico.PeriodoLetivoRequest;
import br.pucminas.eduka.backend.dto.academico.PeriodoLetivoResponse;
import br.pucminas.eduka.backend.dto.academico.TurmaRequest;
import br.pucminas.eduka.backend.dto.academico.TurmaResponse;
import br.pucminas.eduka.backend.dto.financeiro.CategoriaDespesaRequest;
import br.pucminas.eduka.backend.dto.financeiro.CategoriaDespesaResponse;
import br.pucminas.eduka.backend.dto.pessoas.CadastroResponsavelRequest;
import br.pucminas.eduka.backend.dto.pessoas.CadastroResponsavelResponse;
import br.pucminas.eduka.backend.service.academico.AcademicoSecretariaService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;
import br.pucminas.eduka.backend.service.financeiro.CategoriaDespesaService;
import br.pucminas.eduka.backend.service.pessoas.ResponsavelService;

/**
 * Auditoria da estrutura academica e dos cadastros de apoio no Postgres: as
 * restricoes da V53 aceitam as entidades e operacoes novas, e o id numerico
 * (periodo, curso, disciplina e turma) vai para o REGISTRO_ID sem perder nada.
 */
class AuditoriaAcademicaBancoRealTest extends BancoRealTest {

    @Autowired
    private AcademicoSecretariaService academicoService;
    @Autowired
    private CategoriaDespesaService categoriaService;
    @Autowired
    private ResponsavelService responsavelService;

    /**
     * "OPERACAO ENTIDADE" de cada linha do registro, na ordem em que aconteceu.
     * O registro e ENTIDADE + REGISTRO_ID: periodo, curso, disciplina e turma
     * podem ter o mesmo numero, cada um na sua sequencia.
     */
    private List<String> log(UUID escola, String entidade, UUID registro) {
        return jdbc.queryForList("SELECT OPERACAO || ' ' || ENTIDADE FROM LOG_AUDITORIA "
                + "WHERE ESCOLA_ID = ? AND ENTIDADE = ? AND REGISTRO_ID = ? ORDER BY DATA_HORA", String.class,
                escola, entidade, registro);
    }

    @Test
    void estruturaAcademicaEntraNoLogPeloIdNumerico() {
        UUID escola = dados.escola();
        entrarComo("SECRETARIA", escola);

        PeriodoLetivoRequest periodoRequest = new PeriodoLetivoRequest();
        periodoRequest.setNome("2027/1");
        periodoRequest.setDataInicio(LocalDate.now().plusMonths(2));
        periodoRequest.setDataFim(LocalDate.now().plusMonths(8));
        PeriodoLetivoResponse periodo = academicoService.cadastrarPeriodoLetivo(escola, periodoRequest);
        academicoService.abrirMatriculas(escola, periodo.getId());

        CursoRequest cursoRequest = new CursoRequest();
        cursoRequest.setNome("Ensino Médio");
        CursoResponse curso = academicoService.cadastrarCurso(escola, cursoRequest);

        DisciplinaRequest disciplinaRequest = new DisciplinaRequest();
        disciplinaRequest.setNome("Matemática");
        disciplinaRequest.setCursoId(curso.getId());
        disciplinaRequest.setCargaHoraria(80);
        DisciplinaResponse disciplina = academicoService.cadastrarDisciplina(escola, disciplinaRequest);

        TurmaRequest turmaRequest = new TurmaRequest();
        turmaRequest.setPeriodoLetivoId(periodo.getId());
        turmaRequest.setCursoId(curso.getId());
        turmaRequest.setNome("1A");
        turmaRequest.setVagas(30);
        turmaRequest.setTurno(Turno.MATUTINO);
        turmaRequest.setDisciplinaIds(Set.of(disciplina.getId()));
        TurmaResponse turma = academicoService.cadastrarTurma(escola, turmaRequest);
        turmaRequest.setVagas(35);
        academicoService.atualizarTurma(escola, turma.getId(), turmaRequest);
        academicoService.excluirTurma(escola, turma.getId());

        assertThat(log(escola, "PERIODO_LETIVO", AuditoriaService.idNumerico(periodo.getId())))
                .containsExactly("CRIAR PERIODO_LETIVO", "ABRIR_MATRICULAS PERIODO_LETIVO");
        assertThat(log(escola, "CURSO", AuditoriaService.idNumerico(curso.getId()))).containsExactly("CRIAR CURSO");
        assertThat(log(escola, "DISCIPLINA", AuditoriaService.idNumerico(disciplina.getId())))
                .containsExactly("CRIAR DISCIPLINA");
        assertThat(log(escola, "TURMA", AuditoriaService.idNumerico(turma.getId())))
                .containsExactly("CRIAR TURMA", "EDITAR TURMA", "EXCLUIR TURMA");
        // O numero volta inteiro do REGISTRO_ID.
        UUID gravado = jdbc.queryForObject("SELECT REGISTRO_ID FROM LOG_AUDITORIA WHERE ESCOLA_ID = ? "
                + "AND ENTIDADE = 'TURMA' AND OPERACAO = 'CRIAR'", UUID.class, escola);
        assertThat(gravado.getLeastSignificantBits()).isEqualTo(turma.getId());
    }

    @Test
    void categoriaEResponsavelEntramNoLog() {
        UUID escola = dados.escola();
        entrarComo("SECRETARIA", escola);
        CadastroResponsavelResponse responsavel = responsavelService
                .cadastrar(new CadastroResponsavelRequest("Carla Souza", "11144477735", null, null, null));
        responsavelService.atualizar(responsavel.id(),
                new CadastroResponsavelRequest("Carla Souza Lima", "11144477735", null, null, null));
        responsavelService.excluir(responsavel.id());
        assertThat(log(escola, "RESPONSAVEL", responsavel.id()))
                .containsExactly("CRIAR RESPONSAVEL", "EDITAR RESPONSAVEL", "EXCLUIR RESPONSAVEL");

        entrarComo("FINANCEIRO", escola);
        // As categorias padrao, criadas sozinhas no primeiro acesso, nao entram no log.
        categoriaService.listar();
        CategoriaDespesaResponse categoria = categoriaService.criar(new CategoriaDespesaRequest("Transporte"));
        assertThat(log(escola, "CATEGORIA_DESPESA", categoria.id())).containsExactly("CRIAR CATEGORIA_DESPESA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM LOG_AUDITORIA WHERE ESCOLA_ID = ? "
                + "AND ENTIDADE = 'CATEGORIA_DESPESA'", Long.class, escola)).isEqualTo(1L);
    }
}
