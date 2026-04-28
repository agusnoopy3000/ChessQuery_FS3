import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Button, Card, ErrorAlert, RatingBadge } from '@chessquery/ui-lib';
import { Player } from '@chessquery/shared';
import { playerApi, gameApi } from '../api';
import { buildPlayerName, getPrimaryRating } from '../portal-utils';

type Color = 'white' | 'black';

interface MatchPayload {
  you: Player;
  opponent: Player;
  yourColor: Color;
  opponentColor: Color;
}

type Result = '1-0' | '0-1' | '1/2-1/2';

export const PlayerMatchmakingPage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [match, setMatch] = useState<MatchPayload | null>(null);
  const [submitted, setSubmitted] = useState<{ result: Result; eloDelta: number } | null>(null);

  const findMatch = useMutation({
    mutationFn: () => playerApi.findMatch(),
    onSuccess: (data: MatchPayload) => {
      setMatch(data);
      setSubmitted(null);
    },
  });

  const submitGame = useMutation({
    mutationFn: (result: Result) => {
      if (!match) throw new Error('No hay partida activa');
      return gameApi.create({
        whitePlayerId: match.you.id,
        blackPlayerId: match.opponent.id,
        result,
        gameType: 'CASUAL',
        whiteEloBefore: getPrimaryRating(match.you) ?? 1500,
        blackEloBefore: getPrimaryRating(match.opponent) ?? 1500,
        totalMoves: 30,
        durationSeconds: 600,
        pgnContent: `[Event "ChessQuery Casual"]\n[White "${buildPlayerName(match.you)}"]\n[Black "${buildPlayerName(match.opponent)}"]\n[Result "${result}"]\n\n${result}`,
      });
    },
    onSuccess: (game: { whiteEloBefore: number; whiteEloAfter: number }, result: Result) => {
      setSubmitted({ result, eloDelta: game.whiteEloAfter - game.whiteEloBefore });
      queryClient.invalidateQueries({ queryKey: ['me'] });
      queryClient.invalidateQueries({ queryKey: ['player'] });
    },
  });

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20, maxWidth: 920, margin: '0 auto' }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">Portal de Juego</div>
          <h1 className="page-title">Buscar partida</h1>
          <p className="page-copy">
            ChessQuery te empareja con otro jugador de la plataforma de rating cercano.
            La partida queda registrada en ChessQuery y tu ELO se recalcula al reportar
            el resultado, independiente de si tienes cuenta de Lichess.
          </p>
        </div>
      </section>

      {!match ? (
        <Card>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, padding: '32px 16px' }}>
            <div style={{ fontSize: 56 }}>♞</div>
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', maxWidth: 420 }}>
              Pulsa el botón y te asignaremos un rival al azar entre los jugadores
              registrados, priorizando una diferencia de ELO de hasta 400 puntos.
            </div>
            <Button
              size="lg"
              onClick={() => findMatch.mutate()}
              loading={findMatch.isPending}
            >
              Buscar partida
            </Button>
            {findMatch.isError ? (
              <ErrorAlert
                title="No se pudo buscar partida"
                message={
                  (findMatch.error as { response?: { data?: { message?: string } }; message?: string })
                    ?.response?.data?.message ??
                  (findMatch.error as { message?: string })?.message ??
                  'Error desconocido'
                }
              />
            ) : null}
          </div>
        </Card>
      ) : (
        <>
          <Card header="Partida encontrada">
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr auto 1fr',
                gap: 18,
                alignItems: 'center',
                padding: 12,
              }}
            >
              <PlayerCell label="Blancas (tú)" player={match.you} />
              <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--text-muted)' }}>vs</div>
              <PlayerCell label="Negras" player={match.opponent} align="end" />
            </div>
          </Card>

          {submitted ? (
            <Card>
              <div style={{ padding: 22, textAlign: 'center' }}>
                <div style={{ fontSize: 38, fontWeight: 700 }}>{submitted.result}</div>
                <div style={{ marginTop: 8, color: 'var(--text-muted)' }}>
                  Tu ELO cambió en{' '}
                  <strong style={{ color: submitted.eloDelta >= 0 ? 'var(--accent)' : 'var(--red)' }}>
                    {submitted.eloDelta >= 0 ? '+' : ''}{submitted.eloDelta}
                  </strong>{' '}
                  puntos.
                </div>
                <div style={{ marginTop: 18, display: 'flex', gap: 10, justifyContent: 'center' }}>
                  <Button variant="secondary" onClick={() => { setMatch(null); setSubmitted(null); }}>
                    Buscar otra partida
                  </Button>
                  <Button onClick={() => navigate('/me')}>Ir a mi dashboard</Button>
                </div>
              </div>
            </Card>
          ) : (
            <Card header="Reportar resultado">
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 12 }}>
                <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                  Una vez terminada la partida, marca el resultado. ChessQuery recalculará
                  el ELO usando la fórmula FIDE.
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
                  <Button
                    onClick={() => submitGame.mutate('1-0')}
                    loading={submitGame.isPending && submitGame.variables === '1-0'}
                    disabled={submitGame.isPending}
                  >
                    Gané (1-0)
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => submitGame.mutate('1/2-1/2')}
                    loading={submitGame.isPending && submitGame.variables === '1/2-1/2'}
                    disabled={submitGame.isPending}
                  >
                    Tablas (½-½)
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => submitGame.mutate('0-1')}
                    loading={submitGame.isPending && submitGame.variables === '0-1'}
                    disabled={submitGame.isPending}
                  >
                    Perdí (0-1)
                  </Button>
                </div>
                {submitGame.isError ? (
                  <ErrorAlert
                    title="No se pudo registrar la partida"
                    message={
                      (submitGame.error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                      (submitGame.error as { message?: string })?.message ??
                      'Error desconocido'
                    }
                  />
                ) : null}
                <Button variant="ghost" onClick={() => setMatch(null)}>
                  Cancelar
                </Button>
              </div>
            </Card>
          )}
        </>
      )}
    </div>
  );
};

const PlayerCell = ({
  label,
  player,
  align = 'start',
}: {
  label: string;
  player: Player;
  align?: 'start' | 'end';
}) => {
  const rating = getPrimaryRating(player);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: align === 'end' ? 'flex-end' : 'flex-start', gap: 6 }}>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.5, fontWeight: 600 }}>
        {label}
      </div>
      <div style={{ fontSize: 22, fontWeight: 700 }}>{buildPlayerName(player)}</div>
      {rating != null ? <RatingBadge rating={rating} label="ELO" /> : <span style={{ color: 'var(--text-muted)' }}>Sin rating</span>}
    </div>
  );
};
