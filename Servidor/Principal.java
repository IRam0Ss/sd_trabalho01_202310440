/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: Sistema de Comunicacao Interno da E.D.E.N (Modulo Servidor)
 * Funcao: Ponto de entrada para inicializacao dos servicos de rede e gerenciamento do Servidor.
 */

import model.Servidor;

/**
 * Ponto de entrada da aplicacao do servidor central do sistema E.D.E.N.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class Principal {

  /**
   * Metodo principal que instancia e inicia os servicos do servidor.
   * 
   * @param args Argumentos de linha de comando
   */
  public static void main(String[] args) {
    System.setProperty("java.net.preferIPv4Stack", "true");
    Servidor servidor = new Servidor();
    servidor.iniciar();
  }

}
