package br.pucminas.eduka.backend.dto.comum;

public record EnderecoRequest(
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String estado,
        String cep) {
}
