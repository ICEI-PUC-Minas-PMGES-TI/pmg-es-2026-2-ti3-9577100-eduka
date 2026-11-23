package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.dto.AtualizarDespesaRequest;
import br.pucminas.eduka.backend.dto.CategoriaDespesaRequest;
import br.pucminas.eduka.backend.dto.CategoriaDespesaResponse;
import br.pucminas.eduka.backend.dto.DespesaRequest;
import br.pucminas.eduka.backend.dto.DespesaResponse;
import br.pucminas.eduka.backend.dto.MudarSituacaoDespesaRequest;
import br.pucminas.eduka.backend.exception.CategoriaDespesaJaExisteException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.service.CategoriaDespesaService;
import br.pucminas.eduka.backend.service.DespesaService;

/**
 * Despesas e categorias no banco: o ciclo A_PAGAR, PAGA e CANCELADA com a
 * auditoria de cada passo, as categorias de cada escola e o isolamento entre
 * escolas.
 */
class DespesaBancoRealTest extends BancoRealTest {

    @Autowired
    private DespesaService despesaService;
    @Autowired
    private CategoriaDespesaService categoriaService;

    private UUID escola;
    private UUID categoria;

    @BeforeEach
    void financeiroDaEscola() {
        escola = dados.escola();
        categoria = dados.categoriaDespesa(escola);
        autenticarComo(dados.usuario(escola, "FINANCEIRO", "Fernanda Rocha"), "FINANCEIRO", escola);
    }

    private DespesaResponse lancar(boolean paga) {
        return despesaService.criar(new DespesaRequest("Conta de luz", "Administração", categoria,
                new BigDecimal("90.00"), LocalDate.now().plusDays(5), paga));
    }

    private DespesaResponse mudarPara(UUID despesa, SituacaoDespesa situacao) {
        return despesaService.mudarSituacao(despesa, new MudarSituacaoDespesaRequest(situacao));
    }

    private AtualizarDespesaRequest edicao() {
        return new AtualizarDespesaRequest("Conta de agua", "Zelador Antônio", categoria, new BigDecimal("75.00"),
                LocalDate.now().plusDays(8));
    }

    // ---------- Responsavel ----------

    @Test
    void responsavelEGravadoNoLancamentoETrocadoNaEdicao() {
        UUID despesa = lancar(false).id();
        assertThat(despesaService.buscar(despesa).responsavel()).isEqualTo("Administração");

        despesaService.atualizar(despesa, edicao());

        assertThat(despesaService.buscar(despesa).responsavel()).isEqualTo("Zelador Antônio");
    }

    @Test
    void despesaAntigaSemResponsavelContinuaListadaEGanhaUmNaEdicao() {
        // Lancada antes da V47: a coluna fica nula.
        UUID antiga = dados.despesa(escola, categoria, new BigDecimal("50.00"), LocalDate.now().plusDays(3),
                SituacaoDespesa.A_PAGAR, null);

        assertThat(despesaService.buscar(antiga).responsavel()).isNull();

        despesaService.atualizar(antiga, edicao());
        assertThat(despesaService.buscar(antiga).responsavel()).isEqualTo("Zelador Antônio");
    }

    // ---------- Listagem ----------

    @Test
    void listaFiltraNoBancoPorSituacaoCategoriaEVencimentoPeloVencimento() {
        UUID outraCategoria = dados.categoriaDespesa(escola);
        BigDecimal valor = new BigDecimal("50.00");
        UUID luzDeOutubro = dados.despesa(escola, categoria, valor, LocalDate.of(2026, 10, 15),
                SituacaoDespesa.A_PAGAR, null);
        UUID aguaDeOutubro = dados.despesa(escola, categoria, valor, LocalDate.of(2026, 10, 5),
                SituacaoDespesa.A_PAGAR, null);
        dados.despesa(escola, outraCategoria, valor, LocalDate.of(2026, 10, 20), SituacaoDespesa.A_PAGAR, null);
        UUID paga = dados.despesa(escola, categoria, valor, LocalDate.of(2026, 10, 31), SituacaoDespesa.PAGA,
                Instant.now());
        dados.despesa(escola, categoria, valor, LocalDate.of(2026, 11, 1), SituacaoDespesa.A_PAGAR, null);
        UUID outraEscola = dados.escola();
        dados.despesa(outraEscola, dados.categoriaDespesa(outraEscola), valor, LocalDate.of(2026, 10, 10),
                SituacaoDespesa.A_PAGAR, null);
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        LocalDate fim = LocalDate.of(2026, 10, 31);

        assertThat(despesaService.listar(SituacaoDespesa.A_PAGAR, categoria, inicio, fim))
                .extracting(DespesaResponse::id).containsExactly(aguaDeOutubro, luzDeOutubro);
        // So o mes, como a tela pede: as quatro de outubro da escola, em qualquer situacao e categoria.
        assertThat(despesaService.listar(null, null, inicio, fim)).hasSize(4)
                .extracting(DespesaResponse::id).contains(paga);
        assertThat(despesaService.listar(null, null, null, null)).hasSize(5);
    }

    // ---------- Mudancas de situacao ----------

    @Test
    void pagarEstornarECancelarFicamNoLog() {
        UUID despesa = lancar(false).id();

        DespesaResponse paga = mudarPara(despesa, SituacaoDespesa.PAGA);
        assertThat(paga.situacao()).isEqualTo(SituacaoDespesa.PAGA);
        assertThat(paga.pagaEm()).isNotNull();

        DespesaResponse estornada = mudarPara(despesa, SituacaoDespesa.A_PAGAR);
        assertThat(estornada.situacao()).isEqualTo(SituacaoDespesa.A_PAGAR);
        assertThat(estornada.pagaEm()).isNull();

        DespesaResponse cancelada = mudarPara(despesa, SituacaoDespesa.CANCELADA);
        assertThat(cancelada.canceladaEm()).isNotNull();

        assertThat(dados.operacoesNoLog(despesa)).containsExactly(OperacaoAuditoria.CRIAR, OperacaoAuditoria.PAGAR,
                OperacaoAuditoria.ESTORNAR, OperacaoAuditoria.CANCELAR);
    }

    @Test
    void canceladaNaoMudaMaisNemEEditada() {
        UUID despesa = lancar(false).id();
        mudarPara(despesa, SituacaoDespesa.CANCELADA);

        assertThatThrownBy(() -> mudarPara(despesa, SituacaoDespesa.PAGA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mudarPara(despesa, SituacaoDespesa.A_PAGAR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mudarPara(despesa, SituacaoDespesa.CANCELADA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> despesaService.atualizar(despesa, edicao()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(despesaService.buscar(despesa).situacao()).isEqualTo(SituacaoDespesa.CANCELADA);
        assertThat(dados.operacoesNoLog(despesa)).containsExactly(OperacaoAuditoria.CRIAR, OperacaoAuditoria.CANCELAR);
    }

    @Test
    void pagaSoVoltaParaAPagar() {
        UUID despesa = lancar(true).id();

        assertThat(despesaService.buscar(despesa).situacao()).isEqualTo(SituacaoDespesa.PAGA);
        assertThatThrownBy(() -> mudarPara(despesa, SituacaoDespesa.CANCELADA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mudarPara(despesa, SituacaoDespesa.PAGA)).isInstanceOf(IllegalArgumentException.class);

        // Editar nao mexe na situacao.
        DespesaResponse editada = despesaService.atualizar(despesa, edicao());
        assertThat(editada.situacao()).isEqualTo(SituacaoDespesa.PAGA);
        assertThat(editada.valor()).isEqualByComparingTo("75.00");
        assertThat(dados.operacoesNoLog(despesa)).containsExactly(OperacaoAuditoria.CRIAR, OperacaoAuditoria.EDITAR);
    }

    // ---------- Categorias ----------

    @Test
    void cadaEscolaGanhaAsCategoriasPadraoUmaVezSo() {
        autenticarComo("FINANCEIRO", dados.escola());

        assertThat(categoriaService.listar()).extracting(CategoriaDespesaResponse::nome)
                .containsExactlyInAnyOrder("Salários", "Contas", "Materiais", "Consertos", "Limpeza", "Outros");
        assertThat(categoriaService.listar()).hasSize(6);
    }

    @Test
    void nomeDeCategoriaNaoSeRepeteNaEscolaMasPodeEmOutra() {
        categoriaService.criar(new CategoriaDespesaRequest("Transporte"));

        assertThatThrownBy(() -> categoriaService.criar(new CategoriaDespesaRequest(" transporte ")))
                .isInstanceOf(CategoriaDespesaJaExisteException.class);

        autenticarComo("FINANCEIRO", dados.escola());
        assertThat(categoriaService.criar(new CategoriaDespesaRequest("Transporte")).nome()).isEqualTo("Transporte");
    }

    @Test
    void bancoBarraCategoriaRepetidaMesmoComOutraCaixa() {
        // Dois cadastros ao mesmo tempo passam pela checagem do service; a constraint segura o segundo.
        String sql = "INSERT INTO CATEGORIA_DESPESA (ESCOLA_ID, NOME) VALUES (?, ?)";
        jdbc.update(sql, escola, "Uniformes");

        assertThatThrownBy(() -> jdbc.update(sql, escola, "UNIFORMES"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Isolamento entre escolas ----------

    @Test
    void financeiroDeOutraEscolaNaoEnxergaNemMexeNaDespesa() {
        UUID despesa = lancar(false).id();
        UUID outraEscola = dados.escola();
        autenticarComo(dados.usuario(outraEscola, "FINANCEIRO", "Outro Financeiro"), "FINANCEIRO", outraEscola);

        assertThat(despesaService.listar(null, null, null, null)).extracting(DespesaResponse::id)
                .doesNotContain(despesa);
        assertThatThrownBy(() -> despesaService.buscar(despesa)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> mudarPara(despesa, SituacaoDespesa.PAGA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> despesaService.atualizar(despesa, edicao()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        // Nem lancando na categoria da outra escola.
        assertThatThrownBy(() -> lancar(false)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(despesaService.listar(null, null, null, null)).isEmpty();

        autenticarComo("FINANCEIRO", escola);
        DespesaResponse intacta = despesaService.buscar(despesa);
        assertThat(intacta.situacao()).isEqualTo(SituacaoDespesa.A_PAGAR);
        assertThat(intacta.valor()).isEqualByComparingTo("90.00");
        assertThat(dados.operacoesNoLog(despesa)).containsExactly(OperacaoAuditoria.CRIAR);
    }
}
