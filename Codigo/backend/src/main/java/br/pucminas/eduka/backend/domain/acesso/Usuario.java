package br.pucminas.eduka.backend.domain.acesso;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "USUARIO")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_USUARIO")
    private UUID id;

    @Column(name = "ESCOLA_ID")
    private UUID escolaId;

    @Column(name = "PESSOA_ID", nullable = false)
    private UUID pessoaId;

    @Column(name = "LOGIN", nullable = false, unique = true)
    private String login;

    /**
     * Hash da senha com prefixo do algoritmo. Nunca recebe a senha em texto
     * puro — ver {@link br.pucminas.eduka.backend.service.acesso.UsuarioService}.
     */
    @Column(name = "SENHA_HASH", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "PERFIL", nullable = false)
    private PerfilUsuario perfil;

    @Column(name = "ATIVO", nullable = false)
    private boolean ativo;

    /**
     * Senha gerada pelo sistema no primeiro acesso (ver
     * {@link br.pucminas.eduka.backend.service.acesso.AcessoService}). Enquanto
     * verdadeiro, o login nao emite token: so a troca por senha definitiva.
     */
    @Column(name = "SENHA_PROVISORIA", nullable = false)
    private boolean senhaProvisoria;

    @Column(name = "SENHA_PROVISORIA_EXPIRA_EM")
    private Instant senhaProvisoriaExpiraEm;

    /**
     * Ultima troca de senha. Refresh token emitido antes disso nao renova mais
     * a sessao (ver {@link #sessaoAnteriorATrocaDeSenha}).
     */
    @Column(name = "SENHA_ALTERADA_EM")
    private Instant senhaAlteradaEm;

    /** Preenchido pelo DEFAULT da coluna e relido apos o insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private Instant criadoEm;

    public boolean senhaProvisoriaExpirada(Instant agora) {
        return senhaProvisoriaExpiraEm != null && !agora.isBefore(senhaProvisoriaExpiraEm);
    }

    /**
     * Grava a senha nova e marca a troca: as sessoes abertas com a senha
     * anterior deixam de ser renovadas.
     */
    public void trocarSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.senhaAlteradaEm = agora;
    }

    /**
     * O {@code iat} do JWT tem precisao de segundos e a troca, de
     * microssegundos: a comparacao e feita no segundo da troca, para um token
     * emitido logo depois dela, no mesmo segundo, continuar valendo.
     */
    public boolean sessaoAnteriorATrocaDeSenha(Instant emitidaEm) {
        return senhaAlteradaEm != null && emitidaEm.isBefore(senhaAlteradaEm.truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * Evita que a senha vaze em log, stack trace ou resposta de erro.
     */
    @Override
    public String toString() {
        return "Usuario[id=%s, login=%s, perfil=%s, ativo=%s]".formatted(id, login, perfil, ativo);
    }
}
