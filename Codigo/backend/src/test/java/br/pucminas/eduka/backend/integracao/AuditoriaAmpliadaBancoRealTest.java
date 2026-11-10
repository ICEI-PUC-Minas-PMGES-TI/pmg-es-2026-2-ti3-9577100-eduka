package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.StatusMatricula;
import br.pucminas.eduka.backend.domain.VinculoProfessor;
import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.AtualizarMatriculaRequest;
import br.pucminas.eduka.backend.dto.AutorAtividadeResponse;
import br.pucminas.eduka.backend.dto.MatriculaTurmaRequest;
import br.pucminas.eduka.backend.dto.MembroEquipeRequest;
import br.pucminas.eduka.backend.dto.MembroEquipeResponse;
import br.pucminas.eduka.backend.dto.ProfessorRequest;
import br.pucminas.eduka.backend.dto.ProfessorResponse;
import br.pucminas.eduka.backend.service.AlunoService;
import br.pucminas.eduka.backend.service.DashboardFinanceiroService;
import br.pucminas.eduka.backend.service.EquipeService;
import br.pucminas.eduka.backend.service.MatriculaTurmaService;
import br.pucminas.eduka.backend.service.ProfessorService;
import br.pucminas.eduka.backend.service.RegistroAtividadesService;

/**
 * Auditoria alem do financeiro no Postgres: cada cadastro grava a linha com o
 * autor e a escola do token, as restricoes da V44 aceitam as entidades e
 * operacoes novas, e o painel do financeiro continua so com o dinheiro.
 */
class AuditoriaAmpliadaBancoRealTest extends BancoRealTest {

    @Autowired
    private AlunoService alunoService;
    @Autowired
    private ProfessorService professorService;
    @Autowired
    private EquipeService equipeService;
    @Autowired
    private MatriculaTurmaService matriculaService;
    @Autowired
    private DashboardFinanceiroService dashboardFinanceiroService;
    @Autowired
    private RegistroAtividadesService registroAtividadesService;

    /** "OPERACAO ENTIDADE" de cada linha do registro, na ordem em que aconteceu. */
    private List<String> log(UUID escola, UUID registro) {
        return jdbc.queryForList("SELECT OPERACAO || ' ' || ENTIDADE FROM LOG_AUDITORIA "
                + "WHERE ESCOLA_ID = ? AND REGISTRO_ID = ? ORDER BY DATA_HORA", String.class, escola, registro);
    }

    private List<UUID> autores(UUID escola) {
        return jdbc.queryForList("SELECT DISTINCT USUARIO_ID FROM LOG_AUDITORIA WHERE ESCOLA_ID = ?", UUID.class,
                escola);
    }

    @Test
    void cadastrosDaEscolaEntramNoLogComOAutorEAEscolaDoToken() {
        UUID escola = dados.escola();
        UUID master = dados.usuario(escola, "MASTER", "Marta Gestora");
        autenticarComo(master, "MASTER", escola);

        AlunoResponse aluno = alunoService.cadastrar(new AlunoRequest("Ana Souza", "52998224725",
                DadosDeTeste.unico() + "@aluno.com", null, null, "M-" + DadosDeTeste.unico(), null, null));
        alunoService.atualizar(aluno.id(), new AlunoRequest("Ana Souza Lima", "52998224725", aluno.email(), null,
                null, aluno.matricula(), null, null));

        ProfessorResponse professor = professorService.cadastrar(new ProfessorRequest("Paulo Mestre", "11144477735",
                DadosDeTeste.unico() + "@prof.com", null, null, null, VinculoProfessor.CLT_40H));
        professorService.inativar(professor.id());
        // Inativar de novo nao muda nada, entao nao entra no log.
        professorService.inativar(professor.id());

        MembroEquipeResponse financeiro = equipeService.cadastrar(new MembroEquipeRequest(PerfilUsuario.FINANCEIRO,
                "Fabio Contas", "12345678909", DadosDeTeste.unico() + "@fin.com", null));
        equipeService.reenviarAcesso(financeiro.id());
        equipeService.inativar(financeiro.id());

        long turma = dados.turma(escola, "1A", 30);
        UUID matricula = matriculaService.matricular(new MatriculaTurmaRequest(aluno.id(), turma, null, null)).id();
        matriculaService.atualizar(matricula, new AtualizarMatriculaRequest(StatusMatricula.CANCELADA, null, null));

        assertThat(log(escola, aluno.id())).containsExactlyInAnyOrder("CRIAR ALUNO", "EDITAR ALUNO");
        assertThat(log(escola, professor.id())).containsExactlyInAnyOrder("CRIAR PROFESSOR", "INATIVAR PROFESSOR");
        assertThat(log(escola, financeiro.id()))
                .containsExactlyInAnyOrder("CRIAR USUARIO", "REENVIAR_ACESSO USUARIO", "INATIVAR USUARIO");
        assertThat(log(escola, matricula)).containsExactlyInAnyOrder("CRIAR MATRICULA", "CANCELAR MATRICULA");
        assertThat(autores(escola)).containsExactly(master);
    }

    @Test
    void filtroPorPessoaListaSoQuemFezAlgoNaEscolaPorNome() {
        UUID escola = dados.escola();
        UUID outraEscola = dados.escola();
        UUID master = dados.usuario(escola, "MASTER", "Marta Gestora");
        UUID fernanda = dados.usuario(escola, "FINANCEIRO", "Fernanda Rocha");
        dados.usuario(escola, "SECRETARIA", "Sem Atividade");
        UUID deFora = dados.usuario(outraEscola, "MASTER", "Outra Escola");
        Instant agora = Instant.now();
        dados.acaoNoLog(escola, master, OperacaoAuditoria.CRIAR, EntidadeAuditada.ALUNO, UUID.randomUUID(), agora);
        dados.acaoNoLog(escola, master, OperacaoAuditoria.EDITAR, EntidadeAuditada.ALUNO, UUID.randomUUID(), agora);
        dados.acaoNoLog(escola, fernanda, OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, UUID.randomUUID(),
                agora);
        dados.acaoNoLog(outraEscola, deFora, OperacaoAuditoria.CRIAR, EntidadeAuditada.ALUNO, UUID.randomUUID(),
                agora);
        autenticarComo(master, "MASTER", escola);

        assertThat(registroAtividadesService.listarAutores())
                .extracting(AutorAtividadeResponse::nome, AutorAtividadeResponse::perfil)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Fernanda Rocha", PerfilUsuario.FINANCEIRO),
                        org.assertj.core.groups.Tuple.tuple("Marta Gestora", PerfilUsuario.MASTER));
    }

    @Test
    void ultimasAcoesDoPainelFinanceiroFicamSoComODinheiro() {
        UUID escola = dados.escola();
        UUID fernanda = dados.usuario(escola, "FINANCEIRO", "Fernanda Rocha");
        UUID cobranca = UUID.randomUUID();
        dados.acaoNoLog(escola, fernanda, OperacaoAuditoria.CRIAR, EntidadeAuditada.COBRANCA, cobranca,
                Instant.now().minusSeconds(60));
        dados.acaoNoLog(escola, fernanda, OperacaoAuditoria.CRIAR, EntidadeAuditada.ALUNO, UUID.randomUUID(),
                Instant.now());

        autenticarComo("FINANCEIRO", escola);

        assertThat(dashboardFinanceiroService.painel().ultimasAcoes())
                .extracting(acao -> acao.registroId())
                .containsExactly(cobranca);
    }
}
