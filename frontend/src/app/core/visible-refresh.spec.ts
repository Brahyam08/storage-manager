import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { onVisibleRefresh } from './visible-refresh';

const refresh = vi.fn();
@Component({ template: '' })
class RefreshHost {
  constructor() {
    onVisibleRefresh(refresh);
  }
}

describe('Visible page refresh', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    refresh.mockReset();
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('checks visible pages every 15 seconds and cleans up on destruction', () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    const fixture = TestBed.createComponent(RefreshHost);
    vi.advanceTimersByTime(15_000);
    expect(refresh).toHaveBeenCalledTimes(1);
    fixture.destroy();
    vi.advanceTimersByTime(30_000);
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('skips hidden pages and coalesces return events', () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    TestBed.createComponent(RefreshHost);
    vi.advanceTimersByTime(30_000);
    expect(refresh).not.toHaveBeenCalled();
    visibility.mockReturnValue('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});
