/*****************************************************************
* Autor..............: Iury Ramos Sodre
* Matricula..........: 202310440
* Inicio.............: 15/06/2026
* Ultima alteracao...: 18/09/2026
* Nome...............: Cliente
* Funcao.............: Orquestra a conexao do cliente em modo linha de comando, gerenciando threads TCP e UDP.
*************************************************************** */

package model;

import java.util.Scanner;

import Protocol.APDU;
import utils.InfoUser;
import utils.Protocolo;

/**
 * Classe controladora para operacao do cliente via interface de linha de comando (CLI).
 * Gerencia a conexao TCP inicial, instancia o socket UDP e processa a entrada interativa do usuario.
 * 
 * @author Iury Ramos Sodre (Matricula: 202310440)
 * @version 2.0
 * @since 15/06/2026
 */
public class Cliente {

  private String ipServidor;
  private int portaServidor;

  /**
   * Construtor do orquestrador do cliente CLI.
   * 
   * @param ipServidor    Endereco IP do servidor central.
   * @param portaServidor Porta de escuta do servidor.
   */
  public Cliente(String ipServidor, int portaServidor) {
    this.ipServidor = ipServidor;
    this.portaServidor = portaServidor;
  }

  /**
   * Inicializa o fluxo de execucao do cliente no terminal, autenticando o usuario
   * e entrando no loop de comandos.
   */
  public void iniciar() {
    Scanner scanner = new Scanner(System.in);

    try {
      // 1. Configuracoes Iniciais
      System.out.println("==========================================");
      System.out.println("            BEM-VINDO AO CHAT             ");
      System.out.println("==========================================");
      // 2. Inicializando a conexao TCP com o Servidor PRIMEIRO
      int portaTcp = portaServidor;
      int portaUdp = (portaServidor == Protocolo.PORTA_SERVIDOR_LEGACY) ? Protocolo.PORTA_SERVIDOR_LEGACY : Protocolo.PORTA_SERVIDOR_UDP;
      ClienteTCP tcp = new ClienteTCP(ipServidor, portaTcp);

      // 3. Inicializando a conexao UDP
      ClienteUDP udp = new ClienteUDP(ipServidor, portaUdp);
      int minhaPortaUDP = udp.getPortaLocal();

      // Pega o IP local da maquina automaticamente baseado na conexao com o servidor
      String meuIp = tcp.getIpLocal();
      InfoUser eu = null;

      while (true) {
        System.out.print("Digite seu nome: ");
        String nome = scanner.nextLine();
        if (nome.trim().isEmpty()) {
          continue;
        }

        eu = new InfoUser(nome, meuIp, minhaPortaUDP);

        // Registra no servidor para receber avisos e validar nome unico
        APDU resRegistro = tcp.register(eu);
        if (resRegistro != null && Protocolo.ERRO.equals(resRegistro.getOperacao())) {
          System.out.println("\n[SISTEMA] " + resRegistro.getTextoMensagem());
          System.out.println("Por favor, escolha outro nome.\n");
        } else {
          System.out.println("\n[SISTEMA] Conectado com sucesso como " + nome);
          break;
        }
      }

      // 4. Iniciando a thread que escuta mensagens recebidas via UDP
      Thread threadRecepcao = new Thread(udp);
      threadRecepcao.start();

      System.out.println("\nComandos disponiveis:");
      System.out.println("  /join <grupo>            - Entrar em um grupo");
      System.out.println("  /leave <grupo>           - Sair de um grupo");
      System.out.println("  /list                    - Listar grupos ativos no servidor");
      System.out.println("  /send <grupo> <mensagem> - Enviar mensagem para grupo");
      System.out.println("  /pvt <usuario> <msg>     - Enviar mensagem privada");
      System.out.println("  /sair                    - Encerrar aplicativo\n");

      // 5. Loop de interacao com o usuario
      boolean rodando = true;
      while (rodando) {
        String input = scanner.nextLine();
        if (input.trim().isEmpty())
          continue;

        String[] partes = input.split(" ", 3); // quebra o comando em ate 3 partes
        String comando = partes[0].toLowerCase();

        switch (comando) {
          case "/join":
            if (partes.length >= 2) {
              APDU resposta = tcp.join(partes[1], eu);
              if (resposta != null)
                System.out.println("\n[SISTEMA] " + resposta.getTextoMensagem());
            } else {
              System.out.println("Uso correto: /join <grupo>");
            }
            break;

          case "/leave":
            if (partes.length >= 2) {
              APDU resposta = tcp.leave(partes[1], eu);
              if (resposta != null)
                System.out.println("\n[SISTEMA] " + resposta.getTextoMensagem());
            } else {
              System.out.println("Uso correto: /leave <grupo>");
            }
            break;

          case "/list":
            APDU resList = tcp.list();
            if (resList != null)
              System.out.println("\n[SISTEMA] Grupos: " + resList.getTextoMensagem());
            break;

          case "/send":
            if (partes.length >= 3)
              udp.send(partes[1], eu, partes[2]);
            else
              System.out.println("Uso correto: /send <grupo> <mensagem>");
            break;

          case "/pvt":
            if (partes.length >= 3)
              udp.sendPvt(partes[1], eu, partes[2]);
            else
              System.out.println("Uso correto: /pvt <usuario> <mensagem>");
            break;

          case "/sair":
            tcp.fecharConexao();
            udp.fecharConexao();
            rodando = false;
            System.out.println("Saindo...");
            break;

          default:
            System.out.println("Comando desconhecido.");
        }
      }

    } catch (Exception e) {
      System.out.println("ERRO FATAL: " + e.getMessage());
      e.printStackTrace(System.out);
    } finally {
      scanner.close();
      System.exit(0);
    }
  }
}
