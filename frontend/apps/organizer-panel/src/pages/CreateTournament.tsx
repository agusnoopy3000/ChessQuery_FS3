import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Card, Button, Input, Select, ErrorAlert } from '@chessquery/ui-lib';
import { Tournament } from '@chessquery/shared';
import { organizerApi } from '../api';

export const CreateTournamentPage = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: '',
    format: 'SWISS' as Tournament['format'],
    startDate: '',
    endDate: '',
    location: '',
    maxPlayers: 32,
    rounds: 7,
    minElo: '',
    maxElo: '',
    timeControl: '90+30',
  });
  const [error, setError] = useState<string | null>(null);

  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const mutation = useMutation({
    mutationFn: (input: Partial<Tournament>) => organizerApi.createTournament(input),
    onSuccess: (t: Tournament) => navigate(`/tournaments/${t.id}`),
    onError: (err) => {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'No se pudo crear el torneo');
    },
  });

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    mutation.mutate({
      name: form.name,
      format: form.format,
      startDate: form.startDate,
      endDate: form.endDate || null,
      location: form.location || null,
      maxPlayers: Number(form.maxPlayers),
      rounds: Number(form.rounds),
      minElo: form.minElo ? Number(form.minElo) : null,
      maxElo: form.maxElo ? Number(form.maxElo) : null,
      timeControl: form.timeControl || null,
    });
  };

  return (
    <div style={{ padding: 28, maxWidth: 720, margin: '0 auto' }}>
      <button className="btn btn-ghost" onClick={() => navigate('/')} style={{ marginBottom: 12 }}>
        ← Volver
      </button>
      <h1 style={{ fontSize: 22, marginBottom: 16 }}>Crear nuevo torneo</h1>

      <Card>
        <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {error && <ErrorAlert message={error} />}
          <Input label="Nombre" required value={form.name} onChange={(e) => set('name', e.target.value)} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Select
              label="Formato"
              value={form.format}
              onChange={(e) => set('format', e.target.value as Tournament['format'])}
              options={[
                { value: 'SWISS', label: 'Suizo' },
                { value: 'ROUND_ROBIN', label: 'Todos contra todos' },
                { value: 'KNOCKOUT', label: 'Eliminación' },
              ]}
            />
            <Input
              label="Control de tiempo"
              value={form.timeControl}
              onChange={(e) => set('timeControl', e.target.value)}
              placeholder="90+30"
            />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Input label="Fecha inicio" type="date" required value={form.startDate} onChange={(e) => set('startDate', e.target.value)} />
            <Input label="Fecha fin" type="date" value={form.endDate} onChange={(e) => set('endDate', e.target.value)} />
          </div>
          <Input label="Ubicación" value={form.location} onChange={(e) => set('location', e.target.value)} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Input
              label="Cupos"
              type="number"
              min={2}
              value={form.maxPlayers}
              onChange={(e) => set('maxPlayers', Number(e.target.value))}
            />
            <Input
              label="Rondas"
              type="number"
              min={1}
              value={form.rounds}
              onChange={(e) => set('rounds', Number(e.target.value))}
            />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Input label="ELO mínimo" type="number" value={form.minElo} onChange={(e) => set('minElo', e.target.value)} />
            <Input label="ELO máximo" type="number" value={form.maxElo} onChange={(e) => set('maxElo', e.target.value)} />
          </div>
          <Button type="submit" loading={mutation.isPending} size="lg" fullWidth>
            Crear torneo →
          </Button>
        </form>
      </Card>
    </div>
  );
};
