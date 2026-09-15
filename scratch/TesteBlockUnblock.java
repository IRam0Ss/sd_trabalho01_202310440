/**
 * Teste Automatizado das APDUs BLOCK e UNBLOCK (Bloqueio Mútuo & Tag em Grupo)
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class TesteBlockUnblock {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("   TESTANDO APDUS BLOCK E UNBLOCK (SISTEMA EDEN)  ");
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

      // 4. Criar Cliente Carol
      model.ClienteTCP tcpCarol = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpCarol = new model.ClienteUDP("127.0.0.1", 7777);
      udpCarol.setMeuNome("Carol");
      Thread tUdpCarol = new Thread(udpCarol);
      tUdpCarol.setDaemon(true);
      tUdpCarol.start();

      utils.InfoUser userCarol = new utils.InfoUser("Carol", "127.0.0.1", udpCarol.getPortaLocal());
      tcpCarol.register(userCarol);

      // 5. Alice BLOQUEIA Bob via TCP
      System.out.println("\n[1] Alice enviando APDU BLOCK para Bob...");
      Protocol.APDU respBlock = tcpAlice.block("Bob", userAlice);
      System.out.println("    Resposta do Servidor ao BLOCK: " + respBlock.getTextoMensagem());

      // Mapeamento de Ticks no Bob
      final boolean[] tickErroBob = {false};
      udpBob.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {}
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {
          System.out.println("    [LISTENER BOB] Tick -> ID: " + idMensagem + " | Status: " + status);
          if (status == -1) {
            tickErroBob[0] = true;
          }
        }
      });

      // 6. Bob tenta enviar mensagem PRIVADA para Alice
      System.out.println("\n[2] Bob tentando enviar mensagem privada (SENDPVT) para Alice (que o bloqueou)...");
      String idMsgBob = udpBob.sendPvt("Alice", userBob, "Oi Alice, desbloqueia eu!");

      Thread.sleep(1500);

      if (!tickErroBob[0]) {
        throw new RuntimeException("FALHA: Bob nao recebeu a confirmacao de erro/bloqueio (Status -1 / Tick Vermelho)!");
      }
      System.out.println("    >>> SUCESSO: Servidor rejeitou SENDPVT e Bob recebeu Status -1 (Bloqueado)!");

      // 7. Entrar todos no grupo "Devs"
      tcpAlice.join("Devs", userAlice);
      tcpBob.join("Devs", userBob);
      tcpCarol.join("Devs", userCarol);

      final String[] msgRecebidaBobNoGrupo = {""};
      final String[] msgRecebidaCarolNoGrupo = {""};
      final String[] msgRecebidaAliceDeBob = {""};

      udpAlice.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {
          if ("Bob".equalsIgnoreCase(remetente.getNome())) {
            msgRecebidaAliceDeBob[0] = mensagem;
          }
        }
        @Override public void onShutdown() {}
        @Override public void onUpdateUsers() {}
        @Override public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      udpBob.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {
          if ("Alice".equalsIgnoreCase(remetente.getNome())) {
            msgRecebidaBobNoGrupo[0] = mensagem;
          }
        }
        @Override public void onShutdown() {}
        @Override public void onUpdateUsers() {}
        @Override public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      udpCarol.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {
          if ("Alice".equalsIgnoreCase(remetente.getNome())) {
            msgRecebidaCarolNoGrupo[0] = mensagem;
          }
        }
        @Override public void onShutdown() {}
        @Override public void onUpdateUsers() {}
        @Override public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {}
      });

      // 8. Alice envia mensagem no grupo "Devs"
      System.out.println("\n[3] Alice enviando mensagem no grupo 'Devs'...");
      udpAlice.send("Devs", userAlice, "Mensagem da Alice no grupo");

      Thread.sleep(1500);

      System.out.println("    Mensagem da Alice recebida por Carol: '" + msgRecebidaCarolNoGrupo[0] + "'");
      System.out.println("    Mensagem da Alice recebida por Bob (bloqueado por Alice): '" + msgRecebidaBobNoGrupo[0] + "'");

      if (!"Mensagem da Alice no grupo".equals(msgRecebidaCarolNoGrupo[0])) {
        throw new RuntimeException("FALHA: Carol nao recebeu a mensagem original da Alice no grupo!");
      }
      if (!"~BLOCKED~".equals(msgRecebidaBobNoGrupo[0])) {
        throw new RuntimeException("FALHA: Bob (bloqueado por Alice) deveria ter recebido ~BLOCKED~ mas recebeu: " + msgRecebidaBobNoGrupo[0]);
      }
      System.out.println("    >>> SUCESSO: Carol leu a mensagem normal e Bob recebeu ~BLOCKED~!");

      // 9. Bob envia mensagem no grupo "Devs"
      System.out.println("\n[4] Bob enviando mensagem no grupo 'Devs'...");
      udpBob.send("Devs", userBob, "Mensagem do Bob no grupo");

      Thread.sleep(1500);

      System.out.println("    Mensagem do Bob recebida por Alice (que bloqueou Bob): '" + msgRecebidaAliceDeBob[0] + "'");
      if (!"~BLOCKED~".equals(msgRecebidaAliceDeBob[0])) {
        throw new RuntimeException("FALHA: Alice deveria ter recebido ~BLOCKED~ de Bob mas recebeu: " + msgRecebidaAliceDeBob[0]);
      }
      System.out.println("    >>> SUCESSO MUTUO: Alice tambem recebeu ~BLOCKED~ para a mensagem do Bob!");

      // 10. Alice DESBLOQUEIA Bob via TCP
      System.out.println("\n[5] Alice enviando APDU UNBLOCK para Bob...");
      Protocol.APDU respUnblock = tcpAlice.unblock("Bob", userAlice);
      System.out.println("    Resposta do Servidor ao UNBLOCK: " + respUnblock.getTextoMensagem());

      final boolean[] tickSucessoBob = {false};
      udpBob.setListener(new model.MessageListener() {
        @Override public void onMessageReceived(String idMensagem, String destino, utils.InfoUser remetente, String mensagem, boolean isPrivate, boolean isVisualizacaoUnica) {}
        @Override public void onShutdown() {}
        @Override public void onUpdateUsers() {}
        @Override
        public void onTickReceived(String idMensagem, int status, String nomeConfirmou) {
          System.out.println("    [LISTENER BOB] Tick apos unblock -> ID: " + idMensagem + " | Status: " + status);
          if (status == 2) {
            tickSucessoBob[0] = true;
          }
        }
      });

      // 11. Bob envia mensagem PRIVADA para Alice apos o desbloqueio
      System.out.println("\n[6] Bob enviando mensagem privada (SENDPVT) para Alice apos ser desbloqueado...");
      udpBob.sendPvt("Alice", userBob, "Valeu por desbloquear!");

      Thread.sleep(1500);

      if (!tickSucessoBob[0]) {
        throw new RuntimeException("FALHA: Bob nao recebeu a confirmacao de entrega (Status 2) apos desbloqueio!");
      }
      System.out.println("    >>> SUCESSO TOTAL: Comunicacao privada reestabelecida com sucesso apos o UNBLOCK!");

      System.out.println("\n==================================================");
      System.out.println(" TESTE DE BLOCK E UNBLOCK PASSOU COM 100% SUCESSO!");
      System.out.println("==================================================");
      System.exit(0);

    } catch (Exception e) {
      System.err.println("\n[ERRO NO TESTE] " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}
