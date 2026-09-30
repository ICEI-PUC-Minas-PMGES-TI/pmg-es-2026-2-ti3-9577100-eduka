import React, { useState } from 'react';
import PeriodosLetivos from './PeriodosLetivos';
import Cursos from './Cursos';
import Disciplinas from './Disciplinas';
import Turmas from './Turmas';

export default function SecretariaDashboard() {
  const [activeTab, setActiveTab] = useState('periodos');

  return (
    <div style={{ padding: '24px', fontFamily: 'Arial, sans-serif' }}>
      <h1>Painel da Secretaria</h1>
      <p style={{ color: '#666' }}>Gerenciamento acadêmico da instituição</p>

      {/* Navegação por Abas */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '24px', borderBottom: '2px solid #eee' }}>
        <button
          onClick={() => setActiveTab('periodos')}
          style={tabButtonStyle(activeTab === 'periodos')}
        >
          Períodos Letivos
        </button>
        <button
          onClick={() => setActiveTab('cursos')}
          style={tabButtonStyle(activeTab === 'cursos')}
        >
          Cursos
        </button>
        <button
          onClick={() => setActiveTab('disciplinas')}
          style={tabButtonStyle(activeTab === 'disciplinas')}
        >
          Disciplinas
        </button>
        <button
          onClick={() => setActiveTab('turmas')}
          style={tabButtonStyle(activeTab === 'turmas')}
        >
          Turmas
        </button>
      </div>

      {/* Conteúdo da Aba Selecionada */}
      {activeTab === 'periodos' && <PeriodosLetivos />}
      {activeTab === 'cursos' && <Cursos />}
      {activeTab === 'disciplinas' && <Disciplinas />}
      {activeTab === 'turmas' && <Turmas />}
    </div>
  );
}

const tabButtonStyle = (isActive) => ({
  padding: '10px 20px',
  border: 'none',
  borderBottom: isActive ? '3px solid #0056b3' : '3px solid transparent',
  backgroundColor: 'transparent',
  fontWeight: isActive ? 'bold' : 'normal',
  color: isActive ? '#0056b3' : '#555',
  cursor: 'pointer',
  fontSize: '16px'
});

