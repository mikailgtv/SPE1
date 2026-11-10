import { describe, expect, it } from 'vitest';
import { translate } from './i18n';

describe('localization', () => {
  it('provides Persian copy and English copy for the core navigation', () => {
    expect(translate('fa', 'dashboard')).toBe('نمای کلی');
    expect(translate('en', 'dashboard')).toBe('Overview');
    expect(translate('fa', 'unsupportedCamera')).toContain('هیچ مجوز دوربینی افزوده نشده');
  });
});
