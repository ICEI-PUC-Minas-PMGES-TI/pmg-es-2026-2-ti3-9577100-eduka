-- Login do aluno, criado junto com o cadastro (ver AlunoService). Nulo nos
-- alunos anteriores a esta migration, que ainda nao tem acesso.
ALTER TABLE alunos
    ADD COLUMN usuario_id UUID UNIQUE REFERENCES USUARIO(ID_USUARIO);
