package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import br.pucminas.eduka.backend.domain.academico.StatusMatricula;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.financeiro.StatusCobranca;
import br.pucminas.eduka.backend.dto.pessoas.AlunoRequest;
import br.pucminas.eduka.backend.dto.pessoas.AlunoResponse;
import br.pucminas.eduka.backend.service.pessoas.AlunoService;
import br.pucminas.eduka.backend.service.privacidade.RetencaoDadosAlunoService;

/**
 * Politica de retencao no Postgres: a rotina roda sem usuario, entra em cada
 * escola pelo contexto do sistema e anonimiza so o aluno inativo que ja
 * cumpriu o prazo e nao tem mais matricula vigente nem cobranca em aberto.
 */
class RetencaoDadosAlunoBancoRealTest extends BancoRealTest {

    @Autowired
    private AlunoService alunoService;
    @Autowired
    private RetencaoDadosAlunoService retencaoService;

    private UUID escola;
    private UUID master;

    private AlunoResponse alunoInativo(String nome) {
        AlunoResponse aluno = alunoService.cadastrar(new AlunoRequest(nome, "52998224725",
                DadosDeTeste.unico() + "@aluno.com", LocalDate.of(2010, 3, 14), null, "M-" + DadosDeTeste.unico(),
                "Feminino", List.of()));
        long turma = dados.turma(escola, "9" + DadosDeTeste.unico(), 30);
        dados.matricula(escola, aluno.id(), turma, StatusMatricula.CANCELADA, LocalDate.of(2020, 2, 2));
        alunoService.inativar(aluno.id());
        return aluno;
    }

    /** Leva a inativacao para {@code anos} anos atras, como se o tempo tivesse passado. */
    private void inativadoHa(UUID alunoId, int anos) {
        jdbc.update("UPDATE alunos SET inativado_em = NOW() - make_interval(years => ?) WHERE id_aluno = ?", anos,
                alunoId);
    }

    private void comoMaster() {
        autenticarComo(master, "MASTER", escola);
    }

    @Test
    void anonimizaSoQuemPassouDoPrazoESemPendencia() {
        escola = dados.escola();
        master = dados.usuario(escola, "MASTER", "Marta Gestora");
        comoMaster();
        AlunoResponse venceu = alunoInativo("Ana Souza");
        AlunoResponse noPrazo = alunoInativo("Bia Lima");
        AlunoResponse devendo = alunoInativo("Caio Dias");
        dados.cobranca(escola, devendo.id(), StatusCobranca.EM_ATRASO, LocalDate.of(2020, 9, 10));
        inativadoHa(venceu.id(), 6);
        inativadoHa(noPrazo.id(), 4);
        inativadoHa(devendo.id(), 6);

        // A rotina roda sem ninguem logado.
        SecurityContextHolder.clearContext();
        RetencaoDadosAlunoService.Resultado resultado = retencaoService.anonimizarVencidos();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        assertThat(resultado.anonimizados()).isGreaterThanOrEqualTo(1);
        assertThat(resultado.adiados()).isGreaterThanOrEqualTo(1);

        comoMaster();
        AlunoResponse ana = alunoService.buscarPorId(venceu.id());
        assertThat(ana.situacao()).isEqualTo(AlunoResponse.Situacao.ANONIMIZADO);
        assertThat(ana.nome()).isEqualTo("Aluno anonimizado");
        assertThat(ana.cpf()).isNull();
        assertThat(dados.operacoesNoLog(venceu.id())).contains(OperacaoAuditoria.ANONIMIZAR);
        String origem = jdbc.queryForObject("SELECT ORIGEM FROM LOG_AUDITORIA WHERE REGISTRO_ID = ? "
                + "AND OPERACAO = 'ANONIMIZAR'", String.class, venceu.id());
        assertThat(origem).isEqualTo("SISTEMA");

        AlunoResponse bia = alunoService.buscarPorId(noPrazo.id());
        assertThat(bia.nome()).isEqualTo("Bia Lima");
        assertThat(bia.situacao()).isEqualTo(AlunoResponse.Situacao.INATIVO);
        assertThat(bia.anonimizacaoPrevistaEm()).isNotNull();

        // Ainda deve: fica para quando a cobranca for resolvida.
        assertThat(alunoService.buscarPorId(devendo.id()).nome()).isEqualTo("Caio Dias");
    }

    @Test
    void reativarZeraOPrazo() {
        escola = dados.escola();
        master = dados.usuario(escola, "MASTER", "Marta Gestora");
        comoMaster();
        AlunoResponse aluno = alunoInativo("Dora Reis");
        inativadoHa(aluno.id(), 6);

        AlunoResponse reativado = alunoService.ativar(aluno.id());
        assertThat(reativado.anonimizacaoPrevistaEm()).isNull();
        SecurityContextHolder.clearContext();
        retencaoService.anonimizarVencidos();

        comoMaster();
        assertThat(alunoService.buscarPorId(aluno.id()).nome()).isEqualTo("Dora Reis");
        Object inativadoEm = jdbc.queryForObject("SELECT inativado_em FROM alunos WHERE id_aluno = ?", Object.class,
                aluno.id());
        assertThat(inativadoEm).isNull();
    }
}
