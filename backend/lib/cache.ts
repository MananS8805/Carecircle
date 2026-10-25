type Entry<T> = { value: T; expires: number };
export class TtlCache<T> {
  private m = new Map<string, Entry<T>>();
  constructor(private ttlMs: number, private max = 500) {}
  get(k: string, now = Date.now()): T | undefined {
    const e = this.m.get(k);
    if (!e) return undefined;
    if (e.expires < now) { this.m.delete(k); return undefined; }
    return e.value;
  }
  set(k: string, v: T, now = Date.now()) {
    if (this.m.size >= this.max) this.m.clear();
    this.m.set(k, { value: v, expires: now + this.ttlMs });
  }
}
