package br.pucminas.eduka.backend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.domain.VerificacaoLogin;
import br.pucminas.eduka.backend.dto.VerificacaoLoginResponse;
import br.pucminas.eduka.backend.exception.CodigoVerificacaoExpiradoException;
import br.pucminas.eduka.backend.exception.CodigoVerificacaoIncorretoException;
import br.pucminas.eduka.backend.exception.ReenvioCodigoIndisponivelException;
import br.pucminas.eduka.backend.exception.VerificacaoLoginEncerradaException;
import br.pucminas.eduka.backend.repository.PessoaRepository;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.repository.VerificacaoLoginRepository;

/**
 * Segunda etapa do login: codigo de 6 digitos enviado para o e-mail do
 * usuario depois de a senha conferir. Os tokens so sao emitidos por
 * {@link AuthService} quando {@link #confirmar} aceita o codigo.
 *
 * <p>
 * Um codigo de 6 digitos so e seguro porque nao da para chutar a vontade:
 * ele expira, cada verificacao aceita poucos erros e poucos reenvios, e
 * depois disso so um novo login com a senha abre outra.
 * </p>
 */
@Service
@EnableConfigurationProperties(VerificacaoLoginProperties.class)
public class VerificacaoLoginService {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoLoginService.class);

    private final VerificacaoLoginRepository verificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final VerificacaoLoginProperties properties;
    private final SecureRandom random = new SecureRandom();

    public VerificacaoLoginService(VerificacaoLoginRepository verificacaoRepository,
            UsuarioRepository usuarioRepository, PessoaRepository pessoaRepository,
            PasswordEncoder passwordEncoder, ApplicationEventPublisher eventos, Clock clock,
            VerificacaoLoginProperties properties) {
        this.verificacaoRepository = verificacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pessoaRepository = pessoaRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Abre a verificacao de um usuario que acabou de acertar a senha e agenda
     * o e-mail com o codigo. Qualquer verificacao anterior dele deixa de valer.
     *
     * @return vazio quando nao ha verificacao a fazer e o login pode emitir os
     *         tokens direto: recurso desligado, ou usuario sem e-mail para
     *         onde mandar o codigo (exigir o codigo dele seria tranca-lo fora)
     */
    @Transactional
    public Optional<VerificacaoLoginResponse> iniciar(Usuario usuario) {
        if (!properties.habilitada()) {
            return Optional.empty();
        }
        DestinatarioEmail destinatario = destinatario(usuario).orElse(null);
        if (destinatario == null) {
            log.warn("Usuario {} sem e-mail: login concluido sem codigo de verificacao", usuario.getId());
            return Optional.empty();
        }

        verificacaoRepository.deleteByUsuarioId(usuario.getId());

        Instant agora = clock.instant();
        String codigo = gerarCodigo();
        VerificacaoLogin verificacao = verificacaoRepository.save(VerificacaoLogin.builder()
                .usuarioId(usuario.getId())
                .codigoHash(passwordEncoder.encode(codigo))
                .expiraEm(agora.plus(validade()))
                .tentativas(0)
                .envios(1)
                .ultimoEnvioEm(agora)
                .build());

        eventos.publishEvent(new CodigoLoginGerado(usuario.getId(), destinatario.nome(), destinatario.email(), codigo));
        return Optional.of(resposta(verificacao, agora));
    }

    /**
     * Confere o codigo e, se bater, encerra a verificacao: cada uma so serve
     * para um login.
     *
     * <p>
     * {@code noRollbackFor}: contar a tentativa errada e apagar a verificacao
     * esgotada precisam ser gravados mesmo saindo por excecao. Pelo mesmo
     * motivo, quem chama nao pode estar em outra transacao.
     * </p>
     *
     * @return id do usuario que concluiu a verificacao
     */
    @Transactional(noRollbackFor = { CodigoVerificacaoIncorretoException.class,
            CodigoVerificacaoExpiradoException.class, VerificacaoLoginEncerradaException.class })
    public UUID confirmar(UUID verificacaoId, String codigo) {
        VerificacaoLogin verificacao = verificacaoRepository.findParaAtualizarById(verificacaoId)
                .orElseThrow(VerificacaoLoginEncerradaException::new);

        if (verificacao.expirada(clock.instant())) {
            throw new CodigoVerificacaoExpiradoException();
        }

        if (!passwordEncoder.matches(codigo, verificacao.getCodigoHash())) {
            verificacao.setTentativas(verificacao.getTentativas() + 1);
            if (verificacao.getTentativas() >= properties.maxTentativas()) {
                verificacaoRepository.delete(verificacao);
                throw new VerificacaoLoginEncerradaException();
            }
            verificacaoRepository.save(verificacao);
            throw new CodigoVerificacaoIncorretoException();
        }

        verificacaoRepository.delete(verificacao);
        return verificacao.getUsuarioId();
    }

    /**
     * Troca o codigo por um novo, com validade e tentativas zeradas. Vale
     * tambem para codigo ja expirado: quem tem o id da verificacao ja provou
     * que sabe a senha.
     */
    @Transactional
    public VerificacaoLoginResponse reenviar(UUID verificacaoId) {
        VerificacaoLogin verificacao = verificacaoRepository.findParaAtualizarById(verificacaoId)
                .orElseThrow(VerificacaoLoginEncerradaException::new);
        Usuario usuario = usuarioRepository.findById(verificacao.getUsuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(VerificacaoLoginEncerradaException::new);
        DestinatarioEmail destinatario = destinatario(usuario)
                .orElseThrow(VerificacaoLoginEncerradaException::new);

        Instant agora = clock.instant();
        if (verificacao.getEnvios() >= properties.maxEnvios()) {
            throw new ReenvioCodigoIndisponivelException(
                    "Limite de envios atingido. Faca o login novamente");
        }
        long espera = segundosAteReenvio(verificacao, agora);
        if (espera > 0) {
            throw new ReenvioCodigoIndisponivelException(
                    "Aguarde %d segundos para pedir um novo codigo".formatted(espera));
        }

        String codigo = gerarCodigo();
        verificacao.setCodigoHash(passwordEncoder.encode(codigo));
        verificacao.setExpiraEm(agora.plus(validade()));
        verificacao.setTentativas(0);
        verificacao.setEnvios(verificacao.getEnvios() + 1);
        verificacao.setUltimoEnvioEm(agora);
        verificacaoRepository.save(verificacao);

        eventos.publishEvent(new CodigoLoginGerado(usuario.getId(), destinatario.nome(), destinatario.email(), codigo));
        return resposta(verificacao, agora);
    }

    private Optional<DestinatarioEmail> destinatario(Usuario usuario) {
        return DestinatarioEmail.doUsuario(usuario, pessoaRepository);
    }

    private String gerarCodigo() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    private Duration validade() {
        return Duration.ofMinutes(properties.validadeMinutos());
    }

    private long segundosAteReenvio(VerificacaoLogin verificacao, Instant agora) {
        Instant liberadoEm = verificacao.getUltimoEnvioEm().plusSeconds(properties.reenvioIntervaloSegundos());
        return Math.max(0, Duration.between(agora, liberadoEm).toSeconds());
    }

    private VerificacaoLoginResponse resposta(VerificacaoLogin verificacao, Instant agora) {
        return new VerificacaoLoginResponse(
                verificacao.getId(),
                Math.max(0, Duration.between(agora, verificacao.getExpiraEm()).toSeconds()),
                segundosAteReenvio(verificacao, agora));
    }
}
