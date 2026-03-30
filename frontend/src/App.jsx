import React from 'react';
import { Skull, UserPlus, LogOut, Hammer } from 'lucide-react';

export default function App() {
    // Datos de ejemplo basados en tu diseño
    const topPlayers = [
        { id: 1, name: 'Player1', kills: 1250, online: true },
        { id: 2, name: 'Player2', kills: 1100, online: true },
        { id: 3, name: 'Player3', kills: 980, online: false },
    ];

    const recentEvents = [
        { id: 1, type: 'kill', icon: <Skull size={20} className="text-red-500" />, text: 'Player1 eliminó a Player4', time: 'hace 2m' },
        { id: 2, type: 'join', icon: <UserPlus size={20} className="text-green-500" />, text: 'Player5 se unió al servidor', time: 'hace 5m' },
        { id: 3, type: 'leave', icon: <LogOut size={20} className="text-yellow-500" />, text: 'Player2 salió del servidor', time: 'hace 10m' },
        { id: 4, type: 'build', icon: <Hammer size={20} className="text-blue-500" />, text: 'Player6 construyó un Castillo', time: 'hace 15m' },
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

    // Componente auxiliar para el placeholder cuadrado con X
    const PlaceholderIcon = () => (
        <div className="w-10 h-10 border-2 border-gray-600 rounded flex items-center justify-center relative overflow-hidden bg-[#2a2a2a]">
            <div className="absolute w-[140%] h-[2px] bg-gray-600 rotate-45"></div>
            <div className="absolute w-[140%] h-[2px] bg-gray-600 -rotate-45"></div>
        </div>
    );

    return (
        <div className="min-h-screen bg-[#121212] text-gray-200 font-sans selection:bg-gray-700">

            {/* Barra de Navegación */}
            <nav className="flex items-center justify-between px-8 py-4 bg-[#1e1e1e] border-b border-gray-800">
                <div className="flex space-x-6 text-sm font-medium">
                    <a href="#" className="text-white font-bold">Inicio</a>
                    <a href="#" className="text-gray-400 hover:text-white transition-colors">Mapas</a>
                    <a href="#" className="text-gray-400 hover:text-white transition-colors">Jugadores</a>
                    <a href="#" className="text-gray-400 hover:text-white transition-colors">Clanes</a>
                    <a href="#" className="text-gray-400 hover:text-white transition-colors">Estadísticas</a>
                    <a href="#" className="text-gray-400 hover:text-white transition-colors">Rankings</a>
                    <a href="#" className="text-gray-400 hover:text-white transition-colors">Historial</a>
                </div>
                <div className="flex items-center space-x-3">
                    <div className="w-8 h-8 bg-gray-600 rounded-full"></div>
                    <span className="text-sm font-medium text-gray-300">NombreJugador</span>
                </div>
            </nav>

            {/* Contenido Principal */}
            <main className="p-8 max-w-[1400px] mx-auto space-y-6">

                {/* Título */}
                <h1 className="text-3xl font-bold text-white mb-8">Panel de Control del Servidor</h1>

                {/* Fila 1: Tarjetas de Estadísticas Rápidas */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    <div className="bg-[#1e1e1e] p-6 rounded-xl shadow-md flex flex-col justify-center">
                        <h3 className="text-gray-300 font-medium mb-2">Jugadores Online</h3>
                        <div className="flex items-baseline space-x-2">
                            <span className="text-5xl font-bold text-[#4ade80]">128</span>
                            <span className="text-2xl font-medium text-gray-500">/ 250</span>
                        </div>
                    </div>

                    <div className="bg-[#1e1e1e] p-6 rounded-xl shadow-md flex flex-col justify-center">
                        <h3 className="text-gray-300 font-medium mb-2">Tiempo de Actividad</h3>
                        <span className="text-5xl font-bold text-[#60a5fa]">99.8%</span>
                    </div>

                    <div className="bg-[#1e1e1e] p-6 rounded-xl shadow-md flex flex-col justify-center">
                        <h3 className="text-gray-300 font-medium mb-2">TPS Promedio</h3>
                        <span className="text-5xl font-bold text-[#fb923c]">19.8</span>
                    </div>
                </div>

                {/* Fila 2: Rankings PvP y Eventos */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

                    {/* Top Jugadores */}
                    <div className="bg-[#1e1e1e] p-6 rounded-xl shadow-md">
                        <h3 className="text-lg font-bold text-white mb-6">Top Jugadores (Kills)</h3>
                        <div className="space-y-0">
                            {topPlayers.map((player, index) => (
                                <div key={player.id} className={`flex items-center justify-between py-4 ${index !== topPlayers.length - 1 ? 'border-b border-gray-800' : ''}`}>
                                    <div className="flex items-center space-x-4">
                                        <div className="relative">
                                            <div className="w-10 h-10 bg-gray-600 rounded-full"></div>
                                            {/* Indicador de estado */}
                                            <div className={`absolute bottom-0 right-0 w-3.5 h-3.5 rounded-full border-2 border-[#1e1e1e] ${player.online ? 'bg-[#4ade80]' : 'bg-red-500'}`}></div>
                                        </div>
                                        <span className="text-gray-200 font-medium">{player.name}</span>
                                    </div>
                                    <span className="text-[#ef4444] font-bold">{player.kills} Kills</span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Eventos Recientes */}
                    <div className="bg-[#1e1e1e] p-6 rounded-xl shadow-md">
                        <h3 className="text-lg font-bold text-white mb-6">Eventos Recientes</h3>
                        <div className="space-y-0">
                            {recentEvents.map((event, index) => (
                                <div key={event.id} className={`flex items-center py-4 ${index !== recentEvents.length - 1 ? 'border-b border-gray-800' : ''}`}>
                                    <div className="mr-4 w-6 flex justify-center">{event.icon}</div>
                                    {/* Para simular los avatares integrados en el texto como en el diseño */}
                                    <div className="flex-1 flex items-center text-gray-300 text-sm">
                                        {event.type === 'kill' && (
                                            <>
                                                <div className="w-6 h-6 bg-gray-600 rounded-full mx-2"></div>
                                                Player1 eliminó a
                                                <div className="w-6 h-6 bg-gray-600 rounded-full mx-2"></div>
                                                Player4
                                            </>
                                        )}
                                        {event.type === 'join' && (
                                            <>
                                                <div className="w-6 h-6 bg-gray-600 rounded-full mx-2"></div>
                                                Player5 se unió al servidor
                                            </>
                                        )}
                                        {event.type === 'leave' && (
                                            <>
                                                <div className="w-6 h-6 bg-gray-600 rounded-full mx-2"></div>
                                                Player2 salió del servidor
                                            </>
                                        )}
                                        {event.type === 'build' && (
                                            <>
                                                <div className="w-6 h-6 bg-gray-600 rounded-full mx-2"></div>
                                                Player6 construyó un Castillo
                                            </>
                                        )}
                                    </div>
                                    <span className="text-xs text-gray-500 whitespace-nowrap ml-4">{event.time}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Fila 3: Top Items y Bloques */}
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

                    {/* Top Items */}
                    <div className="bg-[#1e1e1e] p-6 rounded-xl shadow-md">
                        <h3 className="text-lg font-bold text-white mb-6">Top Items</h3>
                        <div className="space-y-0">
                            {topItems.map((item, index) => (
                                <div key={item.id} className={`flex items-center justify-between py-4 ${index !== topItems.length - 1 ? 'border-b border-gray-800' : ''}`}>
                                    <div className="flex items-center space-x-4">
                                        <PlaceholderIcon />
                                        <span className="text-gray-200">{item.name}</span>
                                    </div>
                                    <span className="text-[#fb923c] font-medium">{item.value}</span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Top Bloques */}
                    <div className="bg-[#1e1e1e] p-6 rounded-xl shadow-md">
                        <h3 className="text-lg font-bold text-white mb-6">Top Bloques</h3>
                        <div className="space-y-0">
                            {topBlocks.map((block, index) => (
                                <div key={block.id} className={`flex items-center justify-between py-4 ${index !== topBlocks.length - 1 ? 'border-b border-gray-800' : ''}`}>
                                    <div className="flex items-center space-x-4">
                                        <PlaceholderIcon />
                                        <span className="text-gray-200">{block.name}</span>
                                    </div>
                                    <span className="text-[#60a5fa] font-medium">{block.value}</span>
                                </div>
                            ))}
                        </div>
                    </div>

                </div>

            </main>
        </div>
    );
}