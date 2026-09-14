/**
 * Autor: Iury Ramos Sodre
 * Matricula: 202310440
 * Inicio: 15/06/2026
 * Ultima alteracao: 14/09/2026
 * Nome: ServidorTCP
 * Funcao: Servico de escuta passiva de conexoes TCP e instanciacao de threads dedicadas de atendimento.
 */

package model;

import java.net.ServerSocket;
import java.net.Socket;

import controller.GerenciadorGrupos;
import controller.AtendimentoCliente;

/**
 * Servico responsavel pela abertura do socket servidor TCP (ServerSocket) e despacho
 * concorrente de cada nova conexao de cliente para uma instancia de {@link AtendimentoCliente}.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class ServidorTCP implements Runnable {

  private int porta;
  private GerenciadorGrupos gerenciador;

  /**
   * Construtor do ServidorTCP.
   * 
   * @param porta       A porta TCP a ser escutada.
   * @param gerenciador O gerenciador compartilhado de grupos e usuarios.
   */
  public ServidorTCP(int porta, GerenciadorGrupos gerenciador) {
    this.porta = porta;
    this.gerenciador = gerenciador;
  }

  @Override
  public void run() {
    try (ServerSocket servidorTCP = new ServerSocket(porta)) {
      System.out.println("[SERVIDOR:TCP] [INFO] Escutando na porta " + porta);

      while (true) {
        Socket conexaoClienteTCP = servidorTCP.accept();
        System.out.println(
            "[SERVIDOR:TCP] [INFO] Nova conexao recebida de " + conexaoClienteTCP.getInetAddress().getHostAddress());

        // Dispara thread dedicada para atender o cliente conectado
        Thread threadAtendimentoCliente = new Thread(new AtendimentoCliente(conexaoClienteTCP, gerenciador));
        threadAtendimentoCliente.start();
      }

    } catch (Exception e) {
      System.err.println("[SERVIDOR:TCP] [ERROR] " + e.getMessage());
    }
  }

}
