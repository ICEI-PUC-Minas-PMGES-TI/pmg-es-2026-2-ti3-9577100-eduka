package br.pucminas.eduka.backend.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.SolicitacaoCorrecao;
import br.pucminas.eduka.backend.domain.StatusSolicitacaoCorrecao;
import br.pucminas.eduka.backend.dto.SolicitacaoCorrecaoResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.exception.SolicitacaoCorrecaoAbertaException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.SolicitacaoCorrecaoRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;
import br.pucminas.eduka.backend.security.UsuarioAutenticado;

/**
 * Pedido de correcao de dados (IU15): o aluno so consulta os proprios dados e
 * pede a secretaria que corrija; a secretaria ve os pedidos abertos e marca
 * como atendidos depois de corrigir o cadastro.
 */
@Service
public class SolicitacaoCorrecaoService {

    private final SolicitacaoCorrecaoRepository solicitacaoRepository;
    private final AlunoRepository alunoRepository;
    private final PessoaRepository pessoaRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final Clock clock;

    public SolicitacaoCorrecaoService(SolicitacaoCorrecaoRepository solicitacaoRepository,
            AlunoRepository alunoRepository, PessoaRepository pessoaRepository, IsolamentoEscola isolamentoEscola,
            Clock clock) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.alunoRepository = alunoRepository;
        this.pessoaRepository = pessoaRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.clock = clock;
    }

    /**
     * O aluno do token pede a correcao.
     *
     * @throws SolicitacaoCorrecaoAbertaException se ja ha um pedido aberto
     */
    @Transactional
    public SolicitacaoCorrecaoResponse abrir(String mensagem) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoAtual(escolaId);
        if (aberta(escolaId, aluno).isPresent()) {
            throw new SolicitacaoCorrecaoAbertaException();
        }
        try {
            SolicitacaoCorrecao salva = solicitacaoRepository.saveAndFlush(SolicitacaoCorrecao.builder()
                    .escolaId(escolaId)
                    .alunoId(aluno.getId())
                    .mensagem(mensagem.trim())
                    .build());
            return SolicitacaoCorrecaoResponse.de(salva, null, null);
        } catch (DataIntegrityViolationException e) {
            // Dois envios ao mesmo tempo: o indice parcial segura o segundo.
            throw new SolicitacaoCorrecaoAbertaException();
        }
    }

    /** O pedido aberto do aluno do token, se houver. */
    @Transactional(readOnly = true)
    public Optional<SolicitacaoCorrecaoResponse> minhaAberta() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        return aberta(escolaId, alunoAtual(escolaId)).map(s -> SolicitacaoCorrecaoResponse.de(s, null, null));
    }

    /** Pedidos abertos da escola, os mais antigos primeiro, com o nome e a matricula do aluno. */
    @Transactional(readOnly = true)
    public List<SolicitacaoCorrecaoResponse> listarAbertas() {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<SolicitacaoCorrecao> abertas = solicitacaoRepository
                .findAllByEscolaIdAndStatusOrderByCriadoEmAsc(escolaId, StatusSolicitacaoCorrecao.ABERTA);
        Map<UUID, Aluno> alunos = alunoRepository
                .findAllById(abertas.stream().map(SolicitacaoCorrecao::getAlunoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Aluno::getId, Function.identity()));
        Map<UUID, String> nomes = pessoaRepository
                .findAllByIdInAndEscolaId(alunos.values().stream().map(Aluno::getPessoaId).toList(), escolaId)
                .stream()
                .collect(Collectors.toMap(Pessoa::getId, Pessoa::getNome));
        return abertas.stream().map(s -> {
            Aluno aluno = alunos.get(s.getAlunoId());
            return SolicitacaoCorrecaoResponse.de(s, aluno != null ? nomes.get(aluno.getPessoaId()) : null,
                    aluno != null ? aluno.getMatricula() : null);
        }).toList();
    }

    /** A secretaria corrigiu o cadastro: o pedido fecha e o aluno pode pedir de novo. */
    @Transactional
    public SolicitacaoCorrecaoResponse atender(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        SolicitacaoCorrecao solicitacao = solicitacaoRepository.findByIdAndEscolaId(id, escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido de correcao nao encontrado"));
        if (solicitacao.getStatus() == StatusSolicitacaoCorrecao.ABERTA) {
            solicitacao.setStatus(StatusSolicitacaoCorrecao.ATENDIDA);
            solicitacao.setAtendidaEm(clock.instant());
        }
        return SolicitacaoCorrecaoResponse.de(solicitacao, null, null);
    }

    private Optional<SolicitacaoCorrecao> aberta(UUID escolaId, Aluno aluno) {
        return solicitacaoRepository.findFirstByEscolaIdAndAlunoIdAndStatus(escolaId, aluno.getId(),
                StatusSolicitacaoCorrecao.ABERTA);
    }

    /** Aluno dono do login. 404 se o login nao e de um aluno da escola. */
    private Aluno alunoAtual(UUID escolaId) {
        UsuarioAutenticado usuario = isolamentoEscola.usuarioAtual();
        return alunoRepository.findByUsuarioIdAndEscolaId(usuario.id(), escolaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno nao encontrado"));
    }
}
