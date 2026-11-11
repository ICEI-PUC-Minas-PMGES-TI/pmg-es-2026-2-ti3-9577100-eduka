package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.AceitePrivacidade;
import br.pucminas.eduka.backend.dto.AvisoPrivacidadeResponse;
import br.pucminas.eduka.backend.repository.AceitePrivacidadeRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Aviso de privacidade e aceite do usuario do token (IU29), de qualquer
 * perfil. O aceite vale para a versao em {@link AvisoPrivacidade#VERSAO}:
 * quando ela muda, todos veem o aviso de novo.
 */
@Service
public class PrivacidadeService {

    private final AceitePrivacidadeRepository aceiteRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public PrivacidadeService(AceitePrivacidadeRepository aceiteRepository, IsolamentoEscola isolamentoEscola,
            Clock clock) {
        this.aceiteRepository = aceiteRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvisoPrivacidadeResponse consultar() {
        return resposta(aceiteRepository
                .findByUsuarioIdAndVersao(usuarioAtual(), AvisoPrivacidade.VERSAO)
                .map(AceitePrivacidade::getAceitoEm)
                .orElse(null));
    }

    /**
     * Grava o aceite da versao em vigor. Aceitar de novo a mesma versao (dois
     * cliques, duas abas) devolve o aceite que ja existia.
     *
     * @throws IllegalArgumentException a versao nao e a em vigor: o usuario
     *                                  leu um aviso que ja mudou
     */
    @Transactional
    public AvisoPrivacidadeResponse aceitar(String versao) {
        if (!AvisoPrivacidade.VERSAO.equals(versao)) {
            throw new IllegalArgumentException("Versao do aviso de privacidade desatualizada");
        }
        UUID usuarioId = usuarioAtual();
        AceitePrivacidade aceite = aceiteRepository.findByUsuarioIdAndVersao(usuarioId, versao)
                .orElseGet(() -> gravar(usuarioId, versao));
        return resposta(aceite.getAceitoEm());
    }

    private AceitePrivacidade gravar(UUID usuarioId, String versao) {
        try {
            return aceiteRepository.saveAndFlush(AceitePrivacidade.builder()
                    .usuarioId(usuarioId)
                    .versao(versao)
                    .aceitoEm(Instant.now(clock))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Outra requisicao do mesmo usuario gravou no meio: vale a dela.
            return aceiteRepository.findByUsuarioIdAndVersao(usuarioId, versao).orElseThrow(() -> e);
        }
    }

    private UUID usuarioAtual() {
        return isolamentoEscola.usuarioAtual().id();
    }

    private static AvisoPrivacidadeResponse resposta(Instant aceitoEm) {
        return new AvisoPrivacidadeResponse(AvisoPrivacidade.VERSAO, AvisoPrivacidade.PUBLICADO_EM,
                AvisoPrivacidade.RESUMO, AvisoPrivacidade.SECOES, aceitoEm);
    }
}
