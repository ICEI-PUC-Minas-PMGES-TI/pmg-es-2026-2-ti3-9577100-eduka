import React, { useState } from 'react';

export default function Disciplinas() {
  const [disciplinas, setDisciplinas] = useState([
    { id: 1, codigo: 'TI301', nome: 'Trabalho Interdisciplinar III', cargaHoraria: 60, curso: 'Engenharia de Software' },
    { id: 2, codigo: 'ALGO1', nome: 'Algoritmos e Estrutura de Dados', cargaHoraria: 80, curso: 'Ciência da Computação' }
  ]);

  const [showModal, setShowModal] = useState(false);
  const [novaDisciplina, setNovaDisciplina] = useState({ codigo: '', nome: '', cargaHoraria: '', curso: '' });

  const handleCreate = (e) => {
    e.preventDefault();
    if (!novaDisciplina.codigo || !novaDisciplina.nome) return;

    setDisciplinas([...disciplinas, { ...novaDisciplina, id: Date.now() }]);
    setNovaDisciplina({ codigo: '', nome: '', cargaHoraria: '', curso: '' });
    setShowModal(false);
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2>Disciplinas</h2>
        <button onClick={() => setShowModal(true)} style={btnPrimaryStyle}>+ Nova Disciplina</button>
      </div>

      <table style={tableStyle}>
        <thead>
          <tr>
            <th style={thStyle}>Código</th>
            <th style={thStyle}>Nome</th>
            <th style={thStyle}>Carga Horária (h)</th>
            <th style={thStyle}>Curso Vinculado</th>
          </tr>
        </thead>
        <tbody>
          {disciplinas.map((d) => (
            <tr key={d.id}>
              <td style={tdStyle}>{d.codigo}</td>
              <td style={tdStyle}>{d.nome}</td>
              <td style={tdStyle}>{d.cargaHoraria}</td>
              <td style={tdStyle}>{d.curso}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {showModal && (
        <div style={modalOverlayStyle}>
          <div style={modalContentStyle}>
            <h3>Criar Disciplina</h3>
            <form onSubmit={handleCreate}>
              <div style={formGroupStyle}>
                <label>Código:</label>
                <input
                  type="text"
                  value={novaDisciplina.codigo}
                  onChange={(e) => setNovaDisciplina({ ...novaDisciplina, codigo: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Nome da Disciplina:</label>
                <input
                  type="text"
                  value={novaDisciplina.nome}
                  onChange={(e) => setNovaDisciplina({ ...novaDisciplina, nome: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Carga Horária (Horas):</label>
                <input
                  type="number"
                  value={novaDisciplina.cargaHoraria}
                  onChange={(e) => setNovaDisciplina({ ...novaDisciplina, cargaHoraria: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Curso Vinculado:</label>
                <input
                  type="text"
                  value={novaDisciplina.curso}
                  onChange={(e) => setNovaDisciplina({ ...novaDisciplina, curso: e.target.value })}
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