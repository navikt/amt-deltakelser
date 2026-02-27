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
Deltakere som er f.eks. feilregistrert eller hvor det finnes nyere deltakelser på samme tiltak, låses for oppdateringer
i amt-deltaker-bff. Ved behov for å tvinge gjennom oppdateringer for slike deltakerne, f.eks. ved oppdatering av 
format, kan flagget `forcedUpdate` settes til `true` under publisering til deltaker-v2-topic. 