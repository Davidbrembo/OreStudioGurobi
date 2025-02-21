Gurobi è un potente solver di ottimizzazione matematica utilizzato per risolvere problemi di programmazione lineare (LP), 
programmazione lineare intera mista (MILP), programmazione quadratica (QP) e altre varianti. È ampiamente utilizzato in
settori come la logistica, la finanza e l'intelligenza artificiale per trovare soluzioni ottimali a problemi complessi.

## Setup & Installazione

1. Configurare Gurobi:  
    - Installare il solver Gurobi dalla [pagina ufficiale](https://www.gurobi.com/)
    - Aggiungere le librerie Gurobi al progetto Java

2. Importare Gurobi nel progetto Java:
    - Aggiungere `gurobi.jar` al classpath del progetto.
    - Se si utilizza un IDE come IntelliJ IDEA o Eclipse:
        - IntelliJ IDEA: Andare su **File > Project Structure > Libraries > Add JARs** e selezionare `gurobi.jar`.
        - Eclipse: Fare clic con il tasto destro sul progetto, selezionare **Properties > Java Build Path > Libraries > Add External JARs** e scegliere `gurobi.jar`.
    - Se si compila da terminale, includere `gurobi.jar` nel classpath.
