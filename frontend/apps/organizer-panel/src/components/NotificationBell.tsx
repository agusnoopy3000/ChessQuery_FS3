import { useCallback, useEffect, useRef, useState } from 'react';
import { sileo } from 'sileo';
import { organizerApi, NotificationItem } from '../api';

const POLL_MS = 8_000;

// Baseline de sesión: solo se muestran notificaciones posteriores al inicio de
// la sesión. sessionStorage → sesión nueva = bandeja vacía; se limpia en logout.
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
  if (eventType.startsWith('registration.')) return '📋';
  if (eventType.startsWith('tournament.')) return '🏆';
  if (eventType.startsWith('player.')) return '✅';
  if (eventType.startsWith('game.')) return '♟';
  return '🔔';
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
 * Campana de notificaciones + toasts emergentes (Sileo) para el organizador.
 * Polling cada 8s; cada notificación nueva emite un toast físico.
 */
export const NotificationBell = () => {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const lastSeenIdRef = useRef<number | null>(null);
  const initializedRef = useRef(false);
  const baselineRef = useRef<number | null>(null);

  const emitToast = useCallback((n: NotificationItem) => {
    const title = n.subject || 'Notificación';
    if (n.eventType === 'registration.pending') {
      sileo.info({ title });
    } else {
      sileo.success({ title });
    }
  }, []);

  const poll = useCallback(async () => {
    try {
      const list = await organizerApi.listNotifications().catch(() => [] as NotificationItem[]);

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
        lastSeenIdRef.current = baseline;
        initializedRef.current = true;
        return;
      }
      const seen = lastSeenIdRef.current ?? baseline;
      const fresh = sessionList.filter((n) => n.id > seen);
      if (fresh.length > 0) {
        for (const n of [...fresh].reverse()) emitToast(n);
        lastSeenIdRef.current = fresh.reduce((m, n) => (n.id > m ? n.id : m), seen);
      }
    } catch {
      /* ignore */
    }
  }, [emitToast]);

  useEffect(() => {
    poll();
    const interval = setInterval(poll, POLL_MS);
    return () => clearInterval(interval);
  }, [poll]);

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
      const list = await organizerApi.listNotifications();
      const baseline = baselineRef.current ?? readSessionBaseline() ?? 0;
      const sessionList = list.filter((n) => n.id > baseline);
      setItems(sessionList);
      if (sessionList.some((n) => !n.readAt)) {
        await organizerApi.markAllNotificationsRead();
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
      <div ref={wrapperRef} style={{ position: 'fixed', top: 14, right: 18, zIndex: 900 }}>
        <button
          onClick={() => (open ? setOpen(false) : openDropdown())}
          title="Notificaciones"
          style={{
            position: 'relative',
            background: 'var(--surface-2, #15171a)',
            border: '1px solid var(--border, #2a2d27)',
            borderRadius: '50%',
            width: 38, height: 38,
            fontSize: 18, cursor: 'pointer',
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
            {items.map((n) => (
              <div
                key={n.id}
                style={{
                  padding: '10px 14px',
                  borderBottom: '1px solid rgba(255,255,255,0.04)',
                  background: n.readAt ? 'transparent' : 'rgba(106,191,116,0.06)',
                  display: 'flex', gap: 10, alignItems: 'flex-start',
                }}
              >
                <span style={{ fontSize: 16, lineHeight: 1.2, flexShrink: 0 }}>{eventIcon(n.eventType)}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text, #e8ead4)' }}>
                    {n.subject || n.eventType}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                    {formatRelative(n.createdAt)} · {n.eventType}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
};
