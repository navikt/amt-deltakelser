# amt-deltaker

## Utvikling
**Lint fix:** 
```
./gradlew ktlintFormat build
```
**Build:**
```
./gradlew build
```

## Oppdatering av deltakere
Deltakere som er f.eks. feilregistrert eller der det finnes nyere deltakelser på samme tiltak låses for oppdateringer
i amt-deltaker-bff. Hvis man trenger å tvinge gjennom oppdateringer også på disse deltakerne, f.eks. ved oppdatering av 
format, kan man sette flagget `forcedUpdate` til `true` når man produserer til deltaker-v2-topicen. 