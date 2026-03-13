import { useState, useEffect } from 'react';
import Login from './components/Login';
import Register from './components/Register';
import Chat from './components/Chat';

type ViewState = 'login' | 'register' | 'chat';

function App() {
  const [view, setView] = useState<ViewState>('login');

  useEffect(() => {
    // Check if user is already logged in
    const token = localStorage.getItem('accessToken');
    if (token) {
      setView('chat');
    } else {
      setView('login');
    }
  }, []);

  const handleAuthSuccess = () => {
    setView('chat');
  };

  const handleLogout = () => {
    setView('login');
  };

  return (
    <div className="min-h-screen w-screen bg-transparent">
      {view === 'login' && (
        <Login 
          onLoginSuccess={handleAuthSuccess} 
          onSwitchToRegister={() => setView('register')} 
        />
      )}
      {view === 'register' && (
        <Register 
          onRegisterSuccess={handleAuthSuccess} 
          onSwitchToLogin={() => setView('login')} 
        />
      )}
      {view === 'chat' && (
        <Chat onLogout={handleLogout} />
      )}
    </div>
  );
}

export default App;
