import React, { useState } from 'react';
import { Skull, UserPlus, LogOut, Hammer } from 'lucide-react';
import LoginPage from './views/LoginPage.jsx';

const topPlayers = [
    { id: 1, name: 'Player1', kills: 1250, online: true },
    { id: 2, name: 'Player2', kills: 1100, online: true },
    { id: 3, name: 'Player3', kills: 980, online: false },
];

const recentEvents = [
    { id: 1, type: 'kill', text: 'Player1 eliminó a Player4', time: 'hace 2m', tooltip: 'Evento: Kill' },
    { id: 2, type: 'join', text: 'Player5 se unió al servidor', time: 'hace 5m', tooltip: 'Evento: Conexión' },
    { id: 3, type: 'leave', text: 'Player2 salió del servidor', time: 'hace 10m', tooltip: 'Evento: Desconexión' },
    { id: 4, type: 'build', text: 'Player6 construyó un Castillo', time: 'hace 15m', tooltip: 'Evento: Construcción' },
    { id: 5, type: 'kill', text: 'Player3 eliminó a Player7', time: 'hace 18m', tooltip: 'Evento: Kill' },
    { id: 6, type: 'join', text: 'Player8 se unió al servidor', time: 'hace 22m', tooltip: 'Evento: Conexión' },
];

const topItems = [
    { id: 1, name: 'Espada de Diamante', value: '5000 usos' },
    { id: 2, name: 'Pico de Netherita', value: '4500 usos' },
    { id: 3, name: 'Arco Encantado', value: '3000 usos' },
];

const topBlocks = [
    { id: 1, name: 'Piedra', value: '10M colocados' },
    { id: 2, name: 'Tierra', value: '8M colocados' },
    { id: 3, name: 'Madera de Roble', value: '6M colocados' },
];

const navLinks = ['Inicio', 'Mapas', 'Jugadores', 'Clanes', 'Estadísticas', 'Rankings', 'Historial'];

function PlaceholderIcon() {
    return (
        <div className="w-10 h-10 border border-gray-700 rounded-lg flex items-center justify-center relative overflow-hidden bg-[#2a2a2a] flex-shrink-0">
            <div className="absolute w-[140%] h-px bg-gray-600 rotate-45" />
            <div className="absolute w-[140%] h-px bg-gray-600 -rotate-45" />
        </div>
    );
}

function EventIcon({ type }) {
    const icons = {
        kill: { icon: <Skull size={16} />, color: 'text-red-500 bg-red-500/10', label: 'Kill' },
        join: { icon: <UserPlus size={16} />, color: 'text-green-400 bg-green-400/10', label: 'Join' },
        leave: { icon: <LogOut size={16} />, color: 'text-yellow-400 bg-yellow-400/10', label: 'Leave' },
        build: { icon: <Hammer size={16} />, color: 'text-blue-400 bg-blue-400/10', label: 'Build' },
    };
    const { icon, color, label } = icons[type] || icons.build;
    return (
        <div
            data-tooltip={label}
            className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${color}`}
        >
            {icon}
        </div>
    );
}

export default function App() {
    const [activeNav, setActiveNav] = useState('Inicio');
    const [user, setUser] = useState(null);

    if (!user) {
        return <LoginPage onLogin={setUser} />;
    }

    return (
        <div className="min-h-screen bg-[#121212] text-gray-200" style={{ fontFamily: "'Inter', sans-serif" }}>

            {/* Barra de navegación sticky */}
            <nav className="sticky top-0 z-50 flex items-center justify-between px-10 py-3 bg-[#1a1a1a] border-b border-gray-800/60 backdrop-blur-sm">
                <div className="flex items-center gap-1">
                    {navLinks.map(link => (
                        <button
                            key={link}
                            onClick={() => setActiveNav(link)}
                            className={`px-5 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
                                activeNav === link
                                    ? 'text-white bg-white/10'
                                    : 'text-gray-400 hover:text-white hover:bg-white/5'
                            }`}
                        >
                            {link}
                        </button>
                    ))}
                </div>
                <div className="flex items-center gap-3">
                    <div className="flex items-center gap-2.5">
                        <div className="relative">
                            <div className="w-9 h-9 bg-gradient-to-br from-gray-500 to-gray-700 rounded-full flex items-center justify-center text-xs font-bold text-white uppercase">
                                {user.username.slice(0, 2)}
                            </div>
                            <div className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-green-400 rounded-full border-2 border-[#1a1a1a] badge-online" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-gray-200 leading-none">{user.username}</p>
                            <p className="text-xs text-gray-500 mt-0.5 capitalize">{user.role === 'ADMIN' ? 'Administrador' : 'Miembro'}</p>
                        </div>
                    </div>
                    <button
                        onClick={() => setUser(null)}
                        className="text-gray-600 hover:text-gray-300 transition-colors ml-1 text-xs"
                        title="Cerrar sesión"
                    >
                        <LogOut size={15} />
                    </button>
                </div>
            </nav>

            {/* Contenido principal */}
            <main className="px-10 py-8 max-w-[1280px] mx-auto w-full">

                {/* Título */}
                <div className="mb-6">
                    <h1 className="text-3xl font-bold text-white tracking-tight">Panel de Control del Servidor</h1>
                    <p className="text-gray-500 text-sm mt-1">Resumen general del estado del servidor</p>
                </div>

                {/* Dashboard Grid */}
                <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                    gridTemplateRows: 'auto auto auto',
                    gap: '20px',
                    gridTemplateAreas: `
                        "stat1  stat2   stat3"
                        "players events events"
                        "items  items   blocks"
                    `
                }}>

                    {/* Stat 1: Jugadores Online */}
                    <div className="stat-card bg-[#696969] p-6 rounded-2xl shadow-lg border border-gray-800/40" style={{ gridArea: 'stat1' }}>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-gray-400 text-xs font-semibold uppercase tracking-widest">Jugadores Online</h3>
                            <div className="w-2 h-2 rounded-full bg-green-400 badge-online" />
                        </div>
                        <div className="flex items-baseline space-x-2">
                            <span className="text-5xl font-extrabold text-green-400">128</span>
                            <span className="text-xl font-medium text-gray-600">/ 250</span>
                        </div>
                        <div className="mt-4 h-1.5 bg-gray-800 rounded-full overflow-hidden">
                            <div className="h-full bg-green-400/70 rounded-full" style={{ width: '51.2%' }} />
                        </div>
                        <p className="text-xs text-gray-600 mt-1.5">51% de capacidad</p>
                    </div>

                    {/* Stat 2: Tiempo de Actividad */}
                    <div className="stat-card bg-[#696969] p-6 rounded-2xl shadow-lg border border-gray-800/40" style={{ gridArea: 'stat2' }}>
                        <h3 className="text-gray-400 text-xs font-semibold uppercase tracking-widest mb-3">Tiempo de Actividad</h3>
                        <span className="text-5xl font-extrabold text-blue-400">99.8%</span>
                        <p className="text-xs text-gray-600 mt-4">Uptime en los últimos 30 días</p>
                    </div>

                    {/* Stat 3: TPS */}
                    <div className="stat-card bg-[#696969] p-6 rounded-2xl shadow-lg border border-gray-800/40" style={{ gridArea: 'stat3' }}>
                        <h3 className="text-gray-400 text-xs font-semibold uppercase tracking-widest mb-3">TPS Promedio</h3>
                        <span className="text-5xl font-extrabold text-orange-400">19.8</span>
                        <p className="text-xs text-gray-600 mt-4">Máximo: 20 TPS</p>
                    </div>

                    {/* Top Jugadores — columna angosta */}
                    <div className="bg-[#696969] p-6 rounded-2xl shadow-lg border border-gray-800/40 flex flex-col" style={{ gridArea: 'players' }}>
                        <h3 className="text-sm font-bold text-white mb-5 flex items-center gap-2">
                            <span className="w-6 h-6 rounded-lg bg-red-500/20 flex items-center justify-center">
                                <Skull size={13} className="text-red-400" />
                            </span>
                            Top Jugadores
                        </h3>
                        <div className="flex-1">
                            {topPlayers.map((player, index) => (
                                <div
                                    key={player.id}
                                    className={`list-row flex items-center justify-between py-3.5 ${
                                        index !== topPlayers.length - 1 ? 'border-b border-gray-800/60' : ''
                                    }`}
                                >
                                    <div className="flex items-center gap-3">
                                        <span className="text-xs font-bold text-gray-700 w-5 text-center">#{index + 1}</span>
                                        <div className="relative">
                                            <div className="w-9 h-9 bg-gradient-to-br from-gray-600 to-gray-700 rounded-full" />
                                            <div
                                                data-tooltip={player.online ? 'Online' : 'Offline'}
                                                className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-[#1e1e1e] ${
                                                    player.online ? 'bg-green-400 badge-online' : 'bg-red-500'
                                                }`}
                                            />
                                        </div>
                                        <span className="text-gray-200 font-medium text-sm">{player.name}</span>
                                    </div>
                                    <span className="text-red-400 font-bold text-sm tabular-nums">
                                        {player.kills.toLocaleString()} <span className="text-gray-600 font-normal">kills</span>
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Eventos Recientes — columna ancha (span 2) */}
                    <div className="bg-[#696969] p-6 rounded-2xl shadow-lg border border-gray-800/40 flex flex-col" style={{ gridArea: 'events' }}>
                        <h3 className="text-sm font-bold text-white mb-5 flex items-center gap-2">
                            <span className="w-6 h-6 rounded-lg bg-blue-500/20 flex items-center justify-center">
                                <span className="text-blue-400 text-xs font-black">!</span>
                            </span>
                            Eventos Recientes
                        </h3>
                        <div className="events-scroll flex-1 pr-1">
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0' }}>
                                {recentEvents.map((event, index) => (
                                    <div
                                        key={event.id}
                                        className={`list-row flex items-center gap-3 py-3 mx-1 ${
                                            index < recentEvents.length - 2 ? 'border-b border-gray-800/60' : ''
                                        }`}
                                    >
                                        <EventIcon type={event.type} />
                                        <div className="flex-1 min-w-0">
                                            <p className="text-gray-300 text-sm truncate">{event.text}</p>
                                            <p className="text-xs text-gray-600 mt-0.5 tabular-nums">{event.time}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>

                    {/* Top Items — span 2 */}
                    <div className="bg-[#696969] p-6 rounded-2xl shadow-lg border border-gray-800/40" style={{ gridArea: 'items' }}>
                        <h3 className="text-sm font-bold text-white mb-5 flex items-center gap-2">
                            <span className="w-6 h-6 rounded-lg bg-orange-500/20 flex items-center justify-center text-orange-400 text-xs font-bold">⚔</span>
                            Top Items
                        </h3>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px' }}>
                            {topItems.map((item) => (
                                <div
                                    key={item.id}
                                    className="list-row flex items-center gap-3 p-3 rounded-xl bg-[#252525]"
                                >
                                    <PlaceholderIcon />
                                    <div className="min-w-0">
                                        <p className="text-gray-200 text-sm font-medium truncate">{item.name}</p>
                                        <p className="text-orange-400 text-xs font-semibold tabular-nums mt-0.5">{item.value}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Top Bloques — columna angosta */}
                    <div className="bg-[#696969] p-6 rounded-2xl shadow-lg border border-gray-800/40" style={{ gridArea: 'blocks' }}>
                        <h3 className="text-sm font-bold text-white mb-5 flex items-center gap-2">
                            <span className="w-6 h-6 rounded-lg bg-blue-500/20 flex items-center justify-center text-blue-400 text-xs font-bold">▦</span>
                            Top Bloques
                        </h3>
                        <div>
                            {topBlocks.map((block, index) => (
                                <div
                                    key={block.id}
                                    className={`list-row flex items-center justify-between py-3.5 ${
                                        index !== topBlocks.length - 1 ? 'border-b border-gray-800/60' : ''
                                    }`}
                                >
                                    <div className="flex items-center gap-3">
                                        <PlaceholderIcon />
                                        <span className="text-gray-200 text-sm">{block.name}</span>
                                    </div>
                                    <span className="text-blue-400 font-semibold text-sm tabular-nums">{block.value}</span>
                                </div>
                            ))}
                        </div>
                    </div>

                </div>

            </main>
        </div>
    );
}