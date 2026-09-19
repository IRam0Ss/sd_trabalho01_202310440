/**
 * Teste Automatizado da APDU SENDVU (Visualização Única por Pop-up e Confirmação em Grupo por todos os membros)
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class TesteSendVUGroup {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println(" TESTANDO APDU SENDVU (VISUALIZACAO UNICA EM GRUPO) ");
    System.out.println("==================================================");

    try {
      // 1. Subir Servidor EDEN
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();

      Thread.sleep(1500);

      // 2. Criar Cliente Alice (Remetente)
      model.ClienteTCP tcpAlice = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpAlice = new model.ClienteUDP("127.0.0.1", 7777);
      udpAlice.setMeuNome("Alice");
      Thread tUdpAlice = new Thread(udpAlice);
      tUdpAlice.setDaemon(true);
      tUdpAlice.start();

      utils.InfoUser userAlice = new utils.InfoUser("Alice", "127.0.0.1", udpAlice.getPortaLocal());
      tcpAlice.register(userAlice);

      // 3. Criar Cliente Bob (Destinatario 1)
      model.ClienteTCP tcpBob = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpBob = new model.ClienteUDP("127.0.0.1", 7777);
      udpBob.setMeuNome("Bob");
      Thread tUdpBob = new Thread(udpBob);
      tUdpBob.setDaemon(true);
      tUdpBob.start();

      utils.InfoUser userBob = new utils.InfoUser("Bob", "127.0.0.1", udpBob.getPortaLocal());
      tcpBob.register(userBob);

      // 4. Criar Cliente Carol (Destinatario 2)
      model.ClienteTCP tcpCarol = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpCarol = new model.ClienteUDP("127.0.0.1", 7777);
      udpCarol.setMeuNome("Carol");
      Thread tUdpCarol = new Thread(udpCarol);
      tUdpCarol.setDaemon(true);
      tUdpCarol.start();

      utils.InfoUser userCarol = new utils.InfoUser("Carol", "127.0.0.1", udpCarol.getPortaLocal());
      tcpCarol.register(userCarol);

      // 5. Entrar todos no grupo "Devs"
      tcpAlice.join("Devs", userAlice);
      tcpBob.join("Devs", userBob);
      tcpCarol.join("Devs", userCarol);

      final List<Integer> ticksAlice = Collections.synchronizedList(new ArrayList<>());
      final boolean[] bobRecebeuVU = {false};
      final boolean[] carolRecebeuVU = {false};

      udpAlice.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {}
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {
          System.out.println("    [LISTENER ALICE] Tick recebido -> ID: " + idMensagem + " | Status: " + status + " | Confirmou: " + nomeConfirmou);
          ticksAlice.add(status);
        }
      });

      udpBob.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {
          if (isVisualizacaoUnica) {
            System.out.println("    [LISTENER BOB] Recebida mensagem de Visualizacao Unica! ID: " + idMensagem + " Texto: " + mensagem);
            bobRecebeuVU[0] = true;
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      udpCarol.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {
          if (isVisualizacaoUnica) {
            System.out.println("    [LISTENER CAROL] Recebida mensagem de Visualizacao Unica! ID: " + idMensagem + " Texto: " + mensagem);
            carolRecebeuVU[0] = true;
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      // 6. Alice envia mensagem de Visualizacao Unica no grupo Devs
      System.out.println("\n[1] Alice enviando mensagem SENDVU no grupo 'Devs'...");
      String idMsgVU = udpAlice.sendVu("Devs", userAlice, "Codigo Secreto EDEN 1234");
      System.out.println("    ID da mensagem VU: " + idMsgVU);

      Thread.sleep(1500);

      if (!bobRecebeuVU[0] || !carolRecebeuVU[0]) {
        throw new RuntimeException("FALHA: Bob ou Carol nao receberam a mensagem com a flag isVisualizacaoUnica == true!");
      }
      System.out.println("    >>> SUCESSO: Tanto Bob quanto Carol receberam a APDU com isVisualizacaoUnica == true!");

      // 7. Bob simula a abertura do Pop-up Modal (e fechamento/expiracao)
      System.out.println("\n[2] Bob abrindo a mensagem no Pop-up Modal e confirmando leitura...");
      udpBob.sendConfirm(idMsgVU, 3, "Devs", "Alice");

      Thread.sleep(1500);

      long countStatus3 = ticksAlice.stream().filter(s -> s == 3).count();
      System.out.println("    Confirmacoes de leitura (Status 3) na Alice ate agora: " + countStatus3);
      if (countStatus3 != 0) {
        throw new RuntimeException("FALHA: O servidor nao deve repassar Status 3 de VU quando apenas 1 membro visualizou!");
      }
      System.out.println("    >>> SUCESSO PARCIAL: O servidor reteve o Status 3 pois Carol ainda nao abriu a VU. Alice continua em Status 2.");

      // 8. Carol simula a abertura do Pop-up Modal (e fechamento/expiracao)
      System.out.println("\n[3] Carol abrindo a mensagem no Pop-up Modal e confirmando leitura...");
      udpCarol.sendConfirm(idMsgVU, 3, "Devs", "Alice");

      Thread.sleep(1500);

      countStatus3 = ticksAlice.stream().filter(s -> s == 3).count();
      System.out.println("    Confirmacoes de leitura (Status 3) na Alice apos todos lerem: " + countStatus3);
      if (countStatus3 != 1) {
        throw new RuntimeException("FALHA: Esperado exatamente 1 confirmacao agregada de leitura (TODOS visualizaram)!");
      }

      System.out.println("    >>> SUCESSO TOTAL: O servidor agregou e repassou Status 3 quando TODOS (Bob e Carol) visualizaram a mensagem VU!");
      System.out.println("\n==================================================");
      System.out.println("  TESTE DE SENDVU EM GRUPO PASSOU COM 100% SUCESSO!");
      System.out.println("==================================================");
      System.exit(0);

    } catch (Exception e) {
      System.err.println("\n[ERRO NO TESTE] " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}

