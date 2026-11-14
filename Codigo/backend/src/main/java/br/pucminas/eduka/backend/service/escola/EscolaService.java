package br.pucminas.eduka.backend.service.escola;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.domain.auditoria.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.auditoria.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.comum.Endereco;
import br.pucminas.eduka.backend.domain.escola.Escola;
import br.pucminas.eduka.backend.dto.comum.EnderecoDto;
import br.pucminas.eduka.backend.dto.escola.AssinaturaResponse;
import br.pucminas.eduka.backend.dto.escola.EscolaRequest;
import br.pucminas.eduka.backend.dto.escola.EscolaResponse;
import br.pucminas.eduka.backend.dto.escola.EscolaUpdateRequest;
import br.pucminas.eduka.backend.exception.escola.CnpjJaCadastradoException;
import br.pucminas.eduka.backend.repository.escola.EscolaRepository;
import br.pucminas.eduka.backend.service.acesso.MasterInicialService;
import br.pucminas.eduka.backend.service.auditoria.AuditoriaService;

@Service
public class EscolaService {

    private final EscolaRepository escolaRepository;
    private final AuditoriaService auditoriaService;
    private final MasterInicialService masterInicialService;

    public EscolaService(EscolaRepository escolaRepository, AuditoriaService auditoriaService,
            MasterInicialService masterInicialService) {
        this.escolaRepository = escolaRepository;
        this.auditoriaService = auditoriaService;
        this.masterInicialService = masterInicialService;
    }

    public List<EscolaResponse> listar() {
        return escolaRepository.findAll().stream()
                .map(EscolaResponse::from)
                .toList();
    }

    public EscolaResponse buscarPorId(UUID id) {
        return EscolaResponse.from(buscarOuFalhar(id));
    }

    /** Assinatura da escola (IU24-A): o MASTER consulta a da escola do token. */
    @Transactional(readOnly = true)
    public AssinaturaResponse assinatura(UUID escolaId) {
        return AssinaturaResponse.de(buscarOuFalhar(escolaId));
    }

    /**
     * Cadastro de escola nova (RF06). Restrito a {@code SUPER_ADMIN}: criar um
     * tenant e operacao de plataforma, nao de uma escola. Ja cria o primeiro
     * login MASTER e agenda o e-mail de primeiro acesso dele; se o login falhar
     * (e-mail ja usado), a escola tambem nao e criada.
     */
    @Transactional
    public Escola cadastrar(EscolaRequest request) {
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome da escola e obrigatorio");
        }
        if (request.plano() == null) {
            throw new IllegalArgumentException("Plano da escola e obrigatorio");
        }
        if (request.master() == null) {
            throw new IllegalArgumentException("Gestor (MASTER) da escola e obrigatorio");
        }

        String cnpjCpf = vazioParaNulo(request.cnpjCpf());
        if (cnpjCpf != null && escolaRepository.existsByCnpjCpf(cnpjCpf)) {
            throw new CnpjJaCadastradoException(cnpjCpf);
        }

        Escola escola = Escola.cadastrar(
                request.nome().trim(),
                cnpjCpf,
                paraEndereco(request.endereco()),
                vazioParaNulo(request.email()),
                vazioParaNulo(request.telefone()),
                request.plano(),
                request.ativo() == null || request.ativo());
        Escola salva = escolaRepository.save(escola);
        auditar(OperacaoAuditoria.CRIAR, salva.getId());
        masterInicialService.criar(salva.getId(), request.master());
        return salva;
    }

    /** Edicao de escola (IU03). CNPJ nao pode colidir com o de outra escola. */
    @Transactional
    public EscolaResponse atualizar(UUID id, EscolaUpdateRequest request) {
        Escola escola = buscarOuFalhar(id);

        String cnpjCpf = vazioParaNulo(request.cnpjCpf());
        if (cnpjCpf != null && escolaRepository.existsByCnpjCpfAndIdNot(cnpjCpf, id)) {
            throw new CnpjJaCadastradoException(cnpjCpf);
        }

        escola.atualizarDados(request.nome(), request.cnpjCpf(),
                request.endereco() == null ? null : request.endereco().paraDominio(),
                request.email(), request.telefone(), request.plano());
        escola.atualizarAssinatura(request.fimVigencia(), request.limiteUsuarios(), request.modulos());
        auditar(OperacaoAuditoria.EDITAR, id);
        if (request.ativo() != null) {
            // So entra no log quando a situacao muda de fato.
            boolean mudou = request.ativo() != escola.isAtivo();
            if (request.ativo()) {
                escola.ativar();
                escolaRepository.reiniciarAvisosDeSuspensao(escola.getId());
            } else {
                escola.suspender();
            }
            if (mudou) {
                auditar(request.ativo() ? OperacaoAuditoria.ATIVAR : OperacaoAuditoria.SUSPENDER, id);
            }
        }
        return EscolaResponse.from(escolaRepository.save(escola));
    }

    @Transactional
    public EscolaResponse ativar(UUID id) {
        Escola escola = buscarOuFalhar(id);
        if (!escola.isAtivo()) {
            auditar(OperacaoAuditoria.ATIVAR, id);
        }
        escola.ativar();
        // Fatura ainda em aberto volta a ter aviso e prazo antes de suspender de novo.
        escolaRepository.reiniciarAvisosDeSuspensao(id);
        return EscolaResponse.from(escolaRepository.save(escola));
    }

    @Transactional
    public EscolaResponse suspender(UUID id) {
        Escola escola = buscarOuFalhar(id);
        if (escola.isAtivo()) {
            auditar(OperacaoAuditoria.SUSPENDER, id);
        }
        escola.suspender();
        return EscolaResponse.from(escolaRepository.save(escola));
    }

    /**
     * Quem mexe na escola e o SUPER_ADMIN, que nao tem escola: a linha vai
     * para a escola afetada, e o MASTER dela ve no registro de atividades.
     */
    private void auditar(OperacaoAuditoria operacao, UUID escolaId) {
        auditoriaService.registrarNaEscola(operacao, EntidadeAuditada.ESCOLA, escolaId, escolaId);
    }

    private static Endereco paraEndereco(EnderecoDto dto) {
        if (dto == null) {
            return null;
        }
        return Endereco.normalizado(dto.logradouro(), dto.numero(), dto.bairro(), dto.cidade(), dto.estado(),
                dto.cep());
    }

    private static String vazioParaNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private Escola buscarOuFalhar(UUID id) {
        return escolaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "escola não encontrada"));
    }
}
