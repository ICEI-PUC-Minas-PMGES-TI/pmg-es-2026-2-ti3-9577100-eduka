package br.pucminas.eduka.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.CategoriaDespesa;
import br.pucminas.eduka.backend.dto.CategoriaDespesaRequest;
import br.pucminas.eduka.backend.dto.CategoriaDespesaResponse;
import br.pucminas.eduka.backend.exception.CategoriaDespesaJaExisteException;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.CategoriaDespesaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Categorias de despesa da escola do token (RF18). Categoria de outra escola
 * responde 404.
 *
 * <p>
 * Escola sem nenhuma categoria recebe as {@link #PADRAO} na primeira
 * consulta, entao escola nova ja abre a tela de Despesas com o que escolher.
 * Depois disso a lista e so o que esta gravado.
 * </p>
 */
@Service
public class CategoriaDespesaService {

    /** As mesmas opcoes que a tela de Despesas oferecia antes de existir a API. */
    static final List<String> PADRAO = List.of("Salários", "Contas", "Materiais", "Consertos", "Limpeza", "Outros");

    private final CategoriaDespesaRepository categoriaRepository;
    private final IsolamentoEscola isolamentoEscola;

    public CategoriaDespesaService(CategoriaDespesaRepository categoriaRepository,
            IsolamentoEscola isolamentoEscola) {
        this.categoriaRepository = categoriaRepository;
        this.isolamentoEscola = isolamentoEscola;
    }

    /** Categorias da escola pelo nome, criando as padrao se a escola ainda nao tem nenhuma. */
    @Transactional
    public List<CategoriaDespesaResponse> listar() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<CategoriaDespesa> categorias = categoriaRepository.findAllByEscolaIdOrderByNome(escolaId);
        if (categorias.isEmpty()) {
            categoriaRepository.saveAll(PADRAO.stream()
                    .map(nome -> CategoriaDespesa.builder().escolaId(escolaId).nome(nome).build())
                    .toList());
            categorias = categoriaRepository.findAllByEscolaIdOrderByNome(escolaId);
        }
        return categorias.stream().map(CategoriaDespesaResponse::de).toList();
    }

    /**
     * @throws CategoriaDespesaJaExisteException se a escola ja tem categoria
     *                                           com esse nome
     */
    @Transactional
    public CategoriaDespesaResponse criar(CategoriaDespesaRequest request) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        String nome = request.nome().trim();
        if (categoriaRepository.existsByEscolaIdAndNomeIgnoreCase(escolaId, nome)) {
            throw new CategoriaDespesaJaExisteException();
        }
        CategoriaDespesa salva = categoriaRepository
                .saveAndFlush(CategoriaDespesa.builder().escolaId(escolaId).nome(nome).build());
        return CategoriaDespesaResponse.de(salva);
    }

    /** @throws RecursoNaoEncontradoException se a categoria nao existe ou e de outra escola */
    CategoriaDespesa buscarDaEscola(UUID id, UUID escolaId) {
        return categoriaRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria de despesa nao encontrada"));
    }
}
