import React, { useState } from 'react';

export default function Cursos() {
  const [cursos, setCursos] = useState([
    { id: 1, codigo: 'ENG-SOFT', nome: 'Engenharia de Software', duracaoSemestres: 8, nivel: 'Graduação' },
    { id: 2, codigo: 'CIE-COMP', nome: 'Ciência da Computação', duracaoSemestres: 8, nivel: 'Graduação' }
  ]);

  const [showModal, setShowModal] = useState(false);
  const [novoCurso, setNovoCurso] = useState({ codigo: '', nome: '', duracaoSemestres: '', nivel: 'Graduação' });

  const handleCreate = (e) => {
    e.preventDefault();
    if (!novoCurso.codigo || !novoCurso.nome) return;

    setCursos([...cursos, { ...novoCurso, id: Date.now() }]);
    setNovoCurso({ codigo: '', nome: '', duracaoSemestres: '', nivel: 'Graduação' });
    setShowModal(false);
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2>Cursos</h2>
        <button onClick={() => setShowModal(true)} style={btnPrimaryStyle}>+ Novo Curso</button>
      </div>

      <table style={tableStyle}>
        <thead>
          <tr>
            <th style={thStyle}>Código</th>
            <th style={thStyle}>Nome do Curso</th>
            <th style={thStyle}>Duração (Semestres)</th>
            <th style={thStyle}>Nível</th>
          </tr>
        </thead>
        <tbody>
          {cursos.map((c) => (
            <tr key={c.id}>
              <td style={tdStyle}>{c.codigo}</td>
              <td style={tdStyle}>{c.nome}</td>
              <td style={tdStyle}>{c.duracaoSemestres}</td>
              <td style={tdStyle}>{c.nivel}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {showModal && (
        <div style={modalOverlayStyle}>
          <div style={modalContentStyle}>
            <h3>Criar Novo Curso</h3>
            <form onSubmit={handleCreate}>
              <div style={formGroupStyle}>
                <label>Código do Curso:</label>
                <input
                  type="text"
                  value={novoCurso.codigo}
                  onChange={(e) => setNovoCurso({ ...novoCurso, codigo: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Nome do Curso:</label>
                <input
                  type="text"
                  value={novoCurso.nome}
                  onChange={(e) => setNovoCurso({ ...novoCurso, nome: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Duração (Semestres):</label>
                <input
                  type="number"
                  value={novoCurso.duracaoSemestres}
                  onChange={(e) => setNovoCurso({ ...novoCurso, duracaoSemestres: e.target.value })}
                  style={inputStyle}
                  required
                />
              </div>
              <div style={formGroupStyle}>
                <label>Nível:</label>
                <select
                  value={novoCurso.nivel}
                  onChange={(e) => setNovoCurso({ ...novoCurso, nivel: e.target.value })}
                  style={inputStyle}
                >
                  <option value="Graduação">Graduação</option>
                  <option value="Pós-Graduação">Pós-Graduação</option>
                  <option value="Técnico">Técnico</option>
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

