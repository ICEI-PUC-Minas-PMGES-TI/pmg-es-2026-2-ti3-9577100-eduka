package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.pessoas.AlunoRequest;
import br.pucminas.eduka.backend.dto.pessoas.AlunoResponse;
import br.pucminas.eduka.backend.dto.pessoas.ResponsavelRequest;
import br.pucminas.eduka.backend.exception.comum.RegistroEmUsoException;
import br.pucminas.eduka.backend.service.pessoas.AlunoService;
import br.pucminas.eduka.backend.service.privacidade.DadosPessoaisAlunoService;

/**
 * LGPD no Postgres: o PDF sai e entra no log; a anonimizacao apaga o dado
 * pessoal (inclusive o cifrado), desliga o login e deixa o financeiro
 * intacto, e so vale para quem ja saiu da escola sem divida.
 */
class DadosPessoaisAlunoBancoRealTest extends BancoRealTest {

    @Autowired
    private AlunoService alunoService;
    @Autowired
    private DadosPessoaisAlunoService dadosPessoaisAlunoService;

    private UUID escola;

    private AlunoResponse alunoQueSaiu() {
        escola = dados.escola();
        UUID master = dados.usuario(escola, "MASTER", "Marta Gestora");
        autenticarComo(master, "MASTER", escola);
        AlunoResponse aluno = alunoService.cadastrar(new AlunoRequest("Ana Souza", "52998224725",
                DadosDeTeste.unico() + "@aluno.com", LocalDate.of(2010, 3, 14), null, "M-" + DadosDeTeste.unico(),
                "Feminino", List.of(new ResponsavelRequest("Carla Souza", "11144477735", null, "31999990000",
                        "Mae", true))));
        long turma = dados.turma(escola, "9A", 30);
        dados.matricula(escola, aluno.id(), turma, StatusMatricula.CANCELADA, LocalDate.of(2026, 2, 2));
        return aluno;
    }

    @Test
    void exportarGeraOPdfComONomeDoAlunoEEntraNoLog() {
        AlunoResponse aluno = alunoQueSaiu();
        dados.cobrancaPaga(escola, aluno.id(), new BigDecimal("850.00"), LocalDate.of(2026, 3, 10),
                Instant.parse("2026-03-08T12:00:00Z"));

        DadosPessoaisAlunoService.Arquivo arquivo = dadosPessoaisAlunoService.exportar(aluno.id());

        assertThat(new String(arquivo.conteudo(), 0, 5)).isEqualTo("%PDF-");
        assertThat(arquivo.nome()).startsWith("dados-do-aluno-ana-souza-").endsWith(".pdf");
        assertThat(dados.operacoesNoLog(aluno.id())).contains(OperacaoAuditoria.EXPORTAR_DADOS);
    }

    @Test
    void anonimizarApagaODadoPessoalEGuardaOFinanceiro() {
        AlunoResponse aluno = alunoQueSaiu();
        UUID cobranca = dados.cobrancaPaga(escola, aluno.id(), new BigDecimal("850.00"), LocalDate.of(2026, 3, 10),
                Instant.parse("2026-03-08T12:00:00Z"));

        dadosPessoaisAlunoService.anonimizar(aluno.id());

        AlunoResponse depois = alunoService.buscarPorId(aluno.id());
        assertThat(depois.nome()).isEqualTo("Aluno anonimizado");
        assertThat(depois.cpf()).isNull();
        assertThat(depois.email()).isNull();
        assertThat(depois.dataNascimento()).isNull();
        assertThat(depois.genero()).isNull();
        assertThat(depois.responsaveis()).isEmpty();
        assertThat(depois.situacao()).isEqualTo(AlunoResponse.Situacao.ANONIMIZADO);
        assertThat(depois.matricula()).isEqualTo(aluno.matricula());

        // O financeiro continua inteiro.
        assertThat(dados.statusGravado(cobranca)).isEqualTo(StatusCobranca.PAGA);
        assertThat(dados.valor(cobranca)).isEqualByComparingTo("850.00");

        // O login nao e mais o e-mail e nao entra.
        Map<String, Object> login = jdbc.queryForMap(
                "SELECT u.LOGIN, u.ATIVO FROM USUARIO u JOIN alunos a ON a.usuario_id = u.ID_USUARIO "
                        + "WHERE a.id_aluno = ?", aluno.id());
        assertThat((String) login.get("login")).startsWith("anonimizado-");
        assertThat(login.get("ativo")).isEqualTo(false);

        // Na lista tambem aparece como anonimizado.
        assertThat(alunoService.listar(null)).filteredOn(a -> a.id().equals(aluno.id()))
                .extracting(AlunoResponse::situacao).containsExactly(AlunoResponse.Situacao.ANONIMIZADO);
        assertThat(dados.operacoesNoLog(aluno.id())).contains(OperacaoAuditoria.ANONIMIZAR);
    }

    @Test
    void alunoAnonimizadoNaoEEditadoNemExportadoDeNovo() {
        AlunoResponse aluno = alunoQueSaiu();
        dadosPessoaisAlunoService.anonimizar(aluno.id());

        assertThatThrownBy(() -> dadosPessoaisAlunoService.exportar(aluno.id()))
                .isInstanceOf(RegistroEmUsoException.class);
        assertThatThrownBy(() -> dadosPessoaisAlunoService.anonimizar(aluno.id()))
                .isInstanceOf(RegistroEmUsoException.class);
        assertThatThrownBy(() -> alunoService.atualizar(aluno.id(), new AlunoRequest("Ana", "52998224725",
                "ana@aluno.com", null, null, aluno.matricula(), null, null)))
                .isInstanceOf(RegistroEmUsoException.class);
    }

    @Test
    void naoAnonimizaQuemAindaEstudaOuDeve() {
        AlunoResponse aluno = alunoQueSaiu();
        long outra = dados.turma(escola, "9B", 30);
        dados.matricula(escola, aluno.id(), outra, StatusMatricula.TRANCADA, LocalDate.of(2026, 3, 1));
        assertThatThrownBy(() -> dadosPessoaisAlunoService.anonimizar(aluno.id()))
                .isInstanceOf(RegistroEmUsoException.class).hasMessageContaining("matricula");

        AlunoResponse devedor = alunoQueSaiu();
        dados.cobranca(escola, devedor.id(), StatusCobranca.EM_ATRASO, LocalDate.of(2026, 9, 10));
        assertThatThrownBy(() -> dadosPessoaisAlunoService.anonimizar(devedor.id()))
                .isInstanceOf(RegistroEmUsoException.class).hasMessageContaining("cobranca");

        assertThat(alunoService.buscarPorId(devedor.id()).nome()).isEqualTo("Ana Souza");
    }
}
