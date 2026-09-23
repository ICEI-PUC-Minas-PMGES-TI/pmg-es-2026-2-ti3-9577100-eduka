-- Nulo permitido: escolas cadastradas antes desta migration nao tem plano
-- definido. O cadastro pela API exige o plano (EscolaService).
ALTER TABLE ESCOLA ADD COLUMN PLANO TEXT CHECK (PLANO IN ('BASICO', 'AVANCADO', 'ENTERPRISE'));
