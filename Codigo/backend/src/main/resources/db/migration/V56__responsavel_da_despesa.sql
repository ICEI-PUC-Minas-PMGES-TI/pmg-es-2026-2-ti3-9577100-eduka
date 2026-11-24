-- ---------------------------------------------------------------------------
-- Responsavel pela despesa (RF18): nome em texto livre, sem ligacao com os
-- usuarios do sistema (ex.: "Joao da manutencao", "Contabilidade").
-- ---------------------------------------------------------------------------
-- A aplicacao exige o responsavel ao lancar e ao editar (DespesaRequest e
-- AtualizarDespesaRequest). A coluna aceita nulo porque as despesas lancadas
-- antes desta versao nao tem responsavel.
ALTER TABLE DESPESA ADD COLUMN RESPONSAVEL VARCHAR(120);
