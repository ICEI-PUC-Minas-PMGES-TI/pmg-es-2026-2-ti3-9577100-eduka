import React, { useState } from 'react';

export default function PeriodosLetivos() {
  const [periodos, setPeriodos] = useState([
    { id: 1, nome: '2026/1', dataInicio: '2026-02-01', dataFim: '2026-06-30', status: 'Ativo' },
    { id: 2, nome: '2026/2', dataInicio: '2026-08-01', dataFim: '2026-12-15', status: 'Planejado' }
  ]);

  const [showModal, setShowModal] = useState(false);
  const [novoPeriodo, setNovoPeriodo] = useState({ nome: '', dataInicio: '', dataFim: '', status: 'Planejado' });

  const handleCreate = (e) => {
    e.preventDefault();
    if (!novoPeriodo.nome || !novoPeriodo.dataInicio || !novoPeriodo.dataFim) return;

    setPeriodos([...periodos, { ...novoPeriodo, id: Date.now() }]);
    setNovoPeriodo({ nome: '', dataInicio: '', dataFim: '', status: 'Planejado' });
    setShowModal(false);
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2>Períodos Letivos</h2>
        <button onClick={() => setShowModal(true)} style={btnPrimaryStyle}>+ Novo Período</button>
      </div>

      {/* Tabela de Listagem */}
      <table style={tableStyle}>
        <thead>
          <tr>
            <th style={thStyle}>Nome</th>
            <th style={thStyle}>Data de Início</th>
            <th style={thStyle}>Data de Término</th>
            <th style={thStyle}>Status</th>
          </tr>
        </thead>
        <tbody>
          {periodos.map((p) => (
            <tr key={p.id}>
              <td style={tdStyle}>{p.nome}</td>
              <td style={tdStyle}>{p.dataInicio}</td>
              <td style={tdStyle}>{p.dataFim}</td>
              <td style={tdStyle}>{p.status}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Modal de Cadastro */}
      {showModal && (
        <div style={modalOverlayStyle}>
          <div style={modalContentStyle}>
            <h3>Criar Período Letivo</h3>
            <form onSubmit={handleCreate}>
              <div style={formGroupStyle}>
                <label>Nome do Período (ex: 2026/1):</label>
                <input
                  type="text"
                  value={novoPeriodo.nome}
                  onChange={(e) => setNovoPeriodo({ ...novoPeriodo, nome: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Data de Início:</label>
                <input
                  type="date"
                  value={novoPeriodo.dataInicio}
                  onChange={(e) => setNovoPeriodo({ ...novoPeriodo, dataInicio: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Data de Término:</label>
                <input
                  type="date"
                  value={novoPeriodo.dataFim}
                  onChange={(e) => setNovoPeriodo({ ...novoPeriodo, dataFim: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Status:</label>
                <select
                  value={novoPeriodo.status}
                  onChange={(e) => setNovoPeriodo({ ...novoPeriodo, status: e.target.value })}
                  style={inputStyle}
                >
                  <option value="Planejado">Planejado</option>
                  <option value="Ativo">Ativo</option>
                  <option value="Encerrado">Encerrado</option>
                </select>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" onClick={() => setShowModal(false)} style={btnSecondaryStyle}>Cancelar</button>
                <button type="submit" style={btnPrimaryStyle}>Salvar</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

