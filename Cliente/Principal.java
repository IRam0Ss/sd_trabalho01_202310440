/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: Sistema de Comunicacao Interno da E.D.E.N (Modulo Cliente)
 * Funcao: Ponto de entrada para inicializacao da aplicacao grafica JavaFX do Cliente.
 */

import javafx.application.Application;
import view.ClienteGUI;

/**
 * Ponto de entrada da aplicacao cliente do sistema E.D.E.N.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class Principal {

  /**
   * Metodo principal que inicia o ciclo de vida da interface grafica JavaFX.
   * 
   * @param args Argumentos de linha de comando
   */
  public static void main(String[] args) {
    // Inicia a Aplicacao Grafica JavaFX
    Application.launch(ClienteGUI.class, args);
  }
}