package br.pucminas.eduka.backend.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.dto.acesso.UsuarioPlataformaResponse;
import br.pucminas.eduka.backend.dto.comum.Paginacao;
import br.pucminas.eduka.backend.dto.escola.MembroEquipeRequest;
import br.pucminas.eduka.backend.dto.escola.MembroEquipeResponse;
import br.pucminas.eduka.backend.exception.escola.LimiteUsuariosAtingidoException;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.service.acesso.UsuarioPlataformaService;
import br.pucminas.eduka.backend.service.escola.EquipeService;

/**
 * Limite de usuarios (S5-11) e a listagem de logins do SUPER_ADMIN (S5-14)
 * no Postgres: a trava da linha da escola, a contagem dos ativos e o filtro
 * da busca.
 */
class LimiteEUsuariosPlataformaBancoRealTest extends BancoRealTest {

    @Autowired
    private EquipeService equipeService;

    @Autowired
    private UsuarioPlataformaService usuarioPlataformaService;

    @Autowired
    private EscolaRepository escolaRepository;

    @Autowired
    private TransactionTemplate transacao;

    private void limite(UUID escola, Integer limite) {
        jdbc.update("UPDATE ESCOLA SET LIMITE_USUARIOS = ? WHERE ID_ESCOLA = ?", limite, escola);
    }

    private static MembroEquipeRequest secretaria(String cpf) {
        return new MembroEquipeRequest(PerfilUsuario.SECRETARIA, "Sara " + cpf, cpf, DadosDeTeste.unico() + "@sec.com",
                null);
    }

    @Test
    void cadastroEReativacaoParamNoLimiteEInativarLiberaAVaga() {
        UUID escola = dados.escola();
        limite(escola, 2);
        entrarComo("MASTER", escola);

        MembroEquipeResponse sara = equipeService.cadastrar(secretaria("12345678909"));
        assertThatThrownBy(() -> equipeService.cadastrar(secretaria("11144477735")))
                .isInstanceOf(LimiteUsuariosAtingidoException.class);
        // O cadastro barrado nao deixou pessoa nem login para tras.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM USUARIO WHERE ESCOLA_ID = ?", Long.class, escola))
                .isEqualTo(2);

        equipeService.inativar(sara.id());
        MembroEquipeResponse outra = equipeService.cadastrar(secretaria("11144477735"));
        assertThatThrownBy(() -> equipeService.ativar(sara.id())).isInstanceOf(LimiteUsuariosAtingidoException.class);

        limite(escola, null);
        assertThat(equipeService.ativar(sara.id()).ativo()).isTrue();
        assertThat(outra.ativo()).isTrue();
    }

    private Optional<Integer> travar(UUID escola) {
        return transacao.execute(t -> escolaRepository.travarLimiteUsuarios(escola));
    }

    @Test
    void travaDoLimiteDevolveVazioSemLimite() {
        UUID escola = dados.escola();
        autenticarComo("SUPER_ADMIN", null);

        assertThat(travar(escola)).isEmpty();
        assertThat(travar(UUID.randomUUID())).isEmpty();
        limite(escola, 7);
        assertThat(travar(escola)).contains(7);
        // Fora de uma transacao, nao trava nada: recusa em vez de fingir.
        assertThatThrownBy(() -> escolaRepository.travarLimiteUsuarios(escola))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void superAdminFiltraOsLoginsPorEscolaPerfilSituacaoEBusca() {
        UUID escola = dados.escola();
        UUID outra = dados.escola();
        UUID master = dados.usuario(escola, "MASTER", "Marta");
        UUID aluno = dados.usuario(escola, "ALUNO", "Alice");
        dados.usuario(outra, "MASTER", "Mauro");
        jdbc.update("UPDATE USUARIO SET LOGIN = ? WHERE ID_USUARIO = ?", "gestor_" + escola + "@x.com", master);
        jdbc.update("UPDATE USUARIO SET ATIVO = FALSE WHERE ID_USUARIO = ?", aluno);
        autenticarComo("SUPER_ADMIN", null);

        List<UsuarioPlataformaResponse> daEscola = usuarioPlataformaService
                .listar(escola, null, null, null, new Paginacao(0, 20)).conteudo();
        assertThat(daEscola).extracting(UsuarioPlataformaResponse::id).containsExactlyInAnyOrder(master, aluno);
        assertThat(daEscola).allSatisfy(u -> assertThat(u.escolaNome()).startsWith("Escola "));

        assertThat(usuarioPlataformaService.listar(escola, PerfilUsuario.ALUNO, false, null, new Paginacao(0, 20))
                .conteudo()).extracting(UsuarioPlataformaResponse::id).containsExactly(aluno);

        // "_" digitado e texto, nao curinga; e a busca ignora maiusculas.
        assertThat(usuarioPlataformaService.listar(null, null, null, "GESTOR_" + escola, new Paginacao(0, 20))
                .conteudo()).extracting(UsuarioPlataformaResponse::id).containsExactly(master);
        assertThat(usuarioPlataformaService.listar(null, null, null, "gestor%", new Paginacao(0, 20))
                .conteudo()).isEmpty();
    }
}
