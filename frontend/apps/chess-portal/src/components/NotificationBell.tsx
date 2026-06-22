import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { sileo } from 'sileo';
import { playerApi, NotificationItem } from '../api';

const POLL_MS = 8_000;

// Baseline de sesión: id máximo de notificaciones al iniciar la sesión. Solo se
// muestran/cuentan las posteriores. Vive en sessionStorage → al cerrar la
// pestaña (sesión nueva) la bandeja arranca vacía; dentro de la misma sesión se
// acumulan. Se limpia también en el logout. Ver NotificationBell.
const SESSION_BASELINE_KEY = 'cq-notif-baseline';
const readSessionBaseline = (): number | null => {
  try {
    const v = sessionStorage.getItem(SESSION_BASELINE_KEY);
    return v != null ? Number(v) : null;
  } catch {
    return null;
  }
};

const eventIcon = (eventType: string): string => {
  if (eventType === 'game.invitation') return '⚔️';
  if (eventType.startsWith('game.')) return '♟';
  if (eventType.startsWith('tournament.')) return '🏆';
  if (eventType.startsWith('player.')) return '✅';
  if (eventType.startsWith('registration.')) return '📋';
  if (eventType === 'elo.updated') return '📈';
  if (eventType === 'user.registered') return '👋';
  return '🔔';
};

/** Si la notificación es accionable (lleva a una pantalla concreta), devuelve
 *  la ruta. Sino, null. */
const notificationLink = (n: NotificationItem): string | null => {
  if (n.eventType === 'game.invitation' && n.payload) {
    try {
      const p = JSON.parse(n.payload) as { gameId?: number | string };
      if (p.gameId != null) return `/play/${p.gameId}`;
    } catch { /* ignore */ }
  }
  if (n.eventType === 'registration.approved' && n.payload) {
    try {
      const p = JSON.parse(n.payload) as { tournamentId?: number };
      if (p.tournamentId != null) return `/tournaments/${p.tournamentId}`;
    } catch { /* ignore */ }
  }
  return null;
};

const formatRelative = (iso: string | null): string => {
  if (!iso) return '';
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms) || ms < 0) return '';
  const mins = Math.floor(ms / 60_000);
  if (mins < 1) return 'ahora';
  if (mins < 60) return `hace ${mins} min`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `hace ${hrs} h`;
  return `hace ${Math.floor(hrs / 24)} d`;
};

/**
 * N1 + push: campana de notificaciones in-app + toasts emergentes (Sileo).
 *
 * Polling cada 8s del listado. Cada notificación nueva (id > lastSeenId) emite
 * un toast físico de Sileo. La invitación a partida trae acción "Unirse"; las
 * accionables, "Ver". En la primera carga se memoriza el max id para no
 * toastear el histórico de la sesión.
 */
export const NotificationBell = () => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const lastSeenIdRef = useRef<number | null>(null);
  const initializedRef = useRef(false);
  const baselineRef = useRef<number | null>(null);

  // Emite un toast Sileo por notificación nueva.
  const emitToast = useCallback((n: NotificationItem) => {
    const link = notificationLink(n);
    const title = n.subject || (n.eventType === 'game.invitation' ? 'Invitación a partida' : 'Notificación');
    const button = link
      ? { title: n.eventType === 'game.invitation' ? 'Unirse' : 'Ver', onClick: () => navigate(link) }
      : undefined;
    if (n.eventType === 'game.invitation') {
      sileo.info({ title, button });
    } else if (n.eventType.startsWith('registration.rejected')) {
      sileo.warning({ title });
    } else {
      sileo.success({ title, button });
    }
  }, [navigate]);

  const poll = useCallback(async () => {
    try {
      const list = await playerApi.listNotifications().catch(() => [] as NotificationItem[]);

      // Fijar el baseline de la sesión en la primera carga: todo lo previo a la
      // sesión queda oculto; sesión nueva = bandeja vacía.
      if (baselineRef.current == null) {
        const stored = readSessionBaseline();
        if (stored != null) {
          baselineRef.current = stored;
        } else {
          const maxId = list.reduce((m, n) => (n.id > m ? n.id : m), 0);
          baselineRef.current = maxId;
          try { sessionStorage.setItem(SESSION_BASELINE_KEY, String(maxId)); } catch { /* ignore */ }
        }
      }
      const baseline = baselineRef.current ?? 0;
      const sessionList = list.filter((n) => n.id > baseline);
      setItems(sessionList);
      setUnread(sessionList.filter((n) => !n.readAt).length);

      if (!initializedRef.current) {
        // Primera carga: no toasteamos lo que ya estaba al abrir la sesión.
        lastSeenIdRef.current = baseline;
        initializedRef.current = true;
        return;
      }

      const seen = lastSeenIdRef.current ?? baseline;
      const fresh = sessionList.filter((n) => n.id > seen);
      if (fresh.length > 0) {
        // Mostrar de la más vieja a la más nueva (orden cronológico).
        for (const n of [...fresh].reverse()) {
          emitToast(n);
        }
        const newMax = fresh.reduce((m, n) => (n.id > m ? n.id : m), seen);
        lastSeenIdRef.current = newMax;
      }
    } catch {
      // backend caído: ignorar silenciosamente
    }
  }, [emitToast]);

  useEffect(() => {
    poll();
    const interval = setInterval(poll, POLL_MS);
    return () => clearInterval(interval);
  }, [poll]);

  // Cerrar dropdown al click fuera.
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const openDropdown = async () => {
    setOpen(true);
    setLoading(true);
    try {
      const list = await playerApi.listNotifications();
      const baseline = baselineRef.current ?? readSessionBaseline() ?? 0;
      const sessionList = list.filter((n) => n.id > baseline);
      setItems(sessionList);
      if (sessionList.some((n) => !n.readAt)) {
        await playerApi.markAllNotificationsRead();
        setUnread(0);
        setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
      }
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div
        ref={wrapperRef}
        style={{
          position: 'fixed', top: 14, right: 18, zIndex: 900,
        }}
      >
        <button
          onClick={() => (open ? setOpen(false) : openDropdown())}
          title="Notificaciones"
          style={{
            position: 'relative',
            background: 'var(--surface-2, #15171a)',
            border: '1px solid var(--border, #2a2d27)',
            borderRadius: '50%',
            width: 38, height: 38,
            fontSize: 18,
            cursor: 'pointer',
            color: 'var(--text, #e8ead4)',
          }}
        >
          🔔
          {unread > 0 && (
            <span
              style={{
                position: 'absolute', top: -4, right: -4,
                minWidth: 18, height: 18, padding: '0 5px',
                borderRadius: 10,
                background: '#e05a5a', color: '#fff',
                fontSize: 11, fontWeight: 700, lineHeight: '18px',
                border: '2px solid var(--bg, #111210)',
              }}
            >
              {unread > 9 ? '9+' : unread}
            </span>
          )}
        </button>

        {open && (
          <div
            style={{
              position: 'absolute', right: 0, top: 46,
              width: 340, maxHeight: 460, overflow: 'auto',
              background: 'var(--surface, #1c1f1a)',
              border: '1px solid var(--border, #2a2d27)',
              borderRadius: 10,
              boxShadow: '0 20px 50px rgba(0,0,0,0.4)',
              padding: '6px 0',
            }}
          >
            <div style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '8px 14px', borderBottom: '1px solid var(--border, #2a2d27)',
              fontSize: 12, color: 'var(--text-muted)', fontWeight: 600,
            }}>
              <span>NOTIFICACIONES</span>
              {loading && <span>…</span>}
            </div>
            {items.length === 0 && !loading && (
              <div style={{ padding: '24px 16px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
                Sin notificaciones aún.
              </div>
            )}
            {items.map((n) => {
              const link = notificationLink(n);
              const isInvitation = n.eventType === 'game.invitation';
              const go = () => { if (link) { setOpen(false); navigate(link); } };
              const rowClickable = !isInvitation && !!link;
              return (
                <div
                  key={n.id}
                  onClick={rowClickable ? go : undefined}
                  style={{
                    padding: '10px 14px',
                    borderBottom: '1px solid rgba(255,255,255,0.04)',
                    background: n.readAt ? 'transparent' : 'rgba(106,191,116,0.06)',
                    display: 'flex', gap: 10, alignItems: 'flex-start',
                    cursor: rowClickable ? 'pointer' : 'default',
                  }}
                >
                  <span style={{ fontSize: 16, lineHeight: 1.2, flexShrink: 0 }}>{eventIcon(n.eventType)}</span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text, #e8ead4)' }}>
                      {n.subject || n.eventType}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                      {formatRelative(n.createdAt)}
                    </div>
                    {/* La invitación sigue siendo aceptable desde la campana aunque
                        el toast ya haya desaparecido. */}
                    {isInvitation && link && (
                      <button
                        onClick={(e) => { e.stopPropagation(); go(); }}
                        style={{
                          marginTop: 8, padding: '6px 12px', borderRadius: 6, border: 'none',
                          background: '#6abf74', color: '#0e100d',
                          fontSize: 12, fontWeight: 700, cursor: 'pointer',
                        }}
                      >
                        Aceptar y unirse
                      </button>
                    )}
                    {!isInvitation && link && (
                      <div style={{ marginTop: 4, fontSize: 11, color: '#6abf74', fontWeight: 600 }}>
                        Ver detalle →
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </>
  );
};
