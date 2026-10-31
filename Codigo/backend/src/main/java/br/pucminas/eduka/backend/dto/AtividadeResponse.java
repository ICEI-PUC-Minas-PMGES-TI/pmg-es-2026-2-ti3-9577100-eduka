package br.pucminas.eduka.backend.dto;

import java.time.Instant;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.OrigemAuditoria;
import br.pucminas.eduka.backend.domain.PerfilUsuario;

/**
 * Uma linha do Registro de atividades (IU28).
 *
 * @param origem       USUARIO, ou PROVEDOR quando foi o aviso do Asaas; nesse
 *                     caso usuario, nome e perfil vem nulos
 * @param usuarioNome  nulo tambem se a pessoa nao e mais da escola
 */
public record AtividadeResponse(
        UUID id,
        Instant dataHora,
        OrigemAuditoria origem,
        OperacaoAuditoria operacao,
        EntidadeAuditada entidade,
        UUID registroId,
        UUID usuarioId,
        String usuarioNome,
        PerfilUsuario usuarioPerfil) {
}
