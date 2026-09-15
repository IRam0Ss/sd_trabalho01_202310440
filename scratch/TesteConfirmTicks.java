/**
 * Teste Automatizado da APDU CONFIRM (Ticks de Entrega e Leitura)
 */

import java.net.*;
import java.io.*;

public class TesteConfirmTicks {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("     TESTANDO APDU CONFIRM (TICKS DE MENSAGEM)    ");
    System.out.println("==================================================");

    try {
      // 1. Subir Servidor EDEN
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();

      Thread.sleep(1500);

      // 2. Criar Cliente Alice
      model.ClienteTCP tcpAlice = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpAlice = new model.ClienteUDP("127.0.0.1", 7777);
      udpAlice.setMeuNome("Alice");
      Thread tUdpAlice = new Thread(udpAlice);
      tUdpAlice.setDaemon(true);
      tUdpAlice.start();

      utils.InfoUser userAlice = new utils.InfoUser("Alice", "127.0.0.1", udpAlice.getPortaLocal());
      tcpAlice.register(userAlice);

      // 3. Criar Cliente Bob
      model.ClienteTCP tcpBob = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpBob = new model.ClienteUDP("127.0.0.1", 7777);
      udpBob.setMeuNome("Bob");
      Thread tUdpBob = new Thread(udpBob);
      tUdpBob.setDaemon(true);
      tUdpBob.start();

      utils.InfoUser userBob = new utils.InfoUser("Bob", "127.0.0.1", udpBob.getPortaLocal());
      tcpBob.register(userBob);

      // Mapeamento de Ticks recebidos por Alice
      final boolean[] tick2Recebido = {false};
      final boolean[] tick3Recebido = {false};
      final String[] idMensagemRecebido = {""};

      udpAlice.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate) {}
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {
          System.out.println("    [LISTENER ALICE] Tick recebido -> ID: " + idMensagem + " | Status: " + status + " | Confirmado por: " + nomeConfirmou);
          if (status == 2) {
            tick2Recebido[0] = true;
            idMensagemRecebido[0] = idMensagem;
          } else if (status == 3) {
            tick3Recebido[0] = true;
          }
        }
      });

      // 4. Alice envia mensagem privada para Bob
      System.out.println("\n[1] Alice enviando mensagem privada (SENDPVT) para Bob...");
      String idMsgEnviada = udpAlice.sendPvt("Bob", userAlice, "Ola Bob, tudo bem?");
      System.out.println("    ID da mensagem enviada por Alice: " + idMsgEnviada);

      // Aguarda o auto-ACK de entrega (Status 2) ser retornado por Bob
      Thread.sleep(1500);

      if (!tick2Recebido[0]) {
        throw new RuntimeException("FALHA: Alice nao recebeu a confirmacao de entrega (Status 2 / Tick Duplo Cinza)!");
      }
      System.out.println("    >>> SUCESSO: Confirmacao de entrega (Status 2 / Tick Duplo Cinza) recebida com sucesso!");

      // 5. Bob simula a leitura do chat enviando CONFIRM (Status 3)
      System.out.println("\n[2] Bob abrindo a conversa e confirmando leitura (Status 3)...");
      udpBob.sendConfirm(idMsgEnviada, 3, "@Bob", "Alice");

      Thread.sleep(1500);

      if (!tick3Recebido[0]) {
        throw new RuntimeException("FALHA: Alice nao recebeu a confirmacao de leitura (Status 3 / Tick Duplo Dourado)!");
      }
      System.out.println("    >>> SUCESSO: Confirmacao de leitura (Status 3 / Tick Duplo Dourado) recebida com sucesso!");

      System.out.println("\n==================================================");
      System.out.println("  TESTE DE CONFIRM (TICKS) PASSOU COM 100% SUCESSO!");
      System.out.println("==================================================");
      System.exit(0);

    } catch (Exception e) {
      System.err.println("\n[ERRO NO TESTE] " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}

