package br.pucminas.eduka.backend.security;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Isolamento entre escolas (RF05, RNF03): a escola do usuario vem do token,
 * nunca de parametro da requisicao, e todo acesso a dado de negocio passa
 * por aqui.
 *
 * <p>
 * Uso tipico num servico:
 * </p>
 *
 * <pre>
 * Aluno aluno = alunoRepository.findById(id).orElseThrow(...);
 * isolamentoEscola.exigirAcesso(aluno.getEscolaId());
 * </pre>
 *
 * <p>
 * ou, para listagens, filtrar pelo {@link #escolaIdAtual()} em vez de aceitar
 * um {@code escolaId} vindo do cliente.
 * </p>
 *
 * <p>
 * {@code SUPER_ADMIN} administra a plataforma (RF06), nao pertence a
 * nenhuma escola (token sem {@code escolaId}) e passa em qualquer uma. Os
 * modulos de negocio (academico, financeiro...) ja o barram em
 * {@link RotasPorPerfil}; a excecao aqui serve aos recursos de plataforma,
 * como os usuarios de cada escola.
 * </p>
 */
@Component
public class IsolamentoEscola {

    /**
     * Lanca {@link AcessoOutraEscolaException} (403) se o recurso pertencer a
     * outra escola. Super admin nunca e barrado.
     */
    public void exigirAcesso(UUID escolaIdDoRecurso) {
        if (escolaIdDoRecurso == null) {
            throw new IllegalArgumentException("escolaId do recurso nao pode ser nulo");
        }
        UsuarioAutenticado usuario = usuarioAtual();
        if (usuario.isSuperAdmin()) {
            return;
        }
        if (!usuario.escolaId().equals(escolaIdDoRecurso)) {
            throw new AcessoOutraEscolaException();
        }
    }

    /**
     * Escola do usuario autenticado, para filtrar consultas. Para super admin
     * devolve {@code null}; quem precisar de "todas as escolas" deve testar
     * {@link UsuarioAutenticado#isSuperAdmin()} antes.
     */
    public UUID escolaIdAtual() {
        return usuarioAtual().escolaId();
    }

    public UsuarioAutenticado usuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Nenhum usuario autenticado no contexto da requisicao");
        }
        return usuario;
    }
}
