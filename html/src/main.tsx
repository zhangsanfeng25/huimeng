import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import {PageBoundary} from './components/PageBoundary';
import './styles.css';
import './icon.css';
import './theme.css';
import './dashboard.css';
import './login.css';
import './fonts.css';
import './management.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <PageBoundary><App /></PageBoundary>
    </BrowserRouter>
  </React.StrictMode>,
);
