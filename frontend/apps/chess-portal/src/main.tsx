import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, createSupabaseApiClient } from '@chessquery/shared';
import '@chessquery/ui-lib';
import { App } from './App';
import { supabase } from './lib/supabase';

const baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

const api = createSupabaseApiClient({
  baseURL,
  supabase,
  onAuthFailure: () => {
    if (window.location.pathname !== '/login') {
      window.location.assign('/login');
    }
  },
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export { api, supabase };

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider supabase={supabase} defaultRole="PLAYER">
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
