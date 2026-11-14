package simuladormt;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// Simulador de Máquina de Turing de una cinta.
// Autor: Luis López
public class SimuladorMT {

    // Definición de la máquina, se llena al leer el archivo
    static List<String> estados = new ArrayList<>();
    static String estadoInicial = "";
    static List<String> estadosHa = new ArrayList<>();
    static List<String> estadosHe = new ArrayList<>();
    static List<Character> alfabeto = new ArrayList<>();
    static char blanco = '∆';

    // Cada transición MT(q0, a) = (q1, b, D) se guarda como
    // {"q0", "a", "q1", "b", "D", numeroDeLinea}
    static List<String[]> transiciones = new ArrayList<>();

    static List<Character> cinta = new ArrayList<>();
    static int cabeza = 0;

    static final int LIMITE_PASOS = 10000;

    static Scanner teclado = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("       SIMULADOR DE MAQUINA DE TURING");
        System.out.println("==============================================");

        pedirArchivo();

        while (true) {
            String cadena = pedirCadena();
            simular(cadena);

            System.out.println();
            System.out.println("¿Qué desea hacer?");
            System.out.println("  1. Ingresar una nueva cinta");
            System.out.println("  2. Cargar una nueva definición de máquina");
            System.out.println("  3. Salir");
            System.out.print("> ");
            String opcion = teclado.nextLine().trim();

            if (opcion.equals("2")) {
                pedirArchivo();
            } else if (opcion.equals("3")) {
                System.out.println("Fin del programa.");
                return;
            }
        }
    }

    // Pide la ruta del archivo
    static void pedirArchivo() {
        while (true) {
            System.out.println();
            System.out.println("Ubicación del archivo con la definición de la MT:");
            System.out.print("> ");
            String ruta = teclado.nextLine().trim().replace("\"", "");

            try {
                leerArchivo(ruta);
                mostrarDefinicion();
                return;
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                System.out.println("Intente de nuevo.");
            }
        }
    }

    static void leerArchivo(String ruta) throws Exception {
        estados.clear();
        estadosHa.clear();
        estadosHe.clear();
        alfabeto.clear();
        transiciones.clear();
        estadoInicial = "";
        blanco = ' ';

        List<String> lineas;
        try {
            lineas = Files.readAllLines(Paths.get(ruta), StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            // El archivo no está en UTF-8 (por ejemplo, guardado como ANSI en el Bloc de notas)
            lineas = Files.readAllLines(Paths.get(ruta), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            throw new Exception("no se pudo abrir el archivo '" + ruta + "'.");
        }

        for (int i = 0; i < lineas.size(); i++) {
            int numLinea = i + 1;

            // Algunos editores ponen una marca invisible (BOM, código 0xFEFF) al inicio
            String linea = lineas.get(i).replace(" ", "").replace("\t", "")
                    .replace(String.valueOf((char) 0xFEFF), "");

            if (linea.isEmpty() || linea.startsWith("//")) {
                continue;
            }

            if (linea.startsWith("MT(")) {
                leerTransicion(linea, numLinea);
            } else {
                leerInstruccion(linea, numLinea);
            }
        }

        // Si no hay línea Blanco(...), se busca en las transiciones cuál se usa
        if (blanco == ' ') {
            blanco = '∆';
            for (String[] t : transiciones) {
                char leido = t[1].charAt(0);
                char escrito = t[3].charAt(0);
                if (esBlancoComun(leido)) blanco = leido;
                if (esBlancoComun(escrito)) blanco = escrito;
            }
        }

        validarDefinicion();
    }

    // Símbolos que se suelen usar como blanco; la B solo cuenta si no está en el alfabeto
    static boolean esBlancoComun(char c) {
        return c == '_' || c == '∆' || c == 'Δ' || (c == 'B' && !alfabeto.contains('B'));
    }

    // Lee líneas como "Estados(q0,q1,q2)"
    static void leerInstruccion(String linea, int numLinea) throws Exception {
        int abre = linea.indexOf("(");
        if (abre == -1 || !linea.endsWith(")")) {
            throw new Exception("Línea " + numLinea + ": no se reconoce el formato -> " + linea);
        }

        String palabra = linea.substring(0, abre);
        String contenido = linea.substring(abre + 1, linea.length() - 1);
        String[] valores = contenido.split(",");

        if (contenido.isEmpty()) {
            throw new Exception("Línea " + numLinea + ": los paréntesis están vacíos.");
        }

        if (palabra.equalsIgnoreCase("Estados")) {
            for (String v : valores)
                estados.add(v);

        } else if (palabra.equalsIgnoreCase("Inicial")) {
            estadoInicial = contenido;

        } else if (palabra.equalsIgnoreCase("Ha")) {
            for (String v : valores)
                estadosHa.add(v);

        } else if (palabra.equalsIgnoreCase("He")) {
            for (String v : valores)
                estadosHe.add(v);

        } else if (palabra.equalsIgnoreCase("Alfabeto")) {
            for (String v : valores) {
                if (v.length() != 1) {
                    throw new Exception("Línea " + numLinea + ": el símbolo '" + v + "' debe ser un solo carácter.");
                }
                alfabeto.add(v.charAt(0));
            }

        } else if (palabra.equalsIgnoreCase("Blanco")) {
            if (contenido.length() != 1) {
                throw new Exception("Línea " + numLinea + ": el blanco debe ser un solo carácter.");
            }
            blanco = contenido.charAt(0);

        } else {
            throw new Exception("Línea " + numLinea + ": no se reconoce la palabra '" + palabra + "'.");
        }
    }

    static void leerTransicion(String linea, int numLinea) throws Exception {
        String[] lados = linea.split("=");
        if (lados.length != 2 || !lados[0].endsWith(")")
                || !lados[1].startsWith("(") || !lados[1].endsWith(")")) {
            throw new Exception("Línea " + numLinea + ": transición mal escrita -> " + linea);
        }

        String[] izq = lados[0].substring(3, lados[0].length() - 1).split(",");
        String[] der = lados[1].substring(1, lados[1].length() - 1).split(",");

        if (izq.length != 2 || der.length != 3) {
            throw new Exception("Línea " + numLinea + ": transición mal escrita -> " + linea);
        }
        if (izq[1].length() != 1 || der[1].length() != 1) {
            throw new Exception("Línea " + numLinea + ": cada símbolo debe ser un solo carácter.");
        }

        String movimiento = der[2].toUpperCase();
        if (!movimiento.equals("D") && !movimiento.equals("I") && !movimiento.equals("N")) {
            throw new Exception("Línea " + numLinea + ": el movimiento '" + der[2]
                    + "' no es válido. Solo se permite D, I o N.");
        }

        transiciones.add(new String[] { izq[0], izq[1], der[0], der[1], movimiento, "" + numLinea });
    }

    // Se valida al final porque las líneas pueden venir en cualquier orden
    static void validarDefinicion() throws Exception {
        if (estados.isEmpty())
            throw new Exception("falta la línea Estados( ... ).");
        if (estadoInicial.isEmpty())
            throw new Exception("falta la línea Inicial( ... ).");
        if (estadosHa.isEmpty())
            throw new Exception("falta la línea Ha( ... ).");
        if (alfabeto.isEmpty())
            throw new Exception("falta la línea Alfabeto( ... ).");
        if (!estados.contains(estadoInicial)) {
            throw new Exception("el estado inicial '" + estadoInicial + "' no está en Estados.");
        }

        for (int i = 0; i < transiciones.size(); i++) {
            String[] t = transiciones.get(i);
            String donde = "Línea " + t[5] + ": ";

            if (!estados.contains(t[0])) {
                throw new Exception(donde + "el estado '" + t[0] + "' no está en Estados.");
            }
            if (!estados.contains(t[2]) && !estadosHa.contains(t[2]) && !estadosHe.contains(t[2])) {
                throw new Exception(donde + "el estado '" + t[2] + "' no está en Estados, Ha ni He.");
            }
            if (!simboloValido(t[1].charAt(0))) {
                throw new Exception(donde + "el símbolo '" + t[1] + "' no está en el Alfabeto.");
            }
            if (!simboloValido(t[3].charAt(0))) {
                throw new Exception(donde + "el símbolo '" + t[3] + "' no está en el Alfabeto.");
            }

            // Determinista: no puede haber dos transiciones para el mismo (estado, símbolo)
            for (int j = 0; j < i; j++) {
                String[] otra = transiciones.get(j);
                if (otra[0].equals(t[0]) && otra[1].equals(t[1])) {
                    throw new Exception(donde + "ya existe una transición para MT("
                            + t[0] + ", " + t[1] + "). La máquina debe ser determinista.");
                }
            }
        }
    }

    static boolean simboloValido(char c) {
        return alfabeto.contains(c) || c == blanco;
    }

    static void mostrarDefinicion() {
        System.out.println();
        System.out.println("Definición cargada:");
        System.out.println("  Estados      : " + estados);
        System.out.println("  Inicial      : " + estadoInicial);
        System.out.println("  Ha (acepta)  : " + estadosHa);
        System.out.println("  He (rechaza) : " + estadosHe);
        System.out.println("  Alfabeto     : " + alfabeto);
        System.out.println("  Blanco       : " + blanco);
        System.out.println("  Transiciones : " + transiciones.size());
    }

    // Pide la cadena hasta que todos sus caracteres pertenezcan al alfabeto
    static String pedirCadena() {
        while (true) {
            System.out.println();
            System.out.println("Ingrese la cadena de la cinta y presione ENTER:");
            System.out.print("> ");
            String cadena = teclado.nextLine();

            boolean valida = true;
            for (int i = 0; i < cadena.length(); i++) {
                char c = cadena.charAt(i);
                if (!simboloValido(c)) {
                    System.out.println("  El carácter '" + c + "' (posición " + (i + 1)
                            + ") no pertenece al alfabeto " + alfabeto + ".");
                    valida = false;
                }
            }

            if (valida) {
                return cadena;
            }
            System.out.println("  Cadena rechazada. Intente de nuevo.");
        }
    }

    static void simular(String cadena) {
        cinta.clear();
        for (int i = 0; i < cadena.length(); i++) {
            cinta.add(cadena.charAt(i));
        }
        if (cinta.isEmpty()) {
            cinta.add(blanco);
        }
        cabeza = 0;

        String estado = estadoInicial;
        int paso = 0;

        System.out.println();
        System.out.println("---------------- SIMULACION ----------------");
        System.out.println("La celda entre corchetes es la que lee la cabeza.");
        System.out.println();

        while (true) {
            System.out.println("Paso " + paso + "  Estado: " + estado + "  Cinta: " + cintaComoTexto());

            if (estadosHa.contains(estado)) {
                resultado("ACEPTACIÓN", "la máquina llegó al estado de aceptación " + estado + ".");
                return;
            }
            if (estadosHe.contains(estado)) {
                resultado("NO ACEPTACIÓN", "la máquina llegó al estado de rechazo " + estado + ".");
                return;
            }
            if (paso >= LIMITE_PASOS) {
                resultado("NO ACEPTACIÓN", "no se detuvo en " + LIMITE_PASOS + " pasos (posible ciclo infinito).");
                return;
            }

            char leido = cinta.get(cabeza);
            String[] t = buscarTransicion(estado, leido);
            if (t == null) {
                resultado("NO ACEPTACIÓN", "no hay transición definida para MT(" + estado + ", " + leido + ").");
                return;
            }
            System.out.println("        MT(" + estado + ", " + leido + ") = ("
                    + t[2] + ", " + t[3] + ", " + t[4] + ")");

            cinta.set(cabeza, t[3].charAt(0));

            // Mover la cabeza; si se sale de la cinta se agrega un blanco
            if (t[4].equals("D")) {
                cabeza++;
                if (cabeza == cinta.size()) {
                    cinta.add(blanco);
                }
            } else if (t[4].equals("I")) {
                if (cabeza == 0) {
                    cinta.add(0, blanco);
                } else {
                    cabeza--;
                }
            }

            estado = t[2];
            paso++;
        }
    }

    static String[] buscarTransicion(String estado, char leido) {
        for (String[] t : transiciones) {
            if (t[0].equals(estado) && t[1].charAt(0) == leido) {
                return t;
            }
        }
        return null;
    }

    static String cintaComoTexto() {
        String texto = "";
        for (int i = 0; i < cinta.size(); i++) {
            if (i == cabeza) {
                texto += "[" + cinta.get(i) + "]";
            } else {
                texto += " " + cinta.get(i) + " ";
            }
        }
        return texto;
    }

    static void resultado(String resultado, String motivo) {
        System.out.println();
        System.out.println("RESULTADO: " + resultado);
        System.out.println("Motivo: " + motivo);
        System.out.println("--------------------------------------------");
    }
}
