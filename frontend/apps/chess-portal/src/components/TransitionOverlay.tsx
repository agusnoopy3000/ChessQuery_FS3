interface TransitionOverlayProps {
  message: string;
  detail?: string;
}

export const TransitionOverlay = ({ message, detail }: TransitionOverlayProps) => (
  <div
    role="status"
    aria-live="polite"
    className="cq-transition-overlay"
  >
    <style>{`
      @keyframes cq-transition-fade {
        from { opacity: 0; }
        to { opacity: 1; }
      }
      @keyframes cq-transition-card {
        from { opacity: 0; transform: translateY(10px) scale(0.98); }
        to { opacity: 1; transform: translateY(0) scale(1); }
      }
      @keyframes cq-transition-ring {
        to { transform: rotate(360deg); }
      }
      @keyframes cq-transition-dot {
        0%, 80%, 100% { transform: translateY(0); opacity: 0.42; }
        40% { transform: translateY(-5px); opacity: 1; }
      }
      .cq-transition-overlay {
        position: fixed;
        inset: 0;
        z-index: 2200;
        display: grid;
        place-items: center;
        padding: 20px;
        background: rgba(10, 12, 10, 0.72);
        backdrop-filter: blur(10px);
        animation: cq-transition-fade 180ms ease-out both;
      }
      .cq-transition-card {
        min-width: min(360px, 100%);
        border: 1px solid rgba(106, 191, 116, 0.26);
        border-radius: 12px;
        background: linear-gradient(180deg, rgba(28, 32, 27, 0.98), rgba(18, 21, 18, 0.98));
        box-shadow: 0 24px 70px rgba(0, 0, 0, 0.52);
        padding: 26px 24px;
        text-align: center;
        animation: cq-transition-card 220ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
      }
      .cq-transition-spinner {
        width: 54px;
        height: 54px;
        margin: 0 auto 16px;
        border-radius: 50%;
        border: 2px solid rgba(106, 191, 116, 0.18);
        border-top-color: var(--cq-accent, #6abf74);
        border-right-color: rgba(126, 206, 134, 0.72);
        animation: cq-transition-ring 760ms linear infinite;
      }
      .cq-transition-title {
        margin: 0;
        color: var(--cq-text, #e8ead4);
        font-family: 'Space Grotesk', system-ui, sans-serif;
        font-size: 18px;
        font-weight: 700;
        letter-spacing: 0;
      }
      .cq-transition-detail {
        margin: 8px 0 0;
        color: var(--cq-text-dim, #7a7d6e);
        font-size: 13px;
        line-height: 1.5;
      }
      .cq-transition-dots {
        margin-top: 14px;
        display: inline-flex;
        gap: 5px;
      }
      .cq-transition-dots span {
        width: 5px;
        height: 5px;
        border-radius: 50%;
        background: var(--cq-accent, #6abf74);
        animation: cq-transition-dot 900ms ease-in-out infinite;
      }
      .cq-transition-dots span:nth-child(2) { animation-delay: 120ms; }
      .cq-transition-dots span:nth-child(3) { animation-delay: 240ms; }
    `}</style>
    <div className="cq-transition-card">
      <div className="cq-transition-spinner" />
      <h2 className="cq-transition-title">{message}</h2>
      {detail && <p className="cq-transition-detail">{detail}</p>}
      <div className="cq-transition-dots" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
    </div>
  </div>
);
