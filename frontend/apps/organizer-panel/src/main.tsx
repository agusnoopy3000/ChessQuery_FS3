import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  AuthProvider,
  createApiClient,
  localStorageTokenStorage,
} from '@chessquery/shared';
import '@chessquery/ui-lib';
import { App } from './App';

const baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
const storage = localStorageTokenStorage('chessquery.organizer');

const api = createApiClient({
  baseURL,
  storage,
  onAuthFailure: () => {
    window.location.assign('/login');
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

export { api, storage };

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider client={api} storage={storage}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
