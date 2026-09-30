import React, { useState } from 'react';

export default function Turmas() {
  const [turmas, setTurmas] = useState([
    { id: 1, codigoTurma: 'TURMA-A1', disciplina: 'Trabalho Interdisciplinar III', periodo: '2026/2', vagas: 40, horario: 'Ter/Qui 19:00 - 20:40' }
  ]);

  const [showModal, setShowModal] = useState(false);
  const [novaTurma, setNovaTurma] = useState({ codigoTurma: '', disciplina: '', periodo: '', vagas: '', horario: '' });

  const handleCreate = (e) => {
    e.preventDefault();
    if (!novaTurma.codigoTurma || !novaTurma.disciplina) return;

    setTurmas([...turmas, { ...novaTurma, id: Date.now() }]);
    setNovaTurma({ codigoTurma: '', disciplina: '', periodo: '', vagas: '', horario: '' });
    setShowModal(false);
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2>Turmas</h2>
        <button onClick={() => setShowModal(true)} style={btnPrimaryStyle}>+ Nova Turma</button>
      </div>

      <table style={tableStyle}>
        <thead>
          <tr>
            <th style={thStyle}>Código da Turma</th>
            <th style={thStyle}>Disciplina</th>
            <th style={thStyle}>Período Letivo</th>
            <th style={thStyle}>Vagas</th>
            <th style={thStyle}>Horário</th>
          </tr>
        </thead>
        <tbody>
          {turmas.map((t) => (
            <tr key={t.id}>
              <td style={tdStyle}>{t.codigoTurma}</td>
              <td style={tdStyle}>{t.disciplina}</td>
              <td style={tdStyle}>{t.periodo}</td>
              <td style={tdStyle}>{t.vagas}</td>
              <td style={tdStyle}>{t.horario}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {showModal && (
        <div style={modalOverlayStyle}>
          <div style={modalContentStyle}>
            <h3>Criar Nova Turma</h3>
            <form onSubmit={handleCreate}>
              <div style={formGroupStyle}>
                <label>Código/Nome da Turma:</label>
                <input
                  type="text"
                  value={novaTurma.codigoTurma}
                  onChange={(e) => setNovaTurma({ ...novaTurma, codigoTurma: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Disciplina:</label>
                <input
                  type="text"
                  value={novaTurma.disciplina}
                  onChange={(e) => setNovaTurma({ ...novaTurma, disciplina: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Período Letivo:</label>
                <input
                  type="text"
                  value={novaTurma.periodo}
                  onChange={(e) => setNovaTurma({ ...novaTurma, periodo: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Quantidade de Vagas:</label>
                <input
                  type="number"
                  value={novaTurma.vagas}
                  onChange={(e) => setNovaTurma({ ...novaTurma, vagas: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Horário / Dias:</label>
                <input
                  type="text"
                  value={novaTurma.horario}
                  onChange={(e) => setNovaTurma({ ...novaTurma, horario: e.target.value })}
                  placeholder="Ex: Seg/Qua 08:00 - 09:40"
                  style={inputStyle}
                  required
                />
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

/* Estilos Compartilhados */
export const btnPrimaryStyle = {
  backgroundColor: '#0056b3',
  color: '#fff',
  border: 'none',
  padding: '8px 16px',
  borderRadius: '4px',
  cursor: 'pointer'
};

export const btnSecondaryStyle = {
  backgroundColor: '#6c757d',
  color: '#fff',
  border: 'none',
  padding: '8px 16px',
  borderRadius: '4px',
  cursor: 'pointer'
};

export const tableStyle = {
  width: '100%',
  borderCollapse: 'collapse',
  marginTop: '8px'
};

export const thStyle = {
  borderBottom: '2px solid #ddd',
  textAlign: 'left',
  padding: '12px 8px',
  backgroundColor: '#f8f9fa'
};

export const tdStyle = {
  borderBottom: '1px solid #ddd',
  padding: '10px 8px'
};

export const modalOverlayStyle = {
  position: 'fixed',
  top: 0,
  left: 0,
  right: 0,
  bottom: 0,
  backgroundColor: 'rgba(0,0,0,0.5)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 1000
};

export const modalContentStyle = {
  backgroundColor: '#fff',
  padding: '24px',
  borderRadius: '8px',
  width: '400px',
  maxWidth: '90%'
};

export const formGroupStyle = {
  marginBottom: '12px',
  display: 'flex',
  flexDirection: 'column',
  gap: '4px'
};

export const inputStyle = {
  padding: '8px',
  borderRadius: '4px',
  border: '1px solid #ccc'
};

