package br.pucminas.eduka.backend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.RecuperacaoSenha;
import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.RecuperacaoSenhaResponse;
import br.pucminas.eduka.backend.exception.CodigoRecuperacaoInvalidoException;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.RecuperacaoSenhaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.repository.VerificacaoLoginRepository;

/**
 * "Esqueceu sua senha?": o usuario informa o login, recebe no e-mail do
 * cadastro um codigo de 6 digitos de validade curta e, com ele, define uma
 * senha nova.
 *
 * <p>
 * Nada aqui revela se um login existe: {@link #solicitar} responde igual para
 * qualquer login, e {@link #redefinir} da o mesmo erro para codigo errado,
 * vencido ou login desconhecido. Os dois caminhos fazem o mesmo trabalho de
 * hash, para que o tempo de resposta tambem nao entregue a diferenca.
 * </p>
 *
 * <p>
 * Um codigo de 6 digitos so e seguro porque nao da para chutar a vontade: ele
 * vence em poucos minutos e, dentro de uma janela (ver
 * {@link RecuperacaoSenhaProperties}), os palpites errados se somam entre todos
 * os codigos enviados. Pedir um codigo novo nao devolve as tentativas.
 * </p>
 */
@Service
@EnableConfigurationProperties(RecuperacaoSenhaProperties.class)
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private final RecuperacaoSenhaRepository recuperacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final VerificacaoLoginRepository verificacaoLoginRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final RecuperacaoSenhaProperties properties;
    private final SecureRandom random = new SecureRandom();

    /** Comparado quando nao ha codigo a conferir, so para gastar o mesmo tempo. */
    private final String hashSentinela;

    public RecuperacaoSenhaService(RecuperacaoSenhaRepository recuperacaoRepository,
            UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            VerificacaoLoginRepository verificacaoLoginRepository, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventos, Clock clock, RecuperacaoSenhaProperties properties) {
        this.recuperacaoRepository = recuperacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.verificacaoLoginRepository = verificacaoLoginRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.clock = clock;
        this.properties = properties;
        this.hashSentinela = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Envia um codigo para o e-mail do usuario, quando ha para onde e os
     * limites da janela permitem. Em qualquer outro caso (login inexistente,
     * usuario inativo, sem e-mail, pedido cedo demais) nao envia nada e
     * responde do mesmo jeito.
     */
    @Transactional
    public RecuperacaoSenhaResponse solicitar(String login) {
        Instant agora = clock.instant();
        // Gerado e cifrado sempre, mesmo que nao seja enviado: o custo do hash e o mesmo nos dois caminhos.
        String codigo = gerarCodigo();
        String codigoHash = passwordEncoder.encode(codigo);

        Usuario usuario = usuarioAtivo(login);
        DestinatarioEmail destinatario = usuario == null ? null
                : DestinatarioEmail.doUsuario(usuario, pessoaRepository).orElse(null);
        if (destinatario == null) {
            if (usuario != null) {
                log.warn("Usuario {} sem e-mail: recuperacao de senha nao enviada", usuario.getId());
            }
            return resposta();
        }

        RecuperacaoSenha recuperacao = recuperacaoRepository.findParaAtualizarByUsuarioId(usuario.getId())
                .orElse(null);
        if (recuperacao == null) {
            recuperacao = RecuperacaoSenha.builder().usuarioId(usuario.getId()).build();
            abrirJanela(recuperacao, agora);
        } else if (janelaEncerrada(recuperacao, agora)) {
            abrirJanela(recuperacao, agora);
        } else if (!podeEnviar(recuperacao, agora)) {
            log.info("Recuperacao de senha do usuario {} nao reenviada: limite da janela", usuario.getId());
            return resposta();
        } else {
            recuperacao.setEnvios(recuperacao.getEnvios() + 1);
        }

        recuperacao.setCodigoHash(codigoHash);
        recuperacao.setExpiraEm(agora.plus(Duration.ofMinutes(properties.validadeMinutos())));
        recuperacao.setUltimoEnvioEm(agora);
        recuperacaoRepository.save(recuperacao);

        eventos.publishEvent(new CodigoRecuperacaoSenhaGerado(usuario.getId(), destinatario.nome(),
                destinatario.email(), codigo));
        return resposta();
    }

    /**
     * Confere o codigo e grava a senha nova. A senha passa a ser definitiva
     * (vale tambem para quem estava com a provisoria, que provou ter acesso ao
     * e-mail) e qualquer login que estava esperando o codigo de verificacao e
     * encerrado.
     *
     * <p>
     * {@code noRollbackFor}: a tentativa errada precisa ficar gravada mesmo
     * saindo por excecao.
     * </p>
     */
    @Transactional(noRollbackFor = CodigoRecuperacaoInvalidoException.class)
    public void redefinir(String login, String codigo, String novaSenha) {
        // Antes de conferir o codigo: senha fora da regra nao gasta tentativa.
        UsuarioService.validarSenha(novaSenha);
        Instant agora = clock.instant();

        Usuario usuario = usuarioAtivo(login);
        RecuperacaoSenha recuperacao = usuario == null ? null
                : recuperacaoRepository.findParaAtualizarByUsuarioId(usuario.getId()).orElse(null);
        boolean aberta = recuperacao != null && !recuperacao.expirada(agora)
                && recuperacao.getTentativas() < properties.maxTentativas();
        boolean confere = passwordEncoder.matches(codigo, aberta ? recuperacao.getCodigoHash() : hashSentinela);

        if (!aberta) {
            throw new CodigoRecuperacaoInvalidoException();
        }
        if (!confere) {
            recuperacao.setTentativas(recuperacao.getTentativas() + 1);
            if (recuperacao.getTentativas() >= properties.maxTentativas()) {
                // Queima o codigo; outro so sai quando a janela passar (ver podeEnviar).
                recuperacao.setExpiraEm(agora);
            }
            recuperacaoRepository.save(recuperacao);
            throw new CodigoRecuperacaoInvalidoException();
        }

        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setSenhaProvisoria(false);
        usuario.setSenhaProvisoriaExpiraEm(null);
        usuarioRepository.save(usuario);
        recuperacaoRepository.delete(recuperacao);
        verificacaoLoginRepository.deleteByUsuarioId(usuario.getId());
        log.info("Senha do usuario {} redefinida pela recuperacao por e-mail", usuario.getId());
    }

    private Usuario usuarioAtivo(String login) {
        return usuarioRepository.findByLogin(UsuarioService.normalizarLogin(login))
                .filter(Usuario::isAtivo)
                .orElse(null);
    }

    private void abrirJanela(RecuperacaoSenha recuperacao, Instant agora) {
        recuperacao.setJanelaInicio(agora);
        recuperacao.setEnvios(1);
        recuperacao.setTentativas(0);
    }

    private boolean janelaEncerrada(RecuperacaoSenha recuperacao, Instant agora) {
        Instant fim = recuperacao.getJanelaInicio().plus(Duration.ofMinutes(properties.janelaMinutos()));
        return !agora.isBefore(fim);
    }

    private boolean podeEnviar(RecuperacaoSenha recuperacao, Instant agora) {
        Instant liberadoEm = recuperacao.getUltimoEnvioEm().plusSeconds(properties.reenvioIntervaloSegundos());
        return recuperacao.getEnvios() < properties.maxEnvios()
                && recuperacao.getTentativas() < properties.maxTentativas()
                && !agora.isBefore(liberadoEm);
    }

    private String gerarCodigo() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    private RecuperacaoSenhaResponse resposta() {
        return new RecuperacaoSenhaResponse(properties.validadeMinutos() * 60, properties.reenvioIntervaloSegundos());
    }
}
