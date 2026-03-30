import React, { useState } from 'react';
import { LayoutGrid, Users, Clock, Flame, Activity, Skull, Star } from 'lucide-react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';

// Datos de ejemplo para que no se vea vacío
const itemData = [
    { name: 'Piedra', value: 400, color: '#7a7a7a' },
    { name: 'Madera', value: 300, color: '#5d3f2e' },
    { name: 'Diamante', value: 100, color: '#22d3ee' },
];

export default function App() {
    const [isLoggedIn, setIsLoggedIn] = useState(false);

    if (!isLoggedIn) {
        return (
            <div className="min-h-screen bg-mc-dark flex items-center justify-center p-4">
                <div className="bg-mc-card p-8 rounded-2xl border border-white/10 w-full max-w-md text-center shadow-2xl">
                    <LayoutGrid size={48} className="text-mc-green mx-auto mb-4" />
                    <h1 className="text-3xl font-bold text-white mb-6 tracking-tighter">ARCRAFT</h1>
                    <button
                        onClick={() => setIsLoggedIn(true)}
                        className="w-full py-3 bg-mc-green hover:bg-green-400 text-black font-bold rounded-lg transition-all"
                    >
                        Iniciar Sesión con Microsoft
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-mc-dark text-white p-6">
            <nav className="mb-8 flex justify-between items-center border-b border-white/5 pb-4">
                <div className="flex items-center gap-2">
                    <LayoutGrid className="text-mc-green" />
                    <span className="font-black italic">ARCRAFT</span>
                </div>
                <div className="flex gap-4 text-sm text-gray-400">
                    <span className="text-mc-green border-b border-mc-green font-bold">Dashboard</span>
                    <span>Jugadores</span>
                    <span>Clanes</span>
                </div>
            </nav>

            <div className="grid grid-cols-12 gap-6 max-w-7xl mx-auto">
                <header className="col-span-12 mb-4">
                    <h2 className="text-3xl font-bold">Bienvenido, <span className="text-mc-green">Admin</span></h2>
                </header>

                {/* Gráfico */}
                <div className="col-span-12 lg:col-span-6 bg-mc-card p-6 rounded-2xl border border-white/5">
                    <h3 className="mb-4 font-bold flex items-center gap-2"><Activity size={18}/> Items Crafteados</h3>
                    <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                                <Pie data={itemData} innerRadius={60} outerRadius={80} dataKey="value">
                                    {itemData.map((entry, index) => <Cell key={index} fill={entry.color} />)}
                                </Pie>
                                <Tooltip contentStyle={{backgroundColor: '#1a1a1a', border: 'none'}} />
                            </PieChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Logs */}
                <div className="col-span-12 lg:col-span-6 bg-mc-card rounded-2xl border border-white/5 overflow-hidden">
                    <div className="p-4 bg-white/5 font-bold">Eventos Recientes</div>
                    <div className="p-4 space-y-4">
                        <div className="flex gap-3 items-center text-sm">
                            <Skull className="text-red-500" size={16}/>
                            <span>Steve murió por Lava</span>
                            <span className="ml-auto text-gray-500 text-xs">hace 2m</span>
                        </div>
                        <div className="flex gap-3 items-center text-sm">
                            <Star className="text-yellow-500" size={16}/>
                            <span>Alex encontró Diamantes</span>
                            <span className="ml-auto text-gray-500 text-xs">hace 5m</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}