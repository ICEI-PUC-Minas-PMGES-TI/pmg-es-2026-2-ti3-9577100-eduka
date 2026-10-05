// Codigo/backend/src/main/java/br/pucminas/eduka/backend/service/AlunoService.java
package br.pucminas.eduka.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Aluno;
import br.pucminas.eduka.backend.domain.AlunoResponsavel;
import br.pucminas.eduka.backend.domain.AlunoResponsavelId;
import br.pucminas.eduka.backend.domain.PapelPessoa;
import br.pucminas.eduka.backend.domain.PerfilUsuario;
import br.pucminas.eduka.backend.domain.Pessoa;
import br.pucminas.eduka.backend.domain.PessoaPapel;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.AlunoRequest;
import br.pucminas.eduka.backend.dto.AlunoResponse;
import br.pucminas.eduka.backend.dto.EnderecoRequest;
import br.pucminas.eduka.backend.dto.ResponsavelRequest;
import br.pucminas.eduka.backend.dto.ResponsavelResponse;
import br.pucminas.eduka.backend.exception.RecursoNaoEncontradoException;
import br.pucminas.eduka.backend.repository.AlunoRepository;
import br.pucminas.eduka.backend.repository.AlunoResponsavelRepository;
import br.pucminas.eduka.backend.repository.PessoaPapelRepository;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.security.IsolamentoEscola;

/**
 * Regras de aluno com isolamento por escola (RF05, RNF03).
 *
 * <p>
 * A escola nunca e parametro: sai de {@link IsolamentoEscola#escolaIdAtual()}
 * e entra em toda consulta como filtro. Um aluno de outra escola e, para o
 * usuario atual, indistinguivel de um aluno inexistente (404 nos dois casos).
 * </p>
 *
 * <p>
 * Dado pessoal (nome, cpf, data de nascimento, endereco) vive em
 * {@link Pessoa}, cifrado. A busca por nome (enturmacao) sempre filtra
 * primeiro por {@code escola_id} (indice existente) e so decifra em memoria
 * depois - nunca {@code LIKE} sobre coluna cifrada.
 * </p>
 *
 * <p>
 * O login do aluno e criado junto com o cadastro, via {@link AcessoService}:
 * login = e-mail, senha provisoria = CPF, enviada por e-mail.
 * </p>
 */
@Service
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaPapelRepository pessoaPapelRepository;
    private final AlunoResponsavelRepository alunoResponsavelRepository;
    private final IsolamentoEscola isolamentoEscola;
    private final AcessoService acessoService;

    public AlunoService(AlunoRepository alunoRepository, PessoaRepository pessoaRepository,
            PessoaPapelRepository pessoaPapelRepository, AlunoResponsavelRepository alunoResponsavelRepository,
            IsolamentoEscola isolamentoEscola, AcessoService acessoService) {
        this.alunoRepository = alunoRepository;
        this.pessoaRepository = pessoaRepository;
        this.pessoaPapelRepository = pessoaPapelRepository;
        this.alunoResponsavelRepository = alunoResponsavelRepository;
        this.isolamentoEscola = isolamentoEscola;
        this.acessoService = acessoService;
    }

    public List<AlunoResponse> listar(String nome) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        List<Aluno> alunos = alunoRepository.findAllByEscolaId(escolaId);
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository
                .findAllByIdInAndEscolaId(alunos.stream().map(Aluno::getPessoaId).toList(), escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, pessoa -> pessoa));

        String filtro = nome == null || nome.isBlank() ? null : nome.trim().toLowerCase(Locale.ROOT);
        return alunos.stream()
                .filter(aluno -> filtro == null
                        || pessoasPorId.get(aluno.getPessoaId()).getNome().toLowerCase(Locale.ROOT).contains(filtro))
                .map(aluno -> AlunoResponse.deResumo(aluno, pessoasPorId.get(aluno.getPessoaId())))
                .toList();
    }

    public AlunoResponse buscarPorId(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(AlunoService::naoEncontrado);
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(AlunoService::naoEncontrado);
        return AlunoResponse.de(aluno, pessoa, responsaveisDe(aluno.getId(), escolaId));
    }

    /**
     * Cadastra o aluno (e, se informados, seus responsaveis) na escola do
     * usuario autenticado e cria o login dele. O request nao carrega escola:
     * mesmo que carregasse, seria ignorado. E-mail ja usado como login barra
     * o cadastro inteiro (409).
     */
    @Transactional
    public AlunoResponse cadastrar(AlunoRequest request) {
        validar(request);
        UUID escolaId = isolamentoEscola.escolaIdAtual();

        Pessoa pessoaAluno = criarPessoa(escolaId, request.nome(), request.cpf(), request.dataNascimento(),
                request.email() == null ? null : request.email().trim(), null, request.endereco());
        pessoaPapelRepository
                .save(PessoaPapel.builder().pessoaId(pessoaAluno.getId()).papel(PapelPessoa.ALUNO).build());
        Usuario usuario = acessoService.enviarAcessoInicial(pessoaAluno, PerfilUsuario.ALUNO);

        Aluno aluno = alunoRepository.save(Aluno.builder()
                .escolaId(escolaId)
                .pessoaId(pessoaAluno.getId())
                .usuarioId(usuario.getId())
                .matricula(request.matricula())
                .genero(request.genero())
                .build());

        List<ResponsavelResponse> responsaveis = new ArrayList<>();
        if (request.responsaveis() != null) {
            for (ResponsavelRequest responsavelRequest : request.responsaveis()) {
                Pessoa pessoaResponsavel = criarPessoa(escolaId, responsavelRequest.nome(), responsavelRequest.cpf(),
                        null, responsavelRequest.email(), responsavelRequest.telefone(), null);
                pessoaPapelRepository.save(PessoaPapel.builder().pessoaId(pessoaResponsavel.getId())
                        .papel(PapelPessoa.RESPONSAVEL).build());
                alunoResponsavelRepository.save(AlunoResponsavel.builder()
                        .id(new AlunoResponsavelId(aluno.getId(), pessoaResponsavel.getId()))
                        .parentesco(responsavelRequest.parentesco())
                        .build());
                responsaveis.add(new ResponsavelResponse(pessoaResponsavel.getId(), pessoaResponsavel.getNome(),
                        pessoaResponsavel.getEmail(), pessoaResponsavel.getTelefone(),
                        responsavelRequest.parentesco()));
            }
        }
        return AlunoResponse.de(aluno, pessoaAluno, responsaveis);
    }

    /**
     * Novo e-mail de primeiro acesso, com senha provisoria renovada: para
     * quem perdeu o e-mail ou deixou a senha expirar. Recusado (409) se o
     * aluno ja definiu a senha. Aluno anterior ao login automatico ganha o
     * acesso aqui, desde que tenha e-mail e CPF.
     */
    @Transactional
    public void reenviarAcesso(UUID id) {
        UUID escolaId = isolamentoEscola.escolaIdAtual();
        Aluno aluno = alunoRepository.findByIdAndEscolaId(id, escolaId).orElseThrow(AlunoService::naoEncontrado);
        Pessoa pessoa = pessoaRepository.findByIdAndEscolaId(aluno.getPessoaId(), escolaId)
                .orElseThrow(AlunoService::naoEncontrado);
        Usuario usuario = acessoService.enviarAcessoInicial(pessoa, PerfilUsuario.ALUNO);
        if (!usuario.getId().equals(aluno.getUsuarioId())) {
            aluno.setUsuarioId(usuario.getId());
            alunoRepository.save(aluno);
        }
    }

    private Pessoa criarPessoa(UUID escolaId, String nome, String cpf, java.time.LocalDate dataNascimento,
            String email, String telefone, EnderecoRequest endereco) {
        return pessoaRepository.save(Pessoa.builder()
                .escolaId(escolaId)
                .nome(nome)
                .cpf(cpf)
                .dataNascimento(dataNascimento == null ? null : dataNascimento.toString())
                .email(email)
                .telefone(telefone)
                .logradouro(endereco == null ? null : endereco.logradouro())
                .numero(endereco == null ? null : endereco.numero())
                .bairro(endereco == null ? null : endereco.bairro())
                .cidade(endereco == null ? null : endereco.cidade())
                .estado(endereco == null ? null : endereco.estado())
                .cep(endereco == null ? null : endereco.cep())
                .ativo(true)
                .build());
    }

    private List<ResponsavelResponse> responsaveisDe(UUID alunoId, UUID escolaId) {
        List<AlunoResponsavel> vinculos = alunoResponsavelRepository.findAllById_AlunoId(alunoId);
        if (vinculos.isEmpty()) {
            return List.of();
        }
        Map<UUID, Pessoa> pessoasPorId = pessoaRepository
                .findAllByIdInAndEscolaId(vinculos.stream().map(vinculo -> vinculo.getId().pessoaId()).toList(),
                        escolaId)
                .stream().collect(Collectors.toMap(Pessoa::getId, pessoa -> pessoa));
        return vinculos.stream()
                .map(vinculo -> {
                    Pessoa pessoa = pessoasPorId.get(vinculo.getId().pessoaId());
                    return new ResponsavelResponse(pessoa.getId(), pessoa.getNome(), pessoa.getEmail(),
                            pessoa.getTelefone(), vinculo.getParentesco());
                })
                .toList();
    }

    private static void validar(AlunoRequest request) {
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("Nome do aluno e obrigatorio");
        }
        if (request.matricula() == null || request.matricula().isBlank()) {
            throw new IllegalArgumentException("Matricula do aluno e obrigatoria");
        }
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Aluno nao encontrado");
    }
}
