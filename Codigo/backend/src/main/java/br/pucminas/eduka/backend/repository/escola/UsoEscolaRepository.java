package br.pucminas.eduka.backend.repository.escola;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import br.pucminas.eduka.backend.domain.escola.Escola;

/**
 * Uso de cada escola para o Painel de Escolas (IU02). Nativo: o SUPER_ADMIN
 * nao tem escola no token, e em JPQL o {@code @TenantId} de alunos e
 * cobrancas filtraria por uma escola que nao existe.
 */
public interface UsoEscolaRepository extends Repository<Escola, UUID> {

    /**
     * Uma linha por escola. Cobrancas no mes sao as lancadas de {@code inicio}
     * (inclusive) a {@code fim} (exclusive), canceladas ou nao. A ultima
     * atividade e o mais recente entre o ultimo login de um usuario da escola
     * e a ultima acao de usuario no log de auditoria (os avisos do Asaas, sem
     * usuario, nao contam).
     */
    @Query(value = "SELECT e.ID_ESCOLA AS escolaId, "
            + "(SELECT COUNT(*) FROM alunos a WHERE a.escola_id = e.ID_ESCOLA) AS alunos, "
            + "(SELECT COUNT(*) FROM USUARIO u WHERE u.ESCOLA_ID = e.ID_ESCOLA AND u.ATIVO) AS usuariosAtivos, "
            + "(SELECT COUNT(*) FROM COBRANCA c WHERE c.ESCOLA_ID = e.ID_ESCOLA "
            + "AND c.CRIADO_EM >= :inicio AND c.CRIADO_EM < :fim) AS cobrancasNoMes, "
            + "GREATEST((SELECT MAX(u.ULTIMO_ACESSO_EM) FROM USUARIO u WHERE u.ESCOLA_ID = e.ID_ESCOLA), "
            + "(SELECT MAX(l.DATA_HORA) FROM LOG_AUDITORIA l WHERE l.ESCOLA_ID = e.ID_ESCOLA "
            + "AND l.USUARIO_ID IS NOT NULL)) AS ultimaAtividade "
            + "FROM ESCOLA e", nativeQuery = true)
    List<UsoEscola> usoPorEscola(@Param("inicio") Instant inicio, @Param("fim") Instant fim);

    interface UsoEscola {
        UUID getEscolaId();

        long getAlunos();

        long getUsuariosAtivos();

        long getCobrancasNoMes();

        Instant getUltimaAtividade();
    }
}
