package br.pucminas.eduka.backend.domains.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alunos")
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aluno")
    private Long id;

    @Column(name = "nome_aluno")
    private String nomeAluno;

    @Column(name = "numero_matricula")
    private String matriculaAluno;

    @Column(name = "email_responsavel")
    private String emailAluno;

    @Column(name = "telefone_responsavel")
    private String telefoneAluno;

    @Column(name = "genero_aluno")
    private String generoAluno;

    @Column(name = "criado_em")
    private LocalDateTime alunoCriadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime alunoAtualizadoEm;

    public Aluno() {

    }

}