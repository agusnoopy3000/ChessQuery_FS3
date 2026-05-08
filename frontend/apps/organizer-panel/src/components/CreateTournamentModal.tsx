import { FormEvent, useState } from 'react';
import { Button } from '@chessquery/ui-lib';
import type { CreateTournamentInput } from '../api';

interface Props {
  open: boolean;
  onClose: () => void;
  onSubmit: (input: CreateTournamentInput) => void;
  loading?: boolean;
  error?: string | null;
}

const FORMATS: Array<CreateTournamentInput['format']> = ['SWISS', 'ROUND_ROBIN', 'KNOCKOUT'];
const FORMAT_LABEL: Record<CreateTournamentInput['format'], string> = {
  SWISS: 'Suizo',
  ROUND_ROBIN: 'Todos contra todos',
  KNOCKOUT: 'Eliminación directa',
};

export const CreateTournamentModal = ({ open, onClose, onSubmit, loading, error }: Props) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [format, setFormat] = useState<CreateTournamentInput['format']>('SWISS');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [location, setLocation] = useState('');
  const [maxPlayers, setMaxPlayers] = useState('16');
  const [roundsTotal, setRoundsTotal] = useState('5');
  const [minElo, setMinElo] = useState('');
  const [maxElo, setMaxElo] = useState('');
  const [timeControl, setTimeControl] = useState('');
  const [requiresApproval, setRequiresApproval] = useState(true);
  const [validation, setValidation] = useState<string | null>(null);

  if (!open) return null;

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setValidation(null);
    if (!name.trim()) {
      setValidation('El nombre es obligatorio');
      return;
    }
    const max = maxPlayers ? Number(maxPlayers) : undefined;
    if (max != null && (Number.isNaN(max) || max < 2)) {
      setValidation('Máximo de jugadores debe ser ≥ 2');
      return;
    }
    const minE = minElo ? Number(minElo) : undefined;
    const maxE = maxElo ? Number(maxElo) : undefined;
    if (minE != null && maxE != null && minE > maxE) {
      setValidation('ELO mínimo no puede superar al máximo');
      return;
    }
    if (startDate && endDate && startDate > endDate) {
      setValidation('La fecha de fin no puede ser anterior al inicio');
      return;
    }

    onSubmit({
      name: name.trim(),
      description: description.trim() || undefined,
      format,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      location: location.trim() || undefined,
      maxPlayers: max,
      roundsTotal: roundsTotal ? Number(roundsTotal) : undefined,
      minElo: minE,
      maxElo: maxE,
      timeControl: timeControl.trim() || undefined,
      requiresApproval,
    });
  };

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', zIndex: 1000,
        display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--surface, #1a1c19)',
          border: '1px solid var(--border, #2a2d27)',
          borderRadius: 12, padding: 24, width: '100%', maxWidth: 560,
          maxHeight: '90vh', overflowY: 'auto',
        }}
      >
        <h2 style={{ margin: 0, marginBottom: 4, fontSize: 22 }}>Nuevo torneo</h2>
        <p style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 0, marginBottom: 18 }}>
          Los jugadores podrán inscribirse desde su portal cuando el torneo pase a "Inscripciones abiertas".
        </p>

        <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 14 }}>
          <Field label="Nombre *">
            <input value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </Field>
          <Field label="Descripción">
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="input"
              style={{ resize: 'vertical', fontFamily: 'inherit' }}
            />
          </Field>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Formato *">
              <select
                value={format}
                onChange={(e) => setFormat(e.target.value as CreateTournamentInput['format'])}
                className="input"
              >
                {FORMATS.map((f) => (
                  <option key={f} value={f}>{FORMAT_LABEL[f]}</option>
                ))}
              </select>
            </Field>
            <Field label="Sede">
              <input value={location} onChange={(e) => setLocation(e.target.value)} className="input" placeholder="Club o ciudad" />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Inicio">
              <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="input" />
            </Field>
            <Field label="Fin">
              <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} className="input" />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Máx. jugadores">
              <input type="number" min={2} value={maxPlayers} onChange={(e) => setMaxPlayers(e.target.value)} className="input" />
            </Field>
            <Field label="Total de rondas">
              <input type="number" min={1} value={roundsTotal} onChange={(e) => setRoundsTotal(e.target.value)} className="input" />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <Field label="ELO mín.">
              <input type="number" value={minElo} onChange={(e) => setMinElo(e.target.value)} className="input" />
            </Field>
            <Field label="ELO máx.">
              <input type="number" value={maxElo} onChange={(e) => setMaxElo(e.target.value)} className="input" />
            </Field>
            <Field label="Cadencia">
              <input value={timeControl} onChange={(e) => setTimeControl(e.target.value)} className="input" placeholder="90+30" />
            </Field>
          </div>

          <label style={{ display: 'flex', gap: 10, alignItems: 'center', cursor: 'pointer', fontSize: 14 }}>
            <input
              type="checkbox"
              checked={requiresApproval}
              onChange={(e) => setRequiresApproval(e.target.checked)}
              style={{ width: 18, height: 18 }}
            />
            <span>
              Requiere aprobación del organizador
              <span style={{ display: 'block', color: 'var(--text-muted)', fontSize: 12 }}>
                Si se desactiva, los jugadores quedan confirmados al inscribirse.
              </span>
            </span>
          </label>

          {(validation || error) && (
            <div style={{
              background: 'rgba(224,90,90,0.1)', border: '1px solid #e05a5a',
              borderRadius: 8, padding: '10px 14px', color: '#e05a5a', fontSize: 13,
            }}>
              {validation || error}
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 8 }}>
            <Button type="button" variant="ghost" onClick={onClose} disabled={loading}>
              Cancelar
            </Button>
            <Button type="submit" variant="primary" loading={loading}>
              Crear torneo
            </Button>
          </div>
        </form>

        <style>{`
          .input {
            width: 100%;
            background: #0e100d;
            border: 1px solid #2a2d27;
            border-radius: 8px;
            padding: 9px 12px;
            color: #e8ead4;
            font-size: 14px;
            font-family: inherit;
            outline: none;
          }
          .input:focus { border-color: #4a7c59; box-shadow: 0 0 0 3px rgba(74,124,89,0.15); }
        `}</style>
      </div>
    </div>
  );
};

const Field = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
    <label style={{
      fontSize: 11, letterSpacing: '0.1em', textTransform: 'uppercase',
      color: '#7a7d6e', fontFamily: 'Space Mono, monospace',
    }}>{label}</label>
    {children}
  </div>
);
