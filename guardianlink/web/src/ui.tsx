import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { translate, type Language } from './i18n';

type Theme = 'light' | 'dark';
type UiValue = { language: Language; theme: Theme; t: (key: Parameters<typeof translate>[1]) => string; toggleLanguage: () => void; toggleTheme: () => void };
const UiContext = createContext<UiValue | null>(null);

export function UiProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(() => localStorage.getItem('gl-language') === 'en' ? 'en' : 'fa');
  const [theme, setTheme] = useState<Theme>(() => localStorage.getItem('gl-theme') === 'dark' ? 'dark' : 'light');
  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'fa' ? 'rtl' : 'ltr';
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('gl-language', language);
    localStorage.setItem('gl-theme', theme);
  }, [language, theme]);
  const value = useMemo(() => ({ language, theme, t: (key: Parameters<typeof translate>[1]) => translate(language, key), toggleLanguage: () => setLanguage(v => v === 'fa' ? 'en' : 'fa'), toggleTheme: () => setTheme(v => v === 'light' ? 'dark' : 'light') }), [language, theme]);
  return <UiContext.Provider value={value}>{children}</UiContext.Provider>;
}
export function useUi() { const value = useContext(UiContext); if (!value) throw new Error('useUi requires UiProvider'); return value; }
