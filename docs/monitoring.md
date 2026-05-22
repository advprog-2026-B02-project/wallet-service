# Wallet Service Monitoring

## Identitas

| Item | Nilai |
| --- | --- |
| Kelompok | B02 |
| Anggota | TODO: isi nama anggota kelompok sebelum submit |

## Link Repository

| Service | Repository |
| --- | --- |
| Auth service | https://github.com/advprog-2026-B02-project/BidMart-Auth-Service |
| Bidding service | https://github.com/advprog-2026-B02-project/bidmart-bidding-service |
| Catalog service | https://github.com/advprog-2026-B02-project/catalog-service |
| Wallet service | https://github.com/advprog-2026-B02-project/wallet-service |
| Order service | https://github.com/advprog-2026-B02-project/bidmart-order-service |
| Notification service | https://github.com/advprog-2026-B02-project/bidmart-notification-service |
| Frontend | https://github.com/advprog-2026-B02-project/bidmart-frontend |

## Link Staging

| Service | Staging URL |
| --- | --- |
| Auth service | TODO |
| Bidding service | TODO |
| Catalog service | TODO |
| Wallet service | TODO |
| Order service | TODO |
| Notification service | TODO |
| Frontend | TODO |

## Link Progress

| Bukti | Link |
| --- | --- |
| Commit pribadi monitoring | TODO: isi setelah push, format `https://github.com/advprog-2026-B02-project/wallet-service/commit/<sha>` |
| Branch pribadi 100% milestone | TODO: isi setelah push, format `https://github.com/advprog-2026-B02-project/wallet-service/tree/<branch>` |
| Bukti profiling | TODO: isi link screenshot/report k6/Grafana setelah profiling |

## Desain Monitoring

Monitoring wallet-service memakai Spring Boot Actuator, Micrometer Prometheus registry, Prometheus, dan Grafana.

Justifikasi:

- Actuator dan Micrometer adalah integrasi native Spring Boot, sehingga metrik HTTP, JVM, HikariCP, dan health check tersedia tanpa instrumentasi manual besar.
- Prometheus memakai model pull via `/actuator/prometheus`, cocok untuk Docker Compose dan staging karena scrape target cukup diarahkan ke service name internal.
- Grafana dipakai untuk dashboard karena query PromQL bisa langsung divisualisasikan dan mudah dijadikan bukti monitoring.
- Metrik domain wallet dipisahkan dari metrik HTTP. HTTP metric menjawab performa endpoint, sedangkan domain metric menjawab operasi bisnis seperti top up, hold, capture, release, dan settlement.
- Endpoint publik dibatasi ke `/actuator/health`, `/actuator/info`, dan `/actuator/prometheus`. Endpoint bisnis tetap melalui autentikasi yang sudah ada.

Metrik utama:

| Metric | Tipe | Tujuan |
| --- | --- | --- |
| `wallet_operation_total` | Counter | Menghitung operasi domain wallet berdasarkan `operation`, `outcome`, dan `exception`. |
| `wallet_operation_duration_seconds` | Timer histogram | Melihat latency p95 operasi domain wallet. |
| `wallet_wallets` | Gauge | Melihat jumlah wallet yang tercatat. |
| `wallet_wallets_frozen` | Gauge | Melihat jumlah wallet yang sedang dibekukan. |
| `wallet_holds_active` | Gauge | Melihat jumlah hold saldo aktif. |
| `http_server_requests_seconds_*` | Timer histogram | Melihat throughput dan latency endpoint HTTP. |
| `jvm_memory_used_bytes` | Gauge | Melihat konsumsi memori JVM. |
| `hikaricp_connections` | Gauge | Melihat penggunaan koneksi database. |

## Cara Menjalankan

Jalankan wallet-service beserta Prometheus dan Grafana:

```bash
docker compose --profile monitoring up --build
```

Akses:

| Tool | URL |
| --- | --- |
| Wallet health | http://localhost:8084/actuator/health |
| Wallet Prometheus metrics | http://localhost:8084/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |

Credential Grafana default lokal: `admin` / `admin`. Dashboard otomatis terprovision di folder `BidMart` dengan nama `BidMart Wallet Service`.

Contoh query PromQL:

```promql
up{job="wallet-service"}
sum by (operation, outcome) (rate(wallet_operation_total{job="wallet-service"}[5m]))
histogram_quantile(0.95, sum by (le, operation) (rate(wallet_operation_duration_seconds_bucket{job="wallet-service"}[5m])))
wallet_holds_active{job="wallet-service"}
sum by (method, uri, status) (rate(http_server_requests_seconds_count{job="wallet-service"}[5m]))
```

## Proses Profiling

Profiling yang disarankan memakai k6 untuk memberi beban HTTP yang konsisten, lalu Grafana/Prometheus untuk membaca dampaknya pada latency, error rate, JVM memory, dan DB pool.

Alasan proses:

- k6 memberi beban terukur dengan jumlah virtual user dan durasi tetap.
- Endpoint wallet yang diprofilkan adalah endpoint baca yang umum dipakai user: wallet summary dan transaction history.
- Metrik Prometheus memperlihatkan kondisi service dari sisi server, bukan hanya hasil client-side k6.

Langkah:

```bash
docker compose --profile monitoring up --build
BASE_URL=http://localhost:3000 BIDMART_SESSION=<session-cookie> k6 run ../tests/perf/wallet-load.js
```

Bukti yang perlu disimpan:

- Output terminal k6 yang memuat `http_req_duration`, `http_req_failed`, dan threshold.
- Screenshot dashboard Grafana panel HTTP request rate, HTTP p95 latency, JVM memory, dan DB connection pool selama test.
- Link commit monitoring dan branch milestone setelah perubahan dipush.

Analisis improvement awal:

- Jika `http_server_requests_seconds` p95 tinggi saat transaction history dibuka, tambahkan indeks pada `wallet_transactions(wallet_id, created_at desc)`.
- Jika `hikaricp_connections` sering menyentuh maksimum, sesuaikan pool size dan cek query lambat di repository.
- Jika `wallet_operation_total{outcome="failure"}` naik pada operasi hold/capture, audit flow idempotency dan retry antar service.
- Jika `jvm_memory_used_bytes` terus naik setelah load test selesai, ambil heap dump untuk mencari object retention.
