package br.pucminas.eduka.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.pucminas.eduka.backend.domain.CategoriaDespesa;
import br.pucminas.eduka.backend.domain.Despesa;
import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.LogAuditoria;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.SituacaoDespesa;
import br.pucminas.eduka.backend.exception.ApiExceptionHandler;
import br.pucminas.eduka.backend.repository.CategoriaDespesaRepository;
import br.pucminas.eduka.backend.repository.DespesaRepository;
import br.pucminas.eduka.backend.repository.LogAuditoriaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.JwtConfig;
import br.pucminas.eduka.backend.security.JwtService;
import br.pucminas.eduka.backend.service.AuditoriaService;
import br.pucminas.eduka.backend.service.CategoriaDespesaService;
import br.pucminas.eduka.backend.service.DespesaService;

/**
 * Contrato das despesas e das categorias, ponta a ponta: filter chain,
 * tokens, {@code @PreAuthorize}, validacao e servicos reais; so os
 * repositorios sao mock.
 */
@WebMvcTest({ DespesaController.class, CategoriaDespesaController.class })
@Import({ JwtConfig.class, JwtService.class, IsolamentoEscola.class, ApiExceptionHandler.class,
        DespesaService.class, CategoriaDespesaService.class, AuditoriaService.class })
class DespesaControllerSecurityTest {

    private static final UUID ESCOLA_A = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DespesaRepository despesaRepository;
    @MockitoBean
    private CategoriaDespesaRepository categoriaRepository;
    @MockitoBean
    private LogAuditoriaRepository logAuditoriaRepository;

    private CategoriaDespesa contas;
    private CategoriaDespesa materiais;

    @BeforeEach
    void dados() {
        contas = CategoriaDespesa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Contas").build();
        materiais = CategoriaDespesa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).nome("Materiais").build();
        when(categoriaRepository.findByIdAndEscolaId(contas.getId(), ESCOLA_A)).thenReturn(Optional.of(contas));
        when(categoriaRepository.findByIdAndEscolaId(materiais.getId(), ESCOLA_A))
                .thenReturn(Optional.of(materiais));
        when(despesaRepository.saveAndFlush(any(Despesa.class))).thenAnswer(inv -> {
            Despesa despesa = inv.getArgument(0);
            if (despesa.getId() == null) {
                despesa.setId(UUID.randomUUID());
            }
            return despesa;
        });
        when(categoriaRepository.saveAndFlush(any(CategoriaDespesa.class))).thenAnswer(inv -> {
            CategoriaDespesa categoria = inv.getArgument(0);
            categoria.setId(UUID.randomUUID());
            return categoria;
        });
    }

    private String accessToken(String perfil, UUID escolaId) {
        return jwtService.generateAccessToken(UUID.randomUUID(), perfil, escolaId);
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token,
            String corpo) {
        return com(request, token).contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static final String DADOS = "\"descricao\":\"Conta de energia\",\"valor\":4320.00,"
            + "\"dataVencimento\":\"2026-10-15\"";

    private String lancar(CategoriaDespesa categoria, String extras) {
        return "{\"categoriaId\":\"" + categoria.getId() + "\"," + DADOS + extras + "}";
    }

    private static String situacao(SituacaoDespesa situacao) {
        return "{\"situacao\":\"" + situacao + "\"}";
    }

    private Despesa despesa(SituacaoDespesa situacao, CategoriaDespesa categoria, LocalDate vencimento) {
        Despesa despesa = Despesa.builder().id(UUID.randomUUID()).escolaId(ESCOLA_A).categoria(categoria)
                .descricao("Conta de energia").valor(new BigDecimal("4320.00")).dataVencimento(vencimento)
                .situacao(situacao).build();
        when(despesaRepository.findByIdAndEscolaId(despesa.getId(), ESCOLA_A)).thenReturn(Optional.of(despesa));
        return despesa;
    }

    private Despesa despesa(SituacaoDespesa situacao) {
        return despesa(situacao, contas, LocalDate.of(2026, 10, 15));
    }

    /** Unico log gravado ate aqui. */
    private LogAuditoria logGravado() {
        ArgumentCaptor<LogAuditoria> log = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository).save(log.capture());
        return log.getValue();
    }

    // ---------- Seguranca ----------

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/despesas"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/categorias-despesa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soGestaoEFinanceiroEntramNoModulo() throws Exception {
        for (String perfil : List.of("SECRETARIA", "PROFESSOR", "ALUNO")) {
            String token = accessToken(perfil, ESCOLA_A);
            mockMvc.perform(com(get("/despesas"), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(post("/despesas"), token, lancar(contas, "")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(patch("/despesas/" + UUID.randomUUID()), token, situacao(SituacaoDespesa.PAGA)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(com(get("/categorias-despesa"), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(post("/categorias-despesa"), token, "{\"nome\":\"Eventos\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(json(post("/despesas"), accessToken("SUPER_ADMIN", null), lancar(contas, "")))
                .andExpect(status().isForbidden());
        verify(despesaRepository, never()).saveAndFlush(any());

        mockMvc.perform(com(get("/despesas"), accessToken("MASTER", ESCOLA_A)))
                .andExpect(status().isOk());
    }

    // ---------- Pagina ----------

    @Test
    void paginaRespeitaOsPerfisEPassaPaginaEOrdemAoBanco() throws Exception {
        when(despesaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(despesa(SituacaoDespesa.A_PAGAR)), inv.getArgument(1), 41));

        mockMvc.perform(com(get("/despesas/pagina"), accessToken("SECRETARIA", ESCOLA_A)))
                .andExpect(status().isForbidden());

        mockMvc.perform(com(get("/despesas/pagina").param("pagina", "2").param("tamanho", "20")
                .param("ordenarPor", "VALOR").param("direcao", "DESC"), accessToken("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].categoriaNome").value("Contas"))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.totalElementos").value(41))
                .andExpect(jsonPath("$.totalPaginas").value(3));

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(despesaRepository).findAll(any(Specification.class), pedido.capture());
        assertThat(pedido.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pedido.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "valor").and(Sort.by("id")));
    }

    @Test
    void paginaComParametroInvalidoResponde400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        mockMvc.perform(com(get("/despesas/pagina").param("tamanho", "500"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(get("/despesas/pagina").param("ordenarPor", "ESCOLA_ID"), token))
                .andExpect(status().isBadRequest());
        verify(despesaRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // ---------- Criar ----------

    @Test
    void financeiroLancaDespesaAPagarNaEscolaDoToken() throws Exception {
        mockMvc.perform(json(post("/despesas"), accessToken("FINANCEIRO", ESCOLA_A), lancar(contas, "")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.categoriaId").value(contas.getId().toString()))
                .andExpect(jsonPath("$.categoriaNome").value("Contas"))
                .andExpect(jsonPath("$.descricao").value("Conta de energia"))
                .andExpect(jsonPath("$.valor").value(4320.00))
                .andExpect(jsonPath("$.dataVencimento").value("2026-10-15"))
                .andExpect(jsonPath("$.situacao").value("A_PAGAR"))
                .andExpect(jsonPath("$.pagaEm").doesNotExist());

        ArgumentCaptor<Despesa> salva = ArgumentCaptor.forClass(Despesa.class);
        verify(despesaRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
    }

    @Test
    void lancaDespesaJaPaga() throws Exception {
        mockMvc.perform(json(post("/despesas"), accessToken("MASTER", ESCOLA_A), lancar(contas, ",\"paga\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("PAGA"))
                .andExpect(jsonPath("$.pagaEm").isNotEmpty());
    }

    @Test
    void dadosInvalidosRespondem400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        String categoria = "\"categoriaId\":\"" + contas.getId() + "\"";
        for (String corpo : List.of(
                "{" + DADOS + "}",
                "{" + categoria + ",\"descricao\":\" \",\"valor\":10,\"dataVencimento\":\"2026-10-15\"}",
                "{" + categoria + ",\"descricao\":\"Luz\",\"valor\":0,\"dataVencimento\":\"2026-10-15\"}",
                "{" + categoria + ",\"descricao\":\"Luz\",\"valor\":10.123,\"dataVencimento\":\"2026-10-15\"}",
                "{" + categoria + ",\"descricao\":\"Luz\",\"valor\":10}")) {
            mockMvc.perform(json(post("/despesas"), token, corpo))
                    .andExpect(status().isBadRequest());
        }
        verify(despesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void categoriaDeOutraEscolaResponde404() throws Exception {
        CategoriaDespesa deOutraEscola = CategoriaDespesa.builder().id(UUID.randomUUID()).build();

        mockMvc.perform(json(post("/despesas"), accessToken("FINANCEIRO", ESCOLA_A), lancar(deOutraEscola, "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Categoria de despesa nao encontrada"));
        verify(despesaRepository, never()).saveAndFlush(any());
    }

    // ---------- Editar ----------

    @Test
    void editaDespesaAPagarOuPagaSemMudarASituacao() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        for (SituacaoDespesa situacao : List.of(SituacaoDespesa.A_PAGAR, SituacaoDespesa.PAGA)) {
            Despesa despesa = despesa(situacao);

            mockMvc.perform(json(put("/despesas/" + despesa.getId()), token,
                    "{\"categoriaId\":\"" + materiais.getId() + "\",\"descricao\":\"  Livros da biblioteca \","
                            + "\"valor\":3400,\"dataVencimento\":\"2026-10-30\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoriaNome").value("Materiais"))
                    .andExpect(jsonPath("$.descricao").value("Livros da biblioteca"))
                    .andExpect(jsonPath("$.valor").value(3400))
                    .andExpect(jsonPath("$.dataVencimento").value("2026-10-30"))
                    .andExpect(jsonPath("$.situacao").value(situacao.name()))
                    .andExpect(jsonPath("$.atualizadoEm").isNotEmpty());
        }
    }

    @Test
    void despesaCanceladaNaoEEditada() throws Exception {
        Despesa despesa = despesa(SituacaoDespesa.CANCELADA);

        mockMvc.perform(json(put("/despesas/" + despesa.getId()), accessToken("FINANCEIRO", ESCOLA_A),
                lancar(contas, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Despesa CANCELADA nao pode ser editada"));
        verify(despesaRepository, never()).saveAndFlush(any());
    }

    // ---------- Situacao ----------

    @Test
    void marcarComoPagaGuardaQuandoFoiPaga() throws Exception {
        Despesa despesa = despesa(SituacaoDespesa.A_PAGAR);

        mockMvc.perform(json(patch("/despesas/" + despesa.getId()), accessToken("FINANCEIRO", ESCOLA_A),
                situacao(SituacaoDespesa.PAGA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PAGA"))
                .andExpect(jsonPath("$.pagaEm").isNotEmpty());
        assertThat(despesa.getSituacao()).isEqualTo(SituacaoDespesa.PAGA);
    }

    @Test
    void voltarParaAPagarDesfazOPagamento() throws Exception {
        Despesa despesa = despesa(SituacaoDespesa.PAGA);
        despesa.setPagaEm(Instant.parse("2026-10-10T12:00:00Z"));

        mockMvc.perform(json(patch("/despesas/" + despesa.getId()), accessToken("FINANCEIRO", ESCOLA_A),
                situacao(SituacaoDespesa.A_PAGAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("A_PAGAR"))
                .andExpect(jsonPath("$.pagaEm").doesNotExist());
    }

    @Test
    void cancelarMantemADespesaNoHistorico() throws Exception {
        Despesa despesa = despesa(SituacaoDespesa.A_PAGAR);

        mockMvc.perform(json(patch("/despesas/" + despesa.getId()), accessToken("MASTER", ESCOLA_A),
                situacao(SituacaoDespesa.CANCELADA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CANCELADA"))
                .andExpect(jsonPath("$.canceladaEm").isNotEmpty());
        verify(despesaRepository, never()).delete(any(Despesa.class));
    }

    @Test
    void mudancaDeSituacaoNaoPermitidaResponde400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        Despesa paga = despesa(SituacaoDespesa.PAGA);
        Despesa cancelada = despesa(SituacaoDespesa.CANCELADA);

        mockMvc.perform(json(patch("/despesas/" + paga.getId()), token, situacao(SituacaoDespesa.PAGA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Despesa ja esta PAGA"));
        mockMvc.perform(json(patch("/despesas/" + paga.getId()), token, situacao(SituacaoDespesa.CANCELADA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Despesa PAGA nao pode passar para CANCELADA"));
        mockMvc.perform(json(patch("/despesas/" + cancelada.getId()), token, situacao(SituacaoDespesa.A_PAGAR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Despesa CANCELADA nao pode passar para A_PAGAR"));
        mockMvc.perform(json(patch("/despesas/" + paga.getId()), token, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/despesas/" + paga.getId()), token, "{\"situacao\":\"QUITADA\"}"))
                .andExpect(status().isBadRequest());
        verify(despesaRepository, never()).saveAndFlush(any());
    }

    @Test
    void despesaDeOutraEscolaResponde404() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        String id = UUID.randomUUID().toString();
        mockMvc.perform(com(get("/despesas/" + id), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Despesa nao encontrada"));
        mockMvc.perform(json(put("/despesas/" + id), token, lancar(contas, "")))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(patch("/despesas/" + id), token, situacao(SituacaoDespesa.PAGA)))
                .andExpect(status().isNotFound());
    }

    // ---------- Auditoria ----------

    @Test
    void criarRegistraNoLogQuemLancouADespesa() throws Exception {
        UUID usuario = UUID.randomUUID();

        mockMvc.perform(json(post("/despesas"), jwtService.generateAccessToken(usuario, "FINANCEIRO", ESCOLA_A),
                lancar(contas, "")))
                .andExpect(status().isCreated());

        ArgumentCaptor<Despesa> salva = ArgumentCaptor.forClass(Despesa.class);
        verify(despesaRepository).saveAndFlush(salva.capture());
        LogAuditoria log = logGravado();
        assertThat(log.getUsuarioId()).isEqualTo(usuario);
        assertThat(log.getEscolaId()).isEqualTo(ESCOLA_A);
        assertThat(log.getOperacao()).isEqualTo(OperacaoAuditoria.CRIAR);
        assertThat(log.getEntidade()).isEqualTo(EntidadeAuditada.DESPESA);
        assertThat(log.getRegistroId()).isEqualTo(salva.getValue().getId());
    }

    @Test
    void editarRegistraNoLog() throws Exception {
        Despesa despesa = despesa(SituacaoDespesa.A_PAGAR);

        mockMvc.perform(json(put("/despesas/" + despesa.getId()), accessToken("FINANCEIRO", ESCOLA_A),
                lancar(contas, "")))
                .andExpect(status().isOk());

        LogAuditoria log = logGravado();
        assertThat(log.getOperacao()).isEqualTo(OperacaoAuditoria.EDITAR);
        assertThat(log.getRegistroId()).isEqualTo(despesa.getId());
    }

    @Test
    void cadaMudancaDeSituacaoTemSuaOperacaoNoLog() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        record Caso(SituacaoDespesa de, SituacaoDespesa para, OperacaoAuditoria operacao) {
        }
        List<Caso> casos = List.of(
                new Caso(SituacaoDespesa.A_PAGAR, SituacaoDespesa.PAGA, OperacaoAuditoria.PAGAR),
                new Caso(SituacaoDespesa.PAGA, SituacaoDespesa.A_PAGAR, OperacaoAuditoria.ESTORNAR),
                new Caso(SituacaoDespesa.A_PAGAR, SituacaoDespesa.CANCELADA, OperacaoAuditoria.CANCELAR));
        for (Caso caso : casos) {
            Despesa despesa = despesa(caso.de());
            mockMvc.perform(json(patch("/despesas/" + despesa.getId()), token, situacao(caso.para())))
                    .andExpect(status().isOk());
        }

        ArgumentCaptor<LogAuditoria> logs = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository, times(3)).save(logs.capture());
        assertThat(logs.getAllValues()).extracting(LogAuditoria::getOperacao)
                .containsExactly(OperacaoAuditoria.PAGAR, OperacaoAuditoria.ESTORNAR, OperacaoAuditoria.CANCELAR);
        assertThat(logs.getAllValues()).extracting(LogAuditoria::getEntidade)
                .containsOnly(EntidadeAuditada.DESPESA);
    }

    @Test
    void operacaoRecusadaOuSoDeLeituraNaoGeraLog() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        Despesa cancelada = despesa(SituacaoDespesa.CANCELADA);

        mockMvc.perform(json(put("/despesas/" + cancelada.getId()), token, lancar(contas, "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/despesas/" + cancelada.getId()), token, situacao(SituacaoDespesa.PAGA)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/despesas"), token, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(get("/despesas/" + cancelada.getId()), token))
                .andExpect(status().isOk());

        verify(logAuditoriaRepository, never()).save(any());
    }

    // ---------- Consulta ----------

    // Os filtros sao feitos no banco: quem confere cada um e o DespesaBancoRealTest.
    @Test
    void listaPedeAoBancoPeloVencimento() throws Exception {
        Despesa aguaDeOutubro = despesa(SituacaoDespesa.A_PAGAR, contas, LocalDate.of(2026, 10, 5));
        Despesa livrosDeOutubro = despesa(SituacaoDespesa.A_PAGAR, materiais, LocalDate.of(2026, 10, 20));
        when(despesaRepository.findAll(anySpecification(), any(Sort.class)))
                .thenReturn(List.of(aguaDeOutubro, livrosDeOutubro));

        mockMvc.perform(com(get("/despesas").param("situacao", "A_PAGAR")
                .param("categoriaId", contas.getId().toString())
                .param("vencimentoDe", "2026-10-01").param("vencimentoAte", "2026-10-31"),
                accessToken("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(aguaDeOutubro.getId().toString()))
                .andExpect(jsonPath("$[0].categoriaNome").value("Contas"))
                .andExpect(jsonPath("$[1].id").value(livrosDeOutubro.getId().toString()));

        ArgumentCaptor<Sort> ordem = ArgumentCaptor.forClass(Sort.class);
        verify(despesaRepository).findAll(anySpecification(), ordem.capture());
        assertThat(ordem.getValue()).isEqualTo(Sort.by("dataVencimento", "id"));
    }

    @SuppressWarnings("unchecked")
    private static Specification<Despesa> anySpecification() {
        return any(Specification.class);
    }

    @Test
    void filtroInvalidoResponde400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        mockMvc.perform(com(get("/despesas").param("vencimentoDe", "2026-10-31").param("vencimentoAte", "2026-10-01"),
                token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(com(get("/despesas").param("situacao", "QUITADA"), token))
                .andExpect(status().isBadRequest());
    }

    // ---------- Categorias ----------

    @Test
    @SuppressWarnings("unchecked")
    void escolaSemCategoriaRecebeAsPadraoNaPrimeiraConsulta() throws Exception {
        List<CategoriaDespesa> padrao = List.of("Consertos", "Contas", "Limpeza", "Materiais", "Outros", "Salários")
                .stream().map(nome -> CategoriaDespesa.builder().id(UUID.randomUUID()).nome(nome).build()).toList();
        when(categoriaRepository.findAllByEscolaIdOrderByNome(ESCOLA_A)).thenReturn(List.of(), padrao);

        mockMvc.perform(com(get("/categorias-despesa"), accessToken("FINANCEIRO", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].nome").value("Consertos"));

        ArgumentCaptor<List<CategoriaDespesa>> criadas = ArgumentCaptor.forClass(List.class);
        verify(categoriaRepository).saveAll(criadas.capture());
        assertThat(criadas.getValue()).extracting(CategoriaDespesa::getNome)
                .containsExactly("Salários", "Contas", "Materiais", "Consertos", "Limpeza", "Outros");
        assertThat(criadas.getValue()).extracting(CategoriaDespesa::getEscolaId).containsOnly(ESCOLA_A);
    }

    @Test
    void escolaComCategoriaNaoGanhaAsPadraoDeNovo() throws Exception {
        when(categoriaRepository.findAllByEscolaIdOrderByNome(ESCOLA_A)).thenReturn(List.of(contas));

        mockMvc.perform(com(get("/categorias-despesa"), accessToken("MASTER", ESCOLA_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(contas.getId().toString()))
                .andExpect(jsonPath("$[0].nome").value("Contas"));
        verify(categoriaRepository, never()).saveAll(anyList());
    }

    @Test
    void cadastraCategoriaNaEscolaDoToken() throws Exception {
        mockMvc.perform(json(post("/categorias-despesa"), accessToken("FINANCEIRO", ESCOLA_A),
                "{\"nome\":\"  Eventos \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nome").value("Eventos"));

        ArgumentCaptor<CategoriaDespesa> salva = ArgumentCaptor.forClass(CategoriaDespesa.class);
        verify(categoriaRepository).saveAndFlush(salva.capture());
        assertThat(salva.getValue().getEscolaId()).isEqualTo(ESCOLA_A);
    }

    @Test
    void categoriaRepetidaResponde409EVaziaResponde400() throws Exception {
        String token = accessToken("FINANCEIRO", ESCOLA_A);
        when(categoriaRepository.existsByEscolaIdAndNomeIgnoreCase(eq(ESCOLA_A), eq("contas"))).thenReturn(true);

        mockMvc.perform(json(post("/categorias-despesa"), token, "{\"nome\":\"contas\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(json(post("/categorias-despesa"), token, "{\"nome\":\" \"}"))
                .andExpect(status().isBadRequest());
        verify(categoriaRepository, never()).saveAndFlush(any());
    }
}
