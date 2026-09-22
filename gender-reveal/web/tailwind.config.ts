import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        'surface-50': '#FFFBF6',
        'surface-100': '#FFFFFF',
        'surface-200': '#F3EEE7',
        border: '#E6E0D5',
        ink: '#2E2B27',
        'ink-muted': '#8C8579',
        'accent-boy': '#6F86E6',
        'accent-boy-soft': '#EEF1FD',
        'accent-girl': '#E37AA6',
        'accent-girl-soft': '#FDEDF4',
        'accent-primary': '#F2793A',
        'accent-primary-soft': '#FDEAD9',
        danger: '#D6483C',
      },
      fontFamily: {
        display: ['var(--font-hi-melody)', 'system-ui', 'sans-serif'],
        sans: ['var(--font-noto-sans-kr)', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        'display-lg': ['32px', { lineHeight: '40px', fontWeight: '400' }],
        'display-md': ['22px', { lineHeight: '30px', fontWeight: '400' }],
        'body-lg': ['16px', { lineHeight: '24px', fontWeight: '500' }],
        body: ['14px', { lineHeight: '22px', fontWeight: '400' }],
        'body-sm': ['12px', { lineHeight: '18px', fontWeight: '400' }],
        label: ['13px', { lineHeight: '18px', fontWeight: '600' }],
      },
      spacing: {
        'space-1': '4px',
        'space-2': '8px',
        'space-3': '16px',
        'space-4': '24px',
        'space-5': '32px',
      },
      borderRadius: {
        'radius-sm': '8px',
        'radius-md': '14px',
        'radius-full': '999px',
      },
      keyframes: {
        'float-up': {
          '0%': { transform: 'translateY(0) scale(1)', opacity: '0' },
          '20%': { opacity: '0.7' },
          '100%': { transform: 'translateY(-120px) scale(1.2)', opacity: '0' },
        },
        shake: {
          '0%, 100%': { transform: 'rotate(0deg)' },
          '20%': { transform: 'rotate(-6deg)' },
          '40%': { transform: 'rotate(6deg)' },
          '60%': { transform: 'rotate(-4deg)' },
          '80%': { transform: 'rotate(4deg)' },
        },
        'pop-in': {
          '0%': { transform: 'scale(0.3)', opacity: '0' },
          '70%': { transform: 'scale(1.1)', opacity: '1' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
      },
      animation: {
        'float-up': 'float-up 4s ease-in infinite',
        shake: 'shake 0.9s ease-in-out',
        'pop-in': 'pop-in 0.7s ease-out both',
      },
    },
  },
  plugins: [],
};

export default config;
