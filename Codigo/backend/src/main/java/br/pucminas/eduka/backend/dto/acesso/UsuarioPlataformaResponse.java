package br.pucminas.eduka.backend.dto.acesso;

import java.time.Instant;
import java.util.UUID;

import br.pucminas.eduka.backend.domain.acesso.PerfilUsuario;
import br.pucminas.eduka.backend.domain.acesso.Usuario;

/**
 * Um login da plataforma na tela Usuarios do SUPER_ADMIN (S5-14). Sem nome
 * nem CPF da pessoa: para gerir o acesso basta o login (o e-mail), e os dados
 * pessoais ficam com a escola.
 *
 * @param escolaId                 nulo para o SUPER_ADMIN
 * @param escolaNome               nulo para o SUPER_ADMIN
 * @param aguardandoPrimeiroAcesso ainda com a senha provisoria
 * @param ultimoAcessoEm           ultimo login concluido; nulo se nunca entrou
 * @param gerenciavel              o SUPER_ADMIN pode ativar, inativar e reenviar
 *                                 o acesso: so o MASTER, que e quem ele cria. Os
 *                                 demais sao geridos pela propria escola
 */
public record UsuarioPlataformaResponse(
        UUID id,
        String login,
        PerfilUsuario perfil,
        UUID escolaId,
        String escolaNome,
        boolean ativo,
        boolean aguardandoPrimeiroAcesso,
        Instant ultimoAcessoEm,
        Instant criadoEm,
        boolean gerenciavel) {

    public static UsuarioPlataformaResponse de(Usuario usuario, String escolaNome) {
        return new UsuarioPlataformaResponse(usuario.getId(), usuario.getLogin(), usuario.getPerfil(),
                usuario.getEscolaId(), escolaNome, usuario.isAtivo(), usuario.isSenhaProvisoria(),
                usuario.getUltimoAcessoEm(), usuario.getCriadoEm(), usuario.getPerfil() == PerfilUsuario.MASTER);
    }
}
