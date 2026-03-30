/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                'mc-dark': '#0d0d0d',
                'mc-card': '#151515',
                'mc-green': '#4ade80',
            },
        },
    },
    plugins: [],
}