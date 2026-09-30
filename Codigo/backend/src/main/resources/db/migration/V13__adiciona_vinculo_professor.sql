-- ---------------------------------------------------------------------------
-- Vinculo empregaticio do professor com a escola (regime e carga horaria).
-- ---------------------------------------------------------------------------
-- Nulo permitido: professores cadastrados antes desta migration nao tem
-- vinculo definido. Os valores precisam bater com o enum VinculoProfessor.
ALTER TABLE PROFESSOR ADD COLUMN VINCULO TEXT CHECK (VINCULO IN (
    'CLT_40H', 'CLT_30H', 'CLT_20H', 'HORISTA', 'TEMPORARIO', 'PRESTADOR_SERVICO'));
