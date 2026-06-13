export class ReconnectBackoff {
  private delayMs = 1000;

  constructor(
    private readonly maxDelayMs = 32000,
    private readonly jitterMs = 1000,
  ) {}

  nextDelay(): number {
    const delay = this.delayMs + Math.floor(Math.random() * this.jitterMs);
    this.delayMs = Math.min(this.delayMs * 2, this.maxDelayMs);
    return delay;
  }

  reset() {
    this.delayMs = 1000;
  }
}
