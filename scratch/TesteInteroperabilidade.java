/**
 * Teste de Interoperabilidade Universal
 * Valida a compatibilidade mutua entre Servidor EDEN, Cliente EDEN e lucas_cliente.
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class TesteInteroperabilidade {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("  INICIANDO TESTE DE INTEROPERABILIDADE UNIVERSAL ");
    System.out.println("==================================================");

    try {
      // 1. Inicia o Servidor EDEN
      System.out.println("\n[1] Subindo Servidor EDEN...");
      model.Servidor servidor = new model.Servidor();
      Thread threadServidor = new Thread(() -> servidor.iniciar());
      threadServidor.setDaemon(true);
      threadServidor.start();

      Thread.sleep(1500); // Aguarda sockets abrirem

      // 2. Teste de Discovery usando Network.Descobridor (Lucas)
      System.out.println("\n[2] Testando Descoberta Automatica via Network.Descobridor (Lucas)...");
      String ipDescoberto = Network.Descobridor.buscarIPServidor();
      System.out.println("    IP Descoberto por Lucas: " + ipDescoberto);
      if (ipDescoberto == null) {
        throw new RuntimeException("FALHA: Network.Descobridor nao encontrou o Servidor EDEN!");
      }
      System.out.println("    >>> SUCESSO: Descoberta automatica funcionando perfeitamente!");

      // 3. Teste do Cliente Lucas conectando via TCP 6789
      System.out.println("\n[3] Testando Comandos TCP do Cliente Lucas...");
      Cliente clienteLucas = new Cliente("127.0.0.1", 6789);

      int portaUdpLucas = 9991;
      // Thread de escuta UDP do Lucas
      Network.RecebedorUDP recebedorLucas = new Network.RecebedorUDP(portaUdpLucas, "127.0.0.1", 7777);
      recebedorLucas.setDaemon(true);
      recebedorLucas.start();

      // Lucas: REGISTER
      Protocol.APDU regLucas = new Protocol.APDU("REGISTER", null, "LucasChaves", null, portaUdpLucas);
      String respRegLucas = clienteLucas.enviarComandoTCP(regLucas);
      System.out.println("    Lucas REGISTER: " + respRegLucas);
      if (!respRegLucas.startsWith("OK")) {
        throw new RuntimeException("FALHA: REGISTER do Lucas falhou: " + respRegLucas);
      }

      // Lucas: JOIN Grupo "Geral"
      Protocol.APDU joinLucas = new Protocol.APDU("JOIN", "Geral", "LucasChaves", null, portaUdpLucas);
      String respJoinLucas = clienteLucas.enviarComandoTCP(joinLucas);
      System.out.println("    Lucas JOIN: " + respJoinLucas);
      if (!respJoinLucas.startsWith("OK")) {
        throw new RuntimeException("FALHA: JOIN do Lucas falhou: " + respJoinLucas);
      }

      // 4. Teste do Cliente EDEN (Iury) conectando via model.ClienteTCP
      System.out.println("\n[4] Testando Cliente EDEN (Iury) conectando ao Servidor...");
      model.ClienteTCP tcpIury = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpIury = new model.ClienteUDP("127.0.0.1", 7777);
      int portaUdpIury = udpIury.getPortaLocal();
      Thread threadUdpIury = new Thread(udpIury);
      threadUdpIury.setDaemon(true);
      threadUdpIury.start();

      utils.InfoUser userIury = new utils.InfoUser("IurySodre", "127.0.0.1", portaUdpIury);

      // Iury: REGISTER
      Protocol.APDU resRegIury = tcpIury.register(userIury);
      System.out.println("    Iury REGISTER: " + resRegIury.getOperacao() + " - " + resRegIury.getTextoMensagem());
      if (!"OK".equals(resRegIury.getOperacao())) {
        throw new RuntimeException("FALHA: REGISTER de Iury falhou: " + resRegIury.getTextoMensagem());
      }

      // Iury: JOIN Grupo "Geral"
      Protocol.APDU resJoinIury = tcpIury.join("Geral", userIury);
      System.out.println("    Iury JOIN: " + resJoinIury.getOperacao() + " - " + resJoinIury.getTextoMensagem());
      if (!"OK".equals(resJoinIury.getOperacao())) {
        throw new RuntimeException("FALHA: JOIN de Iury falhou: " + resJoinIury.getTextoMensagem());
      }

      // Iury: USERS
      Protocol.APDU resUsers = tcpIury.listUsers();
      System.out.println("    Iury LIST USERS: " + resUsers.getTextoMensagem());
      if (!resUsers.getTextoMensagem().contains("LucasChaves") || !resUsers.getTextoMensagem().contains("IurySodre")) {
        throw new RuntimeException("FALHA: Lista de usuarios nao contem ambos os clientes: " + resUsers.getTextoMensagem());
      }

      // Lucas: LIST
      Protocol.APDU listLucas = new Protocol.APDU("LIST", null, "LucasChaves", null, portaUdpLucas);
      String respListLucas = clienteLucas.enviarComandoTCP(listLucas);
      System.out.println("    Lucas LIST: " + respListLucas);
      if (!respListLucas.contains("Geral")) {
        throw new RuntimeException("FALHA: Grupo Geral nao encontrado na lista: " + respListLucas);
      }

      // 5. Teste de Mensageria UDP em Tempo Real
      System.out.println("\n[5] Testando Mensageria UDP em Tempo Real...");

      final boolean[] msgGrupoRecebidaPorIury = {false};
      udpIury.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String grupoOuRemetente, utils.InfoUser remetente, String texto, boolean isPrivada) {
          System.out.println("    [LISTENER IURY] Mensagem recebida de " + remetente.getNome() + " (" + (isPrivada ? "PVT" : grupoOuRemetente) + "): " + texto);
          if ("LucasChaves".equals(remetente.getNome()) && "Ola Iury pelo padrao universal!".equals(texto)) {
            msgGrupoRecebidaPorIury[0] = true;
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
      });

      // Lucas envia mensagem de grupo UDP
      System.out.println("    Lucas enviando mensagem no grupo Geral...");
      Protocol.APDU msgGrupoLucas = new Protocol.APDU("SEND", "Geral", "LucasChaves", "Ola Iury pelo padrao universal!", portaUdpLucas);
      clienteLucas.enviarMensagemUDP(msgGrupoLucas, 7777);

      Thread.sleep(1000);

      if (!msgGrupoRecebidaPorIury[0]) {
        throw new RuntimeException("FALHA: Iury nao recebeu a mensagem de grupo enviada por Lucas!");
      }
      System.out.println("    >>> SUCESSO: Iury recebeu mensagem de grupo do Lucas!");

      // Iury envia SENDPVT privado para Lucas
      System.out.println("    Iury enviando SENDPVT para LucasChaves...");
      udpIury.sendPvt("LucasChaves", userIury, "Oi Lucas, recebido perfeitamente!");

      Thread.sleep(1000);

      // Lucas envia SENDPVT para Iury
      System.out.println("    Lucas enviando SENDPVT para IurySodre...");
      Protocol.APDU pvtLucas = new Protocol.APDU("SENDPVT", "@IurySodre", "LucasChaves", "Tudo certo no privado tambem!", portaUdpLucas, "IurySodre");
      final boolean[] pvtRecebidaPorIury = {false};
      udpIury.setListener(new model.MessageListener() {
        @Override
        public void onMessageReceived(String grupoOuRemetente, utils.InfoUser remetente, String texto, boolean isPrivada) {
          if (isPrivada && "LucasChaves".equals(remetente.getNome())) {
            System.out.println("    [LISTENER IURY] PVT recebido: " + texto);
            pvtRecebidaPorIury[0] = true;
          }
        }
        @Override
        public void onShutdown() {}
        @Override
        public void onUpdateUsers() {}
      });

      clienteLucas.enviarMensagemUDP(pvtLucas, 7777);
      Thread.sleep(1000);

      if (!pvtRecebidaPorIury[0]) {
        throw new RuntimeException("FALHA: Iury nao recebeu a mensagem privada enviada por Lucas!");
      }
      System.out.println("    >>> SUCESSO: Iury recebeu SENDPVT de Lucas!");

      System.out.println("\n==================================================");
      System.out.println("  TODOS OS TESTES PASSARAM COM 100% DE SUCESSO!   ");
      System.out.println("==================================================");
      System.exit(0);

    } catch (Exception e) {
      System.err.println("\n[ERRO NO TESTE] " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}

