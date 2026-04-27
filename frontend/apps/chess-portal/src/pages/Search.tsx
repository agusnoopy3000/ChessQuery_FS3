import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Input, Card, Skeleton, EmptyState, ErrorAlert, PlayerCard } from '@chessquery/ui-lib';
import { Player } from '@chessquery/shared';
import { playerApi } from '../api';

const unwrap = <T,>(data: unknown): T[] => {
  if (Array.isArray(data)) return data as T[];
  const maybePage = data as { content?: T[] } | null | undefined;
  return maybePage?.content ?? [];
};

export const SearchPage = () => {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const initialQ = params.get('q') ?? '';
  const [input, setInput] = useState(initialQ);
  const [debounced, setDebounced] = useState(initialQ);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(input.trim()), 300);
    return () => clearTimeout(t);
  }, [input]);

  useEffect(() => {
    if (debounced) setParams({ q: debounced }, { replace: true });
    else setParams({}, { replace: true });
  }, [debounced, setParams]);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['search', debounced],
    queryFn: () => playerApi.search(debounced),
    enabled: debounced.length >= 2,
  });

  const results = unwrap<Player>(data);

  return (
    <div style={{ padding: 28, maxWidth: 900, margin: '0 auto' }}>
      <h1 style={{ fontSize: 22, marginBottom: 16 }}>Buscar jugadores</h1>
      <div style={{ marginBottom: 20 }}>
        <Input
          placeholder="Nombre, apellido o ID FIDE…"
          value={input}
          autoFocus
          onChange={(e) => setInput(e.target.value)}
          leftIcon={<span>🔍</span>}
        />
        <div style={{ fontSize: 11, color: 'var(--text-dim)', marginTop: 6 }}>
          Mínimo 2 caracteres · debounce 300ms
        </div>
      </div>

      {debounced.length < 2 ? (
        <Card>
          <EmptyState title="Escribe para buscar" icon="🔍" description="Al menos 2 caracteres" />
        </Card>
      ) : isLoading ? (
        <div style={{ display: 'grid', gap: 10 }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} height={72} />
          ))}
        </div>
      ) : isError ? (
        <ErrorAlert message="No se pudo realizar la búsqueda" onRetry={() => refetch()} />
      ) : results.length === 0 ? (
        <Card>
          <EmptyState title="Sin resultados" description={`No hay coincidencias para “${debounced}”`} />
        </Card>
      ) : (
        <div style={{ display: 'grid', gap: 10 }}>
          {results.map((p) => (
            <PlayerCard key={p.id} player={p} onClick={() => navigate(`/player/${p.id}`)} />
          ))}
        </div>
      )}
    </div>
  );
};
