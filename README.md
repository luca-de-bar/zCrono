- Permesso richiesto: `zcrono.admin`
- Se **PlaceholderAPI** è presente, viene automaticamente pubblicata l’espansione `zcrono`.
## Configurare una nuova mappa

> Negli esempi uso il nome **piramide**.  
> Sostituisci “piramide” col nome che vuoi dare alla tua mappa.

### 1. Creazione
	/zcrono create piramide
- Crea una nuova mappa con il nome scelto. Il nome deve essere univoco.

### 2. Punto di partenza

Posizionati nel punto desiderato e lancia:
	`/zcrono setstart piramide [radius]`

- Se non specifichi `radius`, viene usato il valore predefinito **0,5 blocchi** (centro del blocco).
- Puoi inserire un raggio intero ≥0 per ampliare l’area fino a un massimo di **5 blocchi**.
- L’area è definita come **cilindro 2D** con tolleranza verticale di 1,5 blocchi.

### 3. Punto di arrivo
Analogo allo start: posizionati sul traguardo e usa:
	`/zcrono setend piramide [radius]`

La mappa è considerata pronta solo se entrambi i punti sono definiti.

### 4. Interrompere il cronometro
Durante una corsa, utilizzando il comando `/zcrono leave` si interrompe il cronometro.

### 5. Impostazione del config.yml
 Il comando `/zcrono reload` ricarica `config.yml` per leggere eventuali modifiche ai messaggi o countdown.
 
In `config.yml` puoi configurare il comportamento di zcrono ed i messaggi.
```yaml
countdownSeconds: 3
startMessage: "&#E43A96⏱ &#545EB6La corsa parte tra {seconds}..."
goMessage: "🏁 GO!"
endMessage: "&aHai completato il percorso in {time}."
endChatMessage: "&aHai completato il percorso in {time}."
leaveSuccessMessage: 'Hai interrotto correttamente la corsa.'  
leaveNoActiveMessage: 'Non ci sono corse attive da interrompere."'
```
- Se countdown è 0, viene saltato lo startMessage e usa subito il goMessage
- Supportati i codici colore `&` e i codici esadecimali `&#RRGGBB`.
- Placeholder disponibili nei messaggi: `{seconds}`, `{map}`, `{player}`, `{time}`.

Mentre nella sezione persistence, si decide se salvare so `data.yml` o database (vanno specificate le credenziali qui)
```
persistence:  
  # Imposta a true per salvare le statistiche su MySQL invece che su data.yml.  
  use-mysql: true  
  mysql:  
    host: localhost  
    port: 3306  
    database: database  
    username: utente  
    password: password  
    use-ssl: true
```

NOTA -> la connessione il plugin la stabilisce solo allo startup, di conseguenza sarà necessaria configurazione e riavvio del server per far si che il plugin si colleghi al db.
## Consultare o modificare le mappe
- `/zcrono map list` -> Mostra i nomi di tutte le mappe configurate
- `/zcrono info piramide` -> Riepiloga posizione e raggio di start e end per la mappa
- `/zcrono delete piramide` -> Elimina la mappa specificata, richiede la conferma entro 30s, questo comporta l'eliminazione anche a database di tutti tempi registrati su questa mappa.

## Funzionamento del cronometro

- Entrando nello **start** parte un **countdown** (o il via immediato se `countdownSeconds=0`).
- Uscire dallo start prima del messaggio “GO!” annulla la partenza.
- Al termine del countdown, il cronometro parte e resta attivo fino all’arrivo all’**end**.
- Tornare sullo start durante una corsa riavvia il countdown.
- I tempi vengono formattati come `mm:ss.mmm` e salvati nel metodo di persistenza scelto nel config.yml

## Persistenza e reset

- Le mappe e le aree vengono salvate in `config.yml`.
- I migliori tempi dei player sulle mappe create, vengono salvati sul `data.yml` o database a seconda di che metodo di persistenza viene scelto dal `config.yml`
- Ogni miglioramento scrive subito il nuovo valore. Non vengono persistiti dati che non battono il record precedente.
- Comandi di reset:
    - `/zcrono resetplayer piramide PlayerNick` → resetta il best di un giocatore.
    - `/zcrono resetmap piramide` → resetta l’intera classifica. richiede conferma entro 30s -> rimuove tutti i tempi dal database.


### PlaceholderAPI disponibili

 **Miglior tempo personale**
`%zcrono_besttime_piramide%` 
Restituisce il miglior tempo del giocatore corrente, o `-` se non ne ha.

**Posizione in classifica**
`%zcrono_rank_piramide%`
Mostra la posizione del giocatore in classifica, o `-` se non classificato.

**Top N della mappa**
```
%zcrono_top_piramide_1%
%zcrono_top_piramide_2%
..
%zcrono_top_piramide_10%
```
Restituiscono “nome - tempo” per le posizioni specificate.

**Top N separato (solo nome o solo tempo)**
```
%zcrono_top_player_piramide_1%
%zcrono_top_time_piramide_1%
```
Danno rispettivamente il nome e il tempo per la posizione indicata.

**Cronometro live**
`%zcrono_live_piramide%`
### Test con `/papi parse`

Esempi di test per la mappa _piramide_:

```
/papi parse Koteyo %zcrono_besttime_piramide%
/papi parse Koteyo %zcrono_rank_piramide%
/papi parse Koteyo %zcrono_top_piramide_1%
/papi parse Koteyo %zcrono_top_player_piramide_1%
/papi parse Koteyo %zcrono_top_time_piramide_1%
/papi parse Koteyo %zcrono_live_piramide%
```

- Best personale di Koteyo.
- Posizione in classifica di Koteyo.
- Top 1 globale della mappa.
- Nome del top 1 separato.
- Tempo del top 1 separato.
- Cronometro live di Koteyo.
