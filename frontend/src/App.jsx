import React, { useState } from 'react';
import { Skull, UserPlus, LogOut, Hammer } from 'lucide-react';
import LoginPage from './views/LoginPage.jsx';

const topPlayers = [
    { id: 1, name: 'Player1', kills: 1250, online: true },
    { id: 2, name: 'Player2', kills: 1100, online: true },
    { id: 3, name: 'Player3', kills: 980, online: false },
];

const recentEvents = [
    { id: 1, type: 'kill',  text: 'Player1 eliminó a Player4',    time: 'hace 2m'  },
    { id: 2, type: 'join',  text: 'Player5 se unió al servidor',   time: 'hace 5m'  },
    { id: 3, type: 'leave', text: 'Player2 salió del servidor',    time: 'hace 10m' },
    { id: 4, type: 'build', text: 'Player6 construyó un Castillo', time: 'hace 15m' },
    { id: 5, type: 'kill',  text: 'Player3 eliminó a Player7',     time: 'hace 18m' },
    { id: 6, type: 'join',  text: 'Player8 se unió al servidor',   time: 'hace 22m' },
];

const topItems = [
    { id: 1, name: 'Espada de Diamante', value: '5 000 usos' },
    { id: 2, name: 'Pico de Netherita',  value: '4 500 usos' },
    { id: 3, name: 'Arco Encantado',     value: '3 000 usos' },
];

const topBlocks = [
    { id: 1, name: 'Piedra',         value: '10M' },
    { id: 2, name: 'Tierra',         value: '8M'  },
    { id: 3, name: 'Madera de Roble',value: '6M'  },
];

const navLinks = ['Inicio', 'Mapas', 'Jugadores', 'Clanes', 'Estadísticas', 'Rankings', 'Historial'];

// ── Componentes pequeños ────────────────────────────────────────────

function PlaceholderIcon() {
    return (
        <div style={{ background: 'var(--card-hover)', border: '1px solid var(--border)' }}
             className="w-9 h-9 rounded-lg flex items-center justify-center relative overflow-hidden flex-shrink-0">
            <div className="absolute w-[140%] h-px rotate-45"    style={{ background: 'var(--border)' }} />
            <div className="absolute w-[140%] h-px -rotate-45"   style={{ background: 'var(--border)' }} />
        </div>
    );
}

function EventIcon({ type }) {
    const map = {
        kill:  { icon: <Skull    size={14} />, bg: 'rgba(194,84,90,0.12)',  color: 'var(--red)'    },
        join:  { icon: <UserPlus size={14} />, bg: 'rgba(75,191,133,0.12)', color: 'var(--green)'  },
        leave: { icon: <LogOut   size={14} />, bg: 'rgba(184,154,62,0.12)', color: 'var(--yellow)' },
        build: { icon: <Hammer   size={14} />, bg: 'rgba(90,159,212,0.12)', color: 'var(--blue)'   },
    };
    const { icon, bg, color } = map[type] || map.build;
    return (
        <div data-tooltip={type} className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0"
             style={{ background: bg, color }}>
            {icon}
        </div>
    );
}

// ── App ─────────────────────────────────────────────────────────────

export default function App() {
    const [activeNav, setActiveNav] = useState('Inicio');
    const [user, setUser] = useState(null);

    if (!user) return <LoginPage onLogin={setUser} />;

    return (
        <div className="min-h-screen" style={{ background: 'var(--bg)', color: 'var(--text-1)', fontFamily: "'Inter', sans-serif" }}>

            {/* ── Navbar ─────────────────────────────────────────── */}
            <nav className="sticky top-0 z-50 flex items-center justify-between px-8 py-3"
                 style={{ background: 'var(--surface)', borderBottom: '1px solid var(--border)' }}>

                <div className="flex items-center gap-0.5">
                    {navLinks.map(link => (
                        <button
                            key={link}
                            onClick={() => setActiveNav(link)}
                            className="px-4 py-2 rounded-lg text-sm font-medium transition-all duration-150"
                            style={{
                                color:      activeNav === link ? 'var(--text-1)' : 'var(--text-2)',
                                background: activeNav === link ? 'rgba(255,255,255,0.06)' : 'transparent',
                            }}
                            onMouseEnter={e => { if (activeNav !== link) e.currentTarget.style.color = 'var(--text-1)'; }}
                            onMouseLeave={e => { if (activeNav !== link) e.currentTarget.style.color = 'var(--text-2)'; }}
                        >
                            {link}
                        </button>
                    ))}
                </div>

                <div className="flex items-center gap-3">
                    <div className="flex items-center gap-2.5">
                        <div className="relative">
                            <div className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold"
                                 style={{ background: 'var(--border)', color: 'var(--text-2)' }}>
                                {user.username.slice(0, 2).toUpperCase()}
                            </div>
                            <div className="badge-online absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2"
                                 style={{ background: 'var(--green)', borderColor: 'var(--surface)' }} />
                        </div>
                        <div>
                            <p className="text-sm font-medium leading-none" style={{ color: 'var(--text-1)' }}>{user.username}</p>
                            <p className="text-xs mt-0.5" style={{ color: 'var(--text-3)' }}>
                                {user.role === 'ADMIN' ? 'Administrador' : 'Miembro'}
                            </p>
                        </div>
                    </div>
                    <button onClick={() => setUser(null)} data-tooltip="Cerrar sesión"
                            className="w-7 h-7 rounded-lg flex items-center justify-center transition-colors"
                            style={{ color: 'var(--text-3)' }}
                            onMouseEnter={e => e.currentTarget.style.color = 'var(--text-2)'}
                            onMouseLeave={e => e.currentTarget.style.color = 'var(--text-3)'}>
                        <LogOut size={14} />
                    </button>
                </div>
            </nav>

            {/* ── Main ───────────────────────────────────────────── */}
            <main className="px-8 py-7 max-w-[1280px] mx-auto w-full">

                <div className="mb-6">
                    <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-1)' }}>
                        Panel de Control
                    </h1>
                    <p className="text-sm mt-0.5" style={{ color: 'var(--text-3)' }}>Estado general del servidor</p>
                </div>

                {/* ── Grid ──────────────────────────────────────── */}
                <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                    gap: '16px',
                    gridTemplateAreas: `
                        "stat1   stat2   stat3"
                        "players events  events"
                        "items   items   blocks"
                    `
                }}>

                    {/* Stat 1 — Jugadores */}
                    <div className="stat-card p-5 rounded-2xl" style={{ gridArea: 'stat1', background: 'var(--card)', border: '1px solid var(--border)' }}>
                        <div className="flex items-center justify-between mb-3">
                            <span className="text-xs font-semibold uppercase tracking-widest" style={{ color: 'var(--text-3)' }}>Jugadores Online</span>
                            <div className="badge-online w-2 h-2 rounded-full" style={{ background: 'var(--green)' }} />
                        </div>
                        <div className="flex items-baseline gap-2">
                            <span className="text-4xl font-bold" style={{ color: 'var(--green)' }}>128</span>
                            <span className="text-lg font-medium" style={{ color: 'var(--text-3)' }}>/ 250</span>
                        </div>
                        <div className="mt-3 h-1 rounded-full overflow-hidden" style={{ background: 'var(--border)' }}>
                            <div className="h-full rounded-full" style={{ width: '51.2%', background: 'var(--green)', opacity: 0.6 }} />
                        </div>
                        <p className="text-xs mt-1.5" style={{ color: 'var(--text-3)' }}>51% de capacidad</p>
                    </div>

                    {/* Stat 2 — Uptime */}
                    <div className="stat-card p-5 rounded-2xl" style={{ gridArea: 'stat2', background: 'var(--card)', border: '1px solid var(--border)' }}>
                        <span className="text-xs font-semibold uppercase tracking-widest" style={{ color: 'var(--text-3)' }}>Tiempo de Actividad</span>
                        <div className="mt-3">
                            <span className="text-4xl font-bold" style={{ color: 'var(--blue)' }}>99.8%</span>
                        </div>
                        <p className="text-xs mt-3" style={{ color: 'var(--text-3)' }}>Últimos 30 días</p>
                    </div>

                    {/* Stat 3 — TPS */}
                    <div className="stat-card p-5 rounded-2xl" style={{ gridArea: 'stat3', background: 'var(--card)', border: '1px solid var(--border)' }}>
                        <span className="text-xs font-semibold uppercase tracking-widest" style={{ color: 'var(--text-3)' }}>TPS Promedio</span>
                        <div className="mt-3">
                            <span className="text-4xl font-bold" style={{ color: 'var(--orange)' }}>19.8</span>
                        </div>
                        <p className="text-xs mt-3" style={{ color: 'var(--text-3)' }}>Máximo: 20 TPS</p>
                    </div>

                    {/* Top Jugadores */}
                    <div className="p-5 rounded-2xl flex flex-col" style={{ gridArea: 'players', background: 'var(--card)', border: '1px solid var(--border)' }}>
                        <div className="flex items-center gap-2 mb-4">
                            <div className="w-5 h-5 rounded-md flex items-center justify-center" style={{ background: 'rgba(194,84,90,0.15)' }}>
                                <Skull size={12} style={{ color: 'var(--red)' }} />
                            </div>
                            <span className="text-sm font-semibold" style={{ color: 'var(--text-1)' }}>Top Jugadores</span>
                        </div>
                        <div className="flex-1">
                            {topPlayers.map((player, i) => (
                                <div key={player.id}
                                     className={`list-row flex items-center justify-between py-3 ${i < topPlayers.length - 1 ? 'border-b' : ''}`}
                                     style={{ borderColor: 'var(--border-sub)' }}>
                                    <div className="flex items-center gap-3">
                                        <span className="text-xs font-bold w-4 text-center" style={{ color: 'var(--text-3)' }}>#{i + 1}</span>
                                        <div className="relative">
                                            <div className="w-8 h-8 rounded-full" style={{ background: 'var(--border)' }} />
                                            <div className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 ${player.online ? 'badge-online' : ''}`}
                                                 data-tooltip={player.online ? 'Online' : 'Offline'}
                                                 style={{ background: player.online ? 'var(--green)' : 'var(--red)', borderColor: 'var(--card)' }} />
                                        </div>
                                        <span className="text-sm font-medium" style={{ color: 'var(--text-1)' }}>{player.name}</span>
                                    </div>
                                    <span className="text-sm font-semibold tabular-nums" style={{ color: 'var(--red)' }}>
                                        {player.kills.toLocaleString()} <span style={{ color: 'var(--text-3)', fontWeight: 400 }}>kills</span>
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Eventos Recientes */}
                    <div className="p-5 rounded-2xl flex flex-col" style={{ gridArea: 'events', background: 'var(--card)', border: '1px solid var(--border)' }}>
                        <div className="flex items-center gap-2 mb-4">
                            <div className="w-5 h-5 rounded-md flex items-center justify-center text-xs font-black"
                                 style={{ background: 'rgba(90,159,212,0.15)', color: 'var(--blue)' }}>!</div>
                            <span className="text-sm font-semibold" style={{ color: 'var(--text-1)' }}>Eventos Recientes</span>
                        </div>
                        <div className="events-scroll flex-1 pr-1">
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr' }}>
                                {recentEvents.map((event, i) => (
                                    <div key={event.id}
                                         className={`list-row flex items-center gap-2.5 py-2.5 mx-1 ${i < recentEvents.length - 2 ? 'border-b' : ''}`}
                                         style={{ borderColor: 'var(--border-sub)' }}>
                                        <EventIcon type={event.type} />
                                        <div className="min-w-0">
                                            <p className="text-sm truncate" style={{ color: 'var(--text-1)' }}>{event.text}</p>
                                            <p className="text-xs mt-0.5 tabular-nums" style={{ color: 'var(--text-3)' }}>{event.time}</p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>

                    {/* Top Items */}
                    <div className="p-5 rounded-2xl" style={{ gridArea: 'items', background: 'var(--card)', border: '1px solid var(--border)' }}>
                        <div className="flex items-center gap-2 mb-4">
                            <div className="w-5 h-5 rounded-md flex items-center justify-center text-xs"
                                 style={{ background: 'rgba(201,125,62,0.15)', color: 'var(--orange)' }}>⚔</div>
                            <span className="text-sm font-semibold" style={{ color: 'var(--text-1)' }}>Top Items</span>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: '10px' }}>
                            {topItems.map(item => (
                                <div key={item.id} className="list-row flex items-center gap-3 p-3 rounded-xl"
                                     style={{ background: 'var(--card-hover)', border: '1px solid var(--border-sub)' }}>
                                    <PlaceholderIcon />
                                    <div className="min-w-0">
                                        <p className="text-sm font-medium truncate" style={{ color: 'var(--text-1)' }}>{item.name}</p>
                                        <p className="text-xs mt-0.5 tabular-nums" style={{ color: 'var(--orange)' }}>{item.value}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Top Bloques */}
                    <div className="p-5 rounded-2xl" style={{ gridArea: 'blocks', background: 'var(--card)', border: '1px solid var(--border)' }}>
                        <div className="flex items-center gap-2 mb-4">
                            <div className="w-5 h-5 rounded-md flex items-center justify-center text-xs"
                                 style={{ background: 'rgba(90,159,212,0.15)', color: 'var(--blue)' }}>▦</div>
                            <span className="text-sm font-semibold" style={{ color: 'var(--text-1)' }}>Top Bloques</span>
                        </div>
                        <div>
                            {topBlocks.map((block, i) => (
                                <div key={block.id}
                                     className={`list-row flex items-center justify-between py-3 ${i < topBlocks.length - 1 ? 'border-b' : ''}`}
                                     style={{ borderColor: 'var(--border-sub)' }}>
                                    <div className="flex items-center gap-3">
                                        <PlaceholderIcon />
                                        <span className="text-sm" style={{ color: 'var(--text-1)' }}>{block.name}</span>
                                    </div>
                                    <div className="text-right">
                                        <span className="text-sm font-semibold tabular-nums" style={{ color: 'var(--blue)' }}>{block.value}</span>
                                        <p className="text-xs" style={{ color: 'var(--text-3)' }}>colocados</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                </div>
            </main>
        </div>
    );
}
