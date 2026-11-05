import { Injectable } from '@angular/core';
import { InventoryItem } from '../core/models';

const HEADERS = [
  'Nombre',
  'Categoría',
  'Ubicación',
  'Tipo',
  'Formato',
  'Unidades totales',
  'Disponibles',
  'En uso',
  'Visibilidad',
  'Estado',
  'Descripción',
];

function cell(value: string | number | null | undefined): string {
  const text = String(value ?? '');
  // Spreadsheet-oriented export: prefix potential formulas, including full-width signs.
  const dangerous = /^[\s\u200B-\u200D\u2060]*[=+\-@＝＋－＠]|^[\t\r\n]/u.test(text);
  const safe = dangerous ? '\t' + text : text;
  return '"' + safe.replace(/"/g, '""') + '"';
}

export function inventoryCsv(items: readonly InventoryItem[]): string {
  const rows = items.map((item) => [
    item.name,
    item.category,
    item.location?.path ?? 'Sin ubicación',
    item.kind === 'REUSABLE' ? 'Reutilizable' : 'Consumible',
    item.individual ? 'Individual' : 'Por unidades',
    item.quantity,
    item.availableQuantity,
    item.inUseQuantity,
    item.visibility === 'PRIVATE' ? 'Privado' : 'Compartido',
    item.archived ? 'Archivado' : 'Activo',
    item.description,
  ]);
  return '\uFEFF' + [HEADERS, ...rows].map((row) => row.map(cell).join(';')).join('\r\n') + '\r\n';
}

@Injectable({ providedIn: 'root' })
export class InventoryCsvExport {
  download(items: readonly InventoryItem[]): void {
    const blob = new Blob([inventoryCsv(items)], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    try {
      link.href = url;
      link.download = `inventario-${new Date().toISOString().slice(0, 10)}.csv`;
      link.hidden = true;
      document.body.appendChild(link);
      link.click();
    } finally {
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  }
}
