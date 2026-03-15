# Grafana Dashboard

## Import

1. Open Grafana → Dashboards → Import
2. Upload `grafana-dashboard.json` or paste its contents
3. Select your Prometheus data source
4. Click Import

## Panels

| Panel | Type | Description |
|---|---|---|
| **Request Rate** | Time series | Allowed vs rejected requests per second, stacked |
| **Rejection Rate by Policy** | Time series | Per-policy rejection rate to identify hot policies |
| **Evaluation Latency** | Time series | p50/p95/p99 of rate limit evaluation time |
| **Backend Errors** | Stat | Redis error count over 5 minutes (green/yellow/red thresholds) |
| **Shadow Mode Would-Reject** | Stat | Observe-mode rejections over 5 minutes |
| **Allowed vs Rejected** | Pie chart | Hourly ratio of allowed to rejected requests |
| **Top Rejected Policies** | Table | Top 10 policies by rejection count in the last hour |
| **Request Rate by Policy** | Time series | Per-policy request rate for capacity planning |

## Dashboard Layout

```
┌──────────────────────────┬──────────────────────────┐
│   Request Rate (req/s)   │  Rejection Rate by Policy│
│   [allowed vs rejected]  │  [per policy bars]       │
├──────────────────────────┼─────────────┬────────────┤
│  Evaluation Latency      │  Backend    │  Shadow    │
│  [p50/p95/p99]           │  Errors     │  Would-    │
│                          │  [stat]     │  Reject    │
├──────────────────────────┼─────────────┼────────────┤
│  Request Rate by Policy  │  Allowed vs │  Top       │
│  [per policy lines]      │  Rejected   │  Rejected  │
│                          │  [pie]      │  Policies  │
└──────────────────────────┴─────────────┴────────────┘
```

## Alerting Recommendations

| Alert | Condition | Severity |
|---|---|---|
| High rejection rate | `rate(rate_limiter_rejected_total[5m]) > 10` | Warning |
| Backend errors | `rate(rate_limiter_errors_total[1m]) > 0` | Critical |
| High evaluation latency | `histogram_quantile(0.99, ...) > 0.01` | Warning |
| Shadow mode spikes | `rate(rate_limiter_observed_would_reject_total[5m]) > 50` | Info |
