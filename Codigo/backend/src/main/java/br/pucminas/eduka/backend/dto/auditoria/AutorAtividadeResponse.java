package br.pucminas.eduka.backend.dto.auditoria;

import java.util.UUID;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;

/**
 * Quem ja fez alguma atividade na escola: as opcoes do filtro "Pessoa" do
 * Registro de atividades (IU28).
 *
 * @param nome nulo se a pessoa nao e mais da escola
 */
public record AutorAtividadeResponse(UUID usuarioId, String nome, PerfilUsuario perfil) {
}
