package br.pucminas.eduka.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.pucminas.eduka.backend.domain.Usuario;
import br.pucminas.eduka.backend.dto.LoginResponse;
import br.pucminas.eduka.backend.repository.UsuarioRepository;
import br.pucminas.eduka.backend.security.JwtService;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String login, String senha) {
        Usuario usuario = usuarioRepository.findByLogin(login)
                .filter(candidato -> passwordEncoder.matches(senha, candidato.getSenhaHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciais inválidas"));

        if (!usuario.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "usuário inativo");
        }

        String accessToken = jwtService.generateAccessToken(usuario.getId(), usuario.getPerfil(),
                usuario.getEscolaId());
        String refreshToken = jwtService.generateRefreshToken(usuario.getId());
        return new LoginResponse(accessToken, refreshToken);
    }
}
