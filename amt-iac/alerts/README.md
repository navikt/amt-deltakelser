# Alerts
Her defineres Prometheus alerts som benyttes til driftsovervåking av applikasjonene til team Komet.

Oppsettet er basert på denne oppskriften: [NAIS How-To](https://docs.nais.io/observability/alerting/how-to/prometheus-basic).

## Alertmanager
Alertmanager bestemmer hva som skal skje om plattformen fyrer av alerts. Alerts som defineres i plattformen vil dukke
i Alertmanager sitt brukergrensesnitt.
* [Alertmanager Prod](https://alertmanager.prod-gcp.nav.cloud.nais.io)
* [Alertmanager Dev](https://alertmanager.dev-gcp.nav.cloud.nais.io)

### Custom Alertmanager
Det benyttes en custom Alertmanager. Dette er gjort for å kunne bestemme format og oppførsel for alerts.

Det som bestemmer om en alert skal håndteres av custom Alertmanager er en label i hver
[alert-definisjon](./prod/prometheus-alerts.yaml).

```yaml
- name: amt-application-alerts
  rules:
    - alert: LogsErrorRate
      # ...
      labels:
        namespace: amt
        severity: warning
        alert_type: custom # <- Gjør at custom Alertmanager benyttes
```
Fjern labelen `alert_type` om standard Alertmanager skal benyttes.

## Slack-varslinger

Alerts som fyrer vil bli sendt som varsler til en Slack-kanal av Alertmanager.

* Prod: [#team_komet_alerts](https://nav-it.slack.com/archives/C02K39BFSNM)

Hvilke Slack-kanaler som skal varsles styres av Alertmanager.

For custom Alertmanager er disse definert i [deployment-filen](./prod/prometheus-alertmanager.yaml) i dette repoet.

For standard Alertmanager er disse definert i [NAIS Console](https://console.nav.cloud.nais.io/team/amt/settings).

### Format for Slack-varslinger

Når det oppstår en feil som gjør at en alert fyrer så ser varslet i Slack slik ut:

> **KOMET alertmanager nav-prod-gcp**<br>
> &nbsp;&nbsp;**[FIRING:1] CrashLoopBackoff**<br>
> &nbsp;&nbsp;**Severity:** `WARNING`<br>
> &nbsp;&nbsp;**Title:** POD `amt-deltaker` starter ikke<br>
> &nbsp;&nbsp;**Summary:** Det er en feil som gjør at PODen `amt-deltaker` ikke starter.<br>
> &nbsp;&nbsp;**Action:** Sjekk loggen i Elastic eller i en terminal med `kubectl logs -l app=amt-deltaker`

Når feilen er rettet så ser varselet i Slack slik ut:

> **KOMET alertmanager nav-prod-gcp**<br>
> &nbsp;&nbsp;**[RESOLVED] CrashLoopBackoff**<br>
> &nbsp;&nbsp;**Title:** POD `amt-deltaker` kjører som normalt