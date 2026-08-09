import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';
import { AuthProvider } from './features/auth/context/AuthContext';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import NotificationProvider from './app/providers/NotificationProvider';

// Bật mock API khi VITE_USE_MOCK=true (không cần chạy backend)
if (import.meta.env.VITE_USE_MOCK === 'true') {
  import('./mocks/mockApi').then(() => {
    console.info('%c[MOCK MODE] Đang chạy với dữ liệu giả — backend không cần thiết', 'color: #10b981; font-weight: bold');
  }).catch(console.error);
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <NotificationProvider>
        <App />
      </NotificationProvider>
      <ToastContainer 
        position="top-right"
        autoClose={3000}
        hideProgressBar={false}
        newestOnTop={false}
        closeOnClick
        rtl={false}
        pauseOnFocusLoss
        draggable
        pauseOnHover
        theme="colored"
      />
    </AuthProvider>
  </StrictMode>
);
