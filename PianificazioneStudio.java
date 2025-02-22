package it.unibs.RO;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Scanner;
import gurobi.GRB;
import gurobi.GRBConstr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;

public class PianificazioneStudio {

	public static void main(String[] args) throws FileNotFoundException {

		try {
			//Lettura dei dati da file txt
			File file = new File("instance-26.txt");
            Scanner scanner = new Scanner(file);

            scanner.next(); // Leggi "d"
            int d = scanner.nextInt();

            scanner.next(); // Leggi "t"
            int[] t = new int[10];
            for (int i = 0; i < 10; i++) {
                t[i] = scanner.nextInt();
            }

            scanner.next(); // Leggi "l"
            int l = scanner.nextInt();

            scanner.next(); // Leggi "tau"
            int[] tau = new int[10];
            for (int i = 0; i < 10; i++) {
                tau[i] = scanner.nextInt();
            }

            scanner.next(); // Leggi "tmax"
            int tmax = scanner.nextInt();

            scanner.next(); // Leggi "k"
            int k = scanner.nextInt();

            scanner.next(); // Leggi "a"
            int a = scanner.nextInt();

            scanner.next(); // Leggi "b"
            int b = scanner.nextInt();

            scanner.next(); // Leggi "c"
            int c = scanner.nextInt();

            scanner.close();
            
            // Variabili decisionali, creazione di environment, modelli e array mono e bidimensionali per stampare i risultati
            GRBVar[][] x = new GRBVar[t.length][d]; //ore per materia
            GRBVar[][] y = new GRBVar[t.length][d]; //bool: faccio o no questa materia in quel giorno?
            GRBEnv env = new GRBEnv("PianificazioneStudio.log");
            GRBModel model = new GRBModel(env);//creazione MPLI
            GRBModel model2 = new GRBModel(env);//creazione MPLI*
            double[][] xDaStampare= new double[t.length][d];//salvataggio dei parametri richiesti per non dover rieseguire l'ottimizzazione
            double[][] yDaStampare= new double[t.length][d];
            ArrayList<String> mpliConstrDaStampare = new ArrayList<>();

            //inizializzazione variabili decisionali
            for (int i = 0; i < t.length; i++) {
                for (int j = 0; j < d; j++) {
                    x[i][j] = model.addVar(0, tmax, 0, GRB.INTEGER, "x_" + i + "_" + j);
                    y[i][j] = model.addVar(0, 1, 0, GRB.BINARY, "y_" + i + "_" + j);
                }
            }

            //modellizzazione: quesito 1
            //funzione obiettivo
            setMaxObjective(model, x, k, d);

            // Vincolo 1: Ore minime per materia (>=)
            addMinHoursConstraints(model, x, t, d);

            //Vincolo  2: avere y=0->x=0, y=1->x>tau
            addStudyConstraints(model, x, y, t, tmax, d);

            // Vincolo 3: Non più di l materie al giorno (<=)
            addMaxSubjectsConstraints(model, y, t, l, d);

            // Vincolo 4: Non più di tmax ore al giorno (<=)
            addMaxHoursConstraints(model, x, t, tmax, d);

            // Vincolo 5: Ore minime se si studia la materia (>=)
            addStudyHoursConstraints(model, x, y, t, tau, d);

            // Ottimizzazione
            model.optimize();
            double mpliObjVal = model.get(GRB.DoubleAttr.ObjVal);
            System.out.println("Valore ottimo della funzione obiettivo (MPLI): " + mpliObjVal);
            
            // Stampa dei valori delle variabili decisionali
            printDecisionVariables(x, y, xDaStampare, yDaStampare, t, d);

            // Stampa dei valori delle variabili di slack/surplus 
            printConstraints(model, mpliConstrDaStampare);


            // Modifica delle variabili per MPL (rilassamento continuo)
            GRBModel rilassato = model.relax();
            setVariablesToContinuous(x, y, t, d);

            // Riesegui l'ottimizzazione per MPL
            rilassato.optimize();


            // Stampa del valore ottimo della funzione obiettivo per MPL
            double mplObjVal = rilassato.get(GRB.DoubleAttr.ObjVal);
            System.out.println("Valore ottimo della funzione obiettivo (MPL): " + mplObjVal);

            // Analisi dei vincoli attivi sul modello rilassato
            printActiveConstraints(rilassato);

            // Verifica se la soluzione del rilassato è degenere
            checkDegeneracy(rilassato);

            // Confronto del MPL col MPLI: se uguagliandoli danno true coincidono
            System.out.println("L'ottimo di MPL " + (mplObjVal == mpliObjVal ? "coincide" : "non coincide") + " con quello di MPLI.");

            // Analisi dell'unicità della soluzione ottima
            checkUniqueness(rilassato);

            //rimetto le variabili intere
            setVariablesToInteger(x, y, t, d);

            //quesito 2
            //creo un MPLI* prendendo le caratteristiche del MPLI iniziale
            model2=model;
            
            //Nuovo vincolo su MPLI* sui giorni consecutivi di una materia
            addConsecutiveDaysConstraints(model2, y, t, d);

            //Nuovo vincolo su MPLI* sulle materie del giorno precedente
            addStudyAConstraints(model2, y, a, b, c, d);

            //eseguo l'ottimizzazione del MPLI*
            model2.update();
            model2.optimize();
            double mpli2ObjVal = model2.get(GRB.DoubleAttr.ObjVal);
            System.out.println("Valore ottimo della funzione obiettivo (MPLI2): " + mpli2ObjVal);
            
            //quesito 3
            //Min e max delta di tau della materia 2, ossia con indice 1
            printSensitivityBounds(rilassato, d);

            //Minimo tmax per cui MPLI* ammette soluzione
            int tmaxDaStampare = findMinimumTmax(model2, x, t, d);

         //Output secondo il formato richiesto
        System.out.println("\n\nGRUPPO Singolo 23");
  		System.out.println("Componente: Brembati\n");
  		System.out.println("\nQUESITO I");
  		System.out.println("Valore ottimo della funzione obiettivo (MPLI): " + mpliObjVal);
  		System.out.println("valori delle variabili decisionali");
  		
  		//stampo le variabili precedentemente immagazzinate
         for (int i = 0; i < t.length; i++) {
             for (int j = 0; j < d; j++) {
                 System.out.println("x[" + i + "][" + j + "]: " + xDaStampare[i][j]);
                 System.out.println("y[" + i + "][" + j + "]: " + yDaStampare[i][j]);
             }
         }

       //stampo le variabili precedentemente immagazzinate
         for(int i=0; i<mpliConstrDaStampare.size(); i++) {
        	 System.out.println(mpliConstrDaStampare.get(i));
         }
         
         System.out.println("\nValore ottimo della funzione obiettivo (MPL): " + mplObjVal);
         
         printActiveConstraints(rilassato);         
         checkDegeneracy(rilassato);
         checkUniqueness(rilassato);
         System.out.println("L'ottimo di MPL " + (mplObjVal == mpliObjVal ? "coincide" : "non coincide") + " con quello di MPLI.");

         System.out.println("\n\nQuesito II");
         System.out.println("Valore ottimo della funzione obiettivo (MPLI2): " + mpli2ObjVal);

         System.out.println("\n\nQuesito III");
         printSensitivityBounds(rilassato, d);
         System.out.println("Il minimo valore intero che tmax può assumere tale che MPLI ammetta soluzione è " + (tmaxDaStampare));


         // Chiusura del modello e dell'ambiente
         disposeModels(rilassato, model, model2, env);


        } catch (GRBException e) {
            System.out.println("Errore: " + e.getMessage());
        }
    }
	//imposto l'obiettivo di studiare il più possibile la materia k
	public static void setMaxObjective(GRBModel model, GRBVar[][] x, int k, int d) {
	    GRBLinExpr obj = new GRBLinExpr();
	    for (int j = 0; j < d; j++) {
	        obj.addTerm(1.0, x[k][j]);
	    }
	    try {
	        model.setObjective(obj, GRB.MAXIMIZE);
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//vincolo di studiare ogni materia almeno un minimo di t ore
	public static void addMinHoursConstraints(GRBModel model, GRBVar[][] x, int[] t, int d) {
	    try {
	        for (int i = 0; i < t.length; i++) {
	            GRBLinExpr expr = new GRBLinExpr();
	            for (int j = 0; j < d; j++) {
	                expr.addTerm(1.0, x[i][j]);
	            }
	            model.addConstr(expr, GRB.GREATER_EQUAL, t[i], "min_hours_" + i);
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//vincolo con cui imposto la variabile binaria y a 1 se studio, 0 altrimenti
	public static void addStudyConstraints(GRBModel model, GRBVar[][] x, GRBVar[][] y, int[] t, double tmax, int d) {
	    try {
	        for (int i = 0; i < t.length; i++) {
	            for (int j = 0; j < d; j++) {
	                GRBLinExpr lhsExpr = new GRBLinExpr();
	                lhsExpr.addTerm(1.0, x[i][j]);

	                GRBLinExpr rhsExpr = new GRBLinExpr();
	                rhsExpr.addTerm(tmax, y[i][j]);

	                model.addConstr(lhsExpr, GRB.LESS_EQUAL, rhsExpr, "y=1_if_study_0_else_" + i + "_" + j);
	            }
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//vincolo per cui non posso fare più di l materie al giorno
	public static void addMaxSubjectsConstraints(GRBModel model, GRBVar[][] y, int[] t, double l, int d) {
	    try {
	        for (int j = 0; j < d; j++) {
	            GRBLinExpr expr = new GRBLinExpr();
	            for (int i = 0; i < t.length; i++) {
	                expr.addTerm(1.0, y[i][j]);
	            }
	            model.addConstr(expr, GRB.LESS_EQUAL, l, "max_subjects_day_" + j);
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//vincolo per cui non posso fare più di tmax ore di studio al giorno
	public static void addMaxHoursConstraints(GRBModel model, GRBVar[][] x, int[] t, double tmax, int d) {
	    try {
	        for (int j = 0; j < d; j++) {
	            GRBLinExpr expr = new GRBLinExpr();
	            for (int i = 0; i < t.length; i++) {
	                expr.addTerm(1.0, x[i][j]);
	            }
	            model.addConstr(expr, GRB.LESS_EQUAL, tmax, "max_hours_day_" + j);
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//vincolo per cui se inizio a studiare una materia devo farla almeno tau[indice] ore
	public static void addStudyHoursConstraints(GRBModel model, GRBVar[][] x, GRBVar[][] y, int[] t, int[] tau, int d) {
	    try {
	        for (int i = 0; i < t.length; i++) {
	            for (int j = 0; j < d; j++) {
	                GRBLinExpr lhsExpr = new GRBLinExpr();
	                lhsExpr.addTerm(1.0, x[i][j]);

	                GRBLinExpr rhsExpr = new GRBLinExpr();
	                rhsExpr.addTerm(tau[i], y[i][j]);

	                model.addConstr(lhsExpr, GRB.GREATER_EQUAL, rhsExpr, "study_hours_if_study_" + i + "_" + j);
	            }
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//funzione in cui stampo le variabili inizialmente e le salvo per stamparle correttamente
	public static void printDecisionVariables(GRBVar[][] x, GRBVar[][] y, double[][] xDaStampare, double[][] yDaStampare, int[] t, int d) {
	    try {
	        System.out.println("valori delle variabili decisionali");
	        for (int i = 0; i < t.length; i++) {
	            for (int j = 0; j < d; j++) {
	                System.out.println("x[" + i + "][" + j + "]: " + x[i][j].get(GRB.DoubleAttr.X));
	                System.out.println("y[" + i + "][" + j + "]: " + y[i][j].get(GRB.DoubleAttr.X));
	                xDaStampare[i][j] = x[i][j].get(GRB.DoubleAttr.X);
	                yDaStampare[i][j] = y[i][j].get(GRB.DoubleAttr.X);
	            }
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//funzione in cui stampo le variabili di slack/surplus
	public static void printConstraints(GRBModel model, ArrayList<String> mpliConstrDaStampare) {
	    try {
	        for (GRBConstr con : model.getConstrs()) {
	            String constrInfo = "Slack/Surplus di " + con.get(GRB.StringAttr.ConstrName) + ": " + con.get(GRB.DoubleAttr.Slack);
	            System.out.println(constrInfo);
	            mpliConstrDaStampare.add(constrInfo);
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//imposto le variabili da intere a continue per svolgere MPL
	public static void setVariablesToContinuous(GRBVar[][] x, GRBVar[][] y, int[] t, int d) {
	    try {
	        for (int i = 0; i < t.length; i++) {
	            for (int j = 0; j < d; j++) {
	                x[i][j].set(GRB.CharAttr.VType, GRB.CONTINUOUS);
	                y[i][j].set(GRB.CharAttr.VType, GRB.CONTINUOUS);
	            }
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//stampo i vincoli sul MPL
	public static void printActiveConstraints(GRBModel rilassato) {
	    try {
	        for (GRBConstr con : rilassato.getConstrs()) {
	            if (Math.abs(con.get(GRB.DoubleAttr.Slack)) < 1e-6) { // Tolleranza per evitare errori di precisione
	                System.out.println("Il vincolo " + con.get(GRB.StringAttr.ConstrName) + " è attivo.");
	            }
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//controllo se MPL è degenere: lo è se c'è almeno una var di base o una slack a 0
	public static void checkDegeneracy(GRBModel rilassato) {
	    boolean isDegenerate = false;
	    try {
	        for (GRBVar var : rilassato.getVars()) {
	            if ((var.get(GRB.IntAttr.VBasis) == GRB.BASIC && var.get(GRB.DoubleAttr.X) == 0)) {
	                isDegenerate = true;
	                break;
	            }
	        }

	        if (!isDegenerate) {
	            for (GRBConstr con : rilassato.getConstrs()) {
	                if ((con.get(GRB.DoubleAttr.Slack)) < 1e-6) {
	                    isDegenerate = true;
	                    break;
	                }
	            }
	        }
	        System.out.println("La soluzione ottima è " + (isDegenerate ? "degenere" : "non degenere"));
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//controllo se l'ottimo di MPL è multiplo: lo è se c'è almeno una var di base  con ccr nullo e poi faccio ciò anche con le slack
	public static void checkUniqueness(GRBModel rilassato) {
	    boolean isUnique = true;
	    try {
	        if (rilassato.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
	            for (GRBVar var : rilassato.getVars()) {
	                // Controlla se la variabile è non di base con costo ridotto nullo
	                if (var.get(GRB.IntAttr.VBasis) != GRB.BASIC && Math.abs(var.get(GRB.DoubleAttr.RC)) < 1e-6 ) {
	                    isUnique = false;
	                    break;
	                }
	            }

	            if (isUnique) {
	                for (GRBConstr con : rilassato.getConstrs()) {
	                    // Controlla se c'è una slack con costo ridotto nullo
	                    if (con.get(GRB.IntAttr.CBasis) != GRB.BASIC && Math.abs(con.get(GRB.DoubleAttr.Pi)) < 1e-6 ) {
	                        isUnique = false;
	                        break;
	                    }
	                }
	            }
	            System.out.println("La soluzione ottima è " + (isUnique ? "unica." : "non unica."));
	        } else {
	            System.out.println("Il modello non è stato risolto con successo.");
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//rimetto le variabili intere per il MPLI* dopo aver finito col rilassato
	public static void setVariablesToInteger(GRBVar[][] x, GRBVar[][] y, int[] t, int d) {
	    try {
	        for (int i = 0; i < t.length; i++) {
	            for (int j = 0; j < d; j++) {
	                x[i][j].set(GRB.CharAttr.VType, GRB.INTEGER);
	                y[i][j].set(GRB.CharAttr.VType, GRB.INTEGER);
	            }
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//aggiungo il vincolo per MPLI* per cui non posso fare la stessa materia per più di 2 giorni di seguito
	public static void addConsecutiveDaysConstraints(GRBModel model2, GRBVar[][] y, int[] t, int d) {
	    try {
	        for (int i = 0; i < t.length; i++) {
	            for (int j = 0; j < d - 2; j++) {
	                GRBLinExpr consecutiveDays = new GRBLinExpr();
	                consecutiveDays.addTerm(1.0, y[i][j]);
	                consecutiveDays.addTerm(1.0, y[i][j+1]);
	                consecutiveDays.addTerm(1.0, y[i][j+2]);
	                model2.addConstr(consecutiveDays, GRB.LESS_EQUAL, 2.0, "consecutiveDays_" + i + "_" + j);
	            }
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//aggiungo il vincolo per MPLI* per cui non posso fare la materia a se il giorno prima ho fatto almeno una tra b e c
	public static void addStudyAConstraints(GRBModel model2, GRBVar[][] y, int a, int b, int c, int d) {
	    try {
	        for (int j = 1; j < d; j++) {
	            GRBLinExpr prevDay = new GRBLinExpr();
	            prevDay.addTerm(1.0, y[b][j-1]);
	            prevDay.addTerm(1.0, y[c][j-1]);
	            GRBLinExpr currentDay = new GRBLinExpr();
	            currentDay.addTerm(2.0, y[a][j]);
	            currentDay.add(prevDay);
	            model2.addConstr(currentDay, GRB.LESS_EQUAL, 2.0, "studyA_" + j);
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//faccio l'analisi di sensitività sul rilassato per tau[1] e gestisco i casi in cui ci sia un infinito positivo o negativo
	public static void printSensitivityBounds(GRBModel rilassato, int d) {
	    try {
	        for (int j = 0; j < d; j++) {
	            // Ottieni il vincolo per tau[1] nel giorno j
	            GRBConstr constr = rilassato.getConstrByName(String.format("study_hours_if_study_1_%d", j));

	            // Ottieni i limiti di sensibilità per tau[1] nel giorno j
	            double lowerBound = constr.get(GRB.DoubleAttr.SARHSLow);
	            double upperBound = constr.get(GRB.DoubleAttr.SARHSUp);

	            // Stampa i limiti di sensibilità
	            BigDecimal bdl = new BigDecimal(lowerBound);
	            bdl = bdl.setScale(4, RoundingMode.HALF_UP);
	            lowerBound = bdl.doubleValue();
	            
	            BigDecimal bdu = new BigDecimal(upperBound);
	            bdu = bdu.setScale(4, RoundingMode.HALF_UP);
	            upperBound = bdu.doubleValue();
	            
	            String lowerBoundStr, upperBoundStr;
	            String lowerBoundBracket = "[", upperBoundBracket = "]";

	            if (lowerBound == Double.POSITIVE_INFINITY) {
	                lowerBoundStr = "+INF";
	                lowerBoundBracket = "(";
	            } else if (lowerBound == Double.NEGATIVE_INFINITY) {
	                lowerBoundStr = "-INF";
	                lowerBoundBracket = "(";
	            } else {
	                lowerBoundStr = String.valueOf(lowerBound);
	            }

	            if (upperBound == Double.POSITIVE_INFINITY) {
	                upperBoundStr = "+INF";
	                upperBoundBracket = ")";
	            } else if (upperBound == Double.NEGATIVE_INFINITY) {
	                upperBoundStr = "-INF";
	                upperBoundBracket = ")";
	            } else {
	                upperBoundStr = String.valueOf(upperBound);
	            }

	            System.out.println("Giorno " + j + " Intervallo DELTA tau[1] = " + lowerBoundBracket + lowerBoundStr + ", " + upperBoundStr + upperBoundBracket);	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }
	}
	//trovo il minimo tmax per cui MPLI* ha soluzione aumentandolo gradualmente di 1 partendo da 0finchè trovo una soluzione
	public static int findMinimumTmax(GRBModel model2, GRBVar[][] x, int[] t, int d) {
	    int tmax = 1;
	    int tmaxDaStampare = 0;
	    try {
	        while (true) {
	            // rimuovo i vincoli che dipendono da tmax
	            for (int j = 0; j < d; j++) {
	                model2.remove(model2.getConstrByName("max_hours_day_" + j));
	            }
	            //aggiorno i vincoli col nuovo tmax 
	            for (int j = 0; j < d; j++) {
	                GRBLinExpr expr = new GRBLinExpr();
	                for (int i = 0; i < t.length; i++) {
	                    expr.addTerm(1.0, x[i][j]);
	                }
	                model2.addConstr(expr, GRB.LESS_EQUAL, tmax, "max_hours_day_" + j);
	            }

	            model2.optimize();

	            // Controllo se il modello ha un ottimo ed in caso stampalo
	            if (model2.get(GRB.IntAttr.Status) != GRB.INFEASIBLE) {
	                System.out.println("Il minimo valore intero che tmax può assumere tale che MPLI ammetta soluzione è " + tmax);
	                tmaxDaStampare = tmax;
	                break;
	            }

	            tmax++;
	        }
	    } catch (GRBException e) {
	        e.printStackTrace();
	    }

	    return tmaxDaStampare;
	}
	//rilascio le risorse a fine esecuzione
	public static void disposeModels(GRBModel rilassato, GRBModel model, GRBModel model2, GRBEnv env) {
	    rilassato.dispose();
	    model.dispose();
	    model2.dispose();
	    try {
			env.dispose();
		} catch (GRBException e) {
			e.printStackTrace();
		}
	}

}
