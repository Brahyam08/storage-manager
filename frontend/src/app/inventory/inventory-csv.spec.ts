import { InventoryItem } from '../core/models';
import { InventoryCsvExport, inventoryCsv } from './inventory-csv';

const item: InventoryItem = {
  id: 1,
  houseId: 1,
  ownerId: 7,
  name: 'Tornillos M3',
  category: 'Ferretería',
  location: null,
  kind: 'CONSUMABLE',
  quantity: 5,
  inUseQuantity: 0,
  availableQuantity: 5,
  visibility: 'PRIVATE',
  outOfStock: false,
  description: 'Acero',
};

describe('Inventory CSV', () => {
  it('exports UTF-8 Spanish labels and preserves unassigned, archived and individual states', () => {
    const csv = inventoryCsv([
      {
        ...item,
        kind: 'REUSABLE',
        individual: true,
        archived: true,
        quantity: 1,
        availableQuantity: 1,
      },
    ]);
    expect(csv.startsWith('\uFEFF"Nombre";"Categoría"')).toBe(true);
    expect(csv).toContain(
      '"Sin ubicación";"Reutilizable";"Individual";"1";"1";"0";"Privado";"Archivado"',
    );
    expect(csv.endsWith('\r\n')).toBe(true);
  });

  it('escapes separators, quotes, line breaks and Unicode without creating extra cells', () => {
    const csv = inventoryCsv([
      { ...item, name: 'Cajón; "M3"\r\nñ', description: 'Descripción; "especial"' },
    ]);
    expect(csv).toContain('"Cajón; ""M3""\r\nñ"');
    expect(csv).toContain('"Descripción; ""especial"""');
  });

  it('prefixes spreadsheet formulas including leading whitespace, controls and full-width signs', () => {
    for (const value of [
      '=1+1',
      '+SUM(A1)',
      '-1',
      '@SUM(A1)',
      '  =1',
      '\t=1',
      '\r=1',
      '\n=1',
      '\uFEFF=1',
      '\u200B=1',
      '＝1',
      '＋1',
      '－1',
      '＠SUM(A1)',
    ]) {
      const csv = inventoryCsv([{ ...item, name: value }]);
      expect(csv).toContain('"\t' + value + '"');
    }
    expect(inventoryCsv([item])).toContain('"Tornillos M3"');
  });

  it('does not export account identifiers or unexpected model properties', () => {
    const csv = inventoryCsv([
      { ...item, ownerId: 123456789, sessionToken: 'fixture-private-marker' } as InventoryItem,
    ]);
    expect(csv).not.toContain('123456789');
    expect(csv).not.toContain('fixture-private-marker');
  });

  it('creates a local CSV download and releases its URL after the browser can start reading it', () => {
    vi.useFakeTimers();
    const createUrl = vi.fn((_value: Blob | MediaSource) => 'blob:csv-fixture');
    const revokeUrl = vi.fn();
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = createUrl;
        static override revokeObjectURL = revokeUrl;
      },
    );
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toMatch(/^inventario-\d{4}-\d{2}-\d{2}\.csv$/);
      expect(this.href).toBe('blob:csv-fixture');
    });
    try {
      new InventoryCsvExport().download([item]);
      expect(click).toHaveBeenCalledOnce();
      expect(createUrl.mock.calls[0][0]).toBeInstanceOf(Blob);
      expect((createUrl.mock.calls[0][0] as Blob).type).toBe('text/csv;charset=utf-8');
      expect(document.querySelector('a[download]')).toBeNull();
      expect(revokeUrl).not.toHaveBeenCalled();
      vi.runAllTimers();
      expect(revokeUrl).toHaveBeenCalledExactlyOnceWith('blob:csv-fixture');
    } finally {
      vi.useRealTimers();
      vi.restoreAllMocks();
      vi.unstubAllGlobals();
    }
  });
});
