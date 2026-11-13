package br.pucminas.eduka.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.EntidadeAuditada;
import br.pucminas.eduka.backend.domain.OperacaoAuditoria;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.MasterInicialRequest;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;

/**
 * Primeiro login MASTER de uma escola recem-cadastrada, criado pelo
 * SUPER_ADMIN. Mesmo caminho da equipe: a {@link Pessoa} vai para a escola e o
 * {@link AcessoService} cria o usuario com o CPF de senha provisoria e agenda
 * o e-mail de primeiro acesso.
 *
 * <p>
 * Roda dentro da transacao do cadastro da escola: login ja usado (409) desfaz
 * a escola tambem, e ninguem recebe credencial de uma escola que nao existe.
 * {@link Pessoa} e {@link Usuario} nao sao particionados por
 * {@code @TenantId}, por isso o SUPER_ADMIN, sem escola no token, consegue
 * grava-los numa escola-alvo.
 * </p>
 */
@Service
public class MasterInicialService {

    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final AcessoService acessoService;
    private final AuditoriaService auditoriaService;

    public MasterInicialService(PessoaRepository pessoaRepository, PessoaPapelRepository pessoaPapelRepository,
            AcessoService acessoService, AuditoriaService auditoriaService) {
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.acessoService = acessoService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Usuario criar(UUID escolaId, MasterInicialRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Gestor (MASTER) da escola e obrigatorio");
        }
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(request.nome().trim())
                .cpf(request.cpf())
                .email(request.email().trim())
                .ativo(true)
                .build());
        pessoaPapelRepository
                .save(PessoaPapel.builder().pessoaId(pessoa.getId()).papel(PapelPessoa.FUNCIONARIO).build());

        Usuario usuario = acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.MASTER);
        auditoriaService.registrarNaEscola(OperacaoAuditoria.CRIAR, EntidadeAuditada.USUARIO, usuario.getId(),
                escolaId);
        return usuario;
    }
}
