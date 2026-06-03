import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { playerApi, NotificationItem } from '../api';

const POLL_MS = 8_000;
const TOAST_DURATION_MS = 5_000;
const MAX_TOASTS = 3;

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

interface ToastEntry {
  key: number;
  notification: NotificationItem;
}

/**
 * N1 + push: campana de notificaciones in-app + toasts emergentes.
 *
 * Polling cada 8s del listado. Para cada notificación con id > lastSeenId,
 * empuja un toast que se auto-dismissea en 5s. Esto da experiencia "push"
 * sin depender de Realtime ni Service Workers.
 *
 * En la primera carga se inicializa lastSeenId al max id existente para
 * no spamear toasts del histórico.
 */
export const NotificationBell = () => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(false);
  const [toasts, setToasts] = useState<ToastEntry[]>([]);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const lastSeenIdRef = useRef<number | null>(null);
  const initializedRef = useRef(false);

  const pushToast = useCallback((n: NotificationItem) => {
    const key = Date.now() + Math.random();
    setToasts((prev) => {
      const next = [...prev, { key, notification: n }];
      // FIFO: si superamos el máximo, descartamos los más viejos.
      return next.length > MAX_TOASTS ? next.slice(next.length - MAX_TOASTS) : next;
    });
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.key !== key));
    }, TOAST_DURATION_MS);
  }, []);

  const poll = useCallback(async () => {
    try {
      const [list, count] = await Promise.all([
        playerApi.listNotifications().catch(() => [] as NotificationItem[]),
        playerApi.unreadNotificationCount().catch(() => 0),
      ]);
      setUnread(count);
      setItems(list);

      if (!initializedRef.current) {
        // Primera carga: solo memorizamos el max id para no toastear histórico.
        const maxId = list.reduce((m, n) => (n.id > m ? n.id : m), 0);
        lastSeenIdRef.current = maxId;
        initializedRef.current = true;
        return;
      }

      const seen = lastSeenIdRef.current ?? 0;
      const fresh = list.filter((n) => n.id > seen);
      if (fresh.length > 0) {
        // Mostrar de la más vieja a la más nueva (orden cronológico).
        for (const n of [...fresh].reverse()) {
          pushToast(n);
        }
        const newMax = fresh.reduce((m, n) => (n.id > m ? n.id : m), seen);
        lastSeenIdRef.current = newMax;
      }
    } catch {
      // backend caído: ignorar silenciosamente
    }
  }, [pushToast]);

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
      setItems(list);
      if (list.some((n) => !n.readAt)) {
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

  const dismissToast = (key: number) => {
    setToasts((prev) => prev.filter((t) => t.key !== key));
  };

  /** Declinar invitación: marca la notificación como leída y cierra el toast.
   *  No notifica al invitador (no hay endpoint dedicado todavía). */
  const declineInvitation = async (key: number, notificationId: number) => {
    dismissToast(key);
    try {
      await playerApi.markNotificationRead(notificationId);
      setUnread((u) => Math.max(0, u - 1));
    } catch {
      /* ignore: el polling siguiente lo reconciliará */
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

      {/* Stack de toasts emergentes (push notifications). */}
      <div
        style={{
          position: 'fixed', top: 64, right: 18, zIndex: 1100,
          display: 'flex', flexDirection: 'column', gap: 10,
          pointerEvents: 'none',
        }}
      >
        {toasts.map(({ key, notification: n }) => {
          const link = notificationLink(n);
          const isInvitation = n.eventType === 'game.invitation';
          return (
            <div
              key={key}
              onClick={() => {
                if (!isInvitation && link) {
                  navigate(link);
                  dismissToast(key);
                }
              }}
              style={{
                pointerEvents: 'auto',
                cursor: isInvitation ? 'default' : (link ? 'pointer' : 'default'),
                minWidth: 300, maxWidth: 380,
                background: isInvitation ? 'rgba(35,28,22,0.98)' : 'rgba(28,31,26,0.98)',
                border: '1px solid var(--border, #2a2d27)',
                borderLeft: `3px solid ${isInvitation ? '#f0b94e' : '#6abf74'}`,
                borderRadius: 10,
                boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
                padding: '12px 14px',
                display: 'flex', gap: 12, alignItems: 'flex-start',
                animation: 'cq-toast-slide-in 220ms ease-out',
              }}
            >
              <span style={{ fontSize: 22, flexShrink: 0 }}>{eventIcon(n.eventType)}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text, #e8ead4)', lineHeight: 1.3 }}>
                  {n.subject || (isInvitation ? 'Invitación a partida' : 'Notificación')}
                </div>
                {isInvitation && link && (
                  <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                    <button
                      onClick={(e) => { e.stopPropagation(); dismissToast(key); navigate(link); }}
                      style={{
                        flex: 1, padding: '7px 10px', borderRadius: 6, border: 'none',
                        background: '#6abf74', color: '#0e100d',
                        fontSize: 12, fontWeight: 700, cursor: 'pointer',
                        letterSpacing: '0.02em',
                      }}
                    >
                      Aceptar
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); declineInvitation(key, n.id); }}
                      style={{
                        flex: 1, padding: '7px 10px', borderRadius: 6,
                        border: '1px solid #4a4d40', background: 'transparent',
                        color: 'var(--text-muted, #7a7d6e)',
                        fontSize: 12, fontWeight: 600, cursor: 'pointer',
                      }}
                    >
                      Declinar
                    </button>
                  </div>
                )}
                {!isInvitation && link && (
                  <div
                    style={{
                      marginTop: 6, fontSize: 12, color: '#6abf74', fontWeight: 600,
                      display: 'flex', alignItems: 'center', gap: 4,
                    }}
                  >
                    Ver detalle <span>→</span>
                  </div>
                )}
              </div>
              <button
                onClick={(e) => { e.stopPropagation(); dismissToast(key); }}
                style={{
                  background: 'transparent', border: 'none', color: 'var(--text-muted)',
                  cursor: 'pointer', fontSize: 16, lineHeight: 1, padding: 2,
                  flexShrink: 0,
                }}
                aria-label="Cerrar"
              >
                ×
              </button>
            </div>
          );
        })}
        <style>{`
          @keyframes cq-toast-slide-in {
            from { opacity: 0; transform: translateX(20px); }
            to { opacity: 1; transform: translateX(0); }
          }
        `}</style>
      </div>
    </>
  );
};
