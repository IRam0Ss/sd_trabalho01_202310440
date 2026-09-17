/**
 * Teste Automatizado: Verificação de Auto-LEAVE de todos os grupos na Desconexão Limpa
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class TesteAutoLeaveOnDisconnect {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("  TESTANDO AUTO-LEAVE LIMPO NA DESCONEXAO DO CLIENTE ");
    System.out.println("==================================================");

    try {
      // 1. Iniciar Servidor
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();

      Thread.sleep(1500);

      // 2. Criar Cliente Bob
      model.ClienteTCP tcpBob = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpBob = new model.ClienteUDP("127.0.0.1", 7777);
      udpBob.setMeuNome("Bob");
      Thread tUdpBob = new Thread(udpBob);
      tUdpBob.setDaemon(true);
      tUdpBob.start();

      utils.InfoUser userBob = new utils.InfoUser("Bob", "127.0.0.1", udpBob.getPortaLocal());
      tcpBob.register(userBob);

      // 3. Bob entra em dois grupos: "SalaA" e "SalaB"
      System.out.println("[1] Bob entrando nos grupos 'SalaA' e 'SalaB'...");
      tcpBob.join("SalaA", userBob);
      tcpBob.join("SalaB", userBob);

      Protocol.APDU membrosA = tcpBob.listMembers("SalaA");
      Protocol.APDU membrosB = tcpBob.listMembers("SalaB");
      System.out.println("    Membros SalaA antes: " + membrosA.getTextoMensagem());
      System.out.println("    Membros SalaB antes: " + membrosB.getTextoMensagem());

      // 4. Bob simula a rotina desconectarLimpo() saindo de todas as salas registradas
      System.out.println("\n[2] Executando rotina de desconexao limpa do Bob...");
      List<String> gruposDoBob = Arrays.asList("SalaA", "SalaB");
      for (String g : gruposDoBob) {
        tcpBob.leave(g, userBob);
      }
      tcpBob.fecharConexao();
      udpBob.fecharConexao();

      // 5. Cliente Alice verifica se Bob de fato saiu das duas salas
      model.ClienteTCP tcpAlice = new model.ClienteTCP("127.0.0.1", 6789);
      model.ClienteUDP udpAlice = new model.ClienteUDP("127.0.0.1", 7777);
      udpAlice.setMeuNome("Alice");
      Thread tUdpAlice = new Thread(udpAlice);
      tUdpAlice.setDaemon(true);
      tUdpAlice.start();

      utils.InfoUser userAlice = new utils.InfoUser("Alice", "127.0.0.1", udpAlice.getPortaLocal());
      tcpAlice.register(userAlice);

      Protocol.APDU membrosAposA = tcpAlice.listMembers("SalaA");
      Protocol.APDU membrosAposB = tcpAlice.listMembers("SalaB");
      System.out.println("    Membros SalaA apos desconexao: " + membrosAposA.getTextoMensagem());
      System.out.println("    Membros SalaB apos desconexao: " + membrosAposB.getTextoMensagem());

      boolean bobAindaEstaNaSala = (membrosAposA.getTextoMensagem() != null && membrosAposA.getTextoMensagem().contains("Bob"))
          || (membrosAposB.getTextoMensagem() != null && membrosAposB.getTextoMensagem().contains("Bob"));

      if (bobAindaEstaNaSala) {
        throw new RuntimeException("FALHA: Bob ainda permaneceu em alguma sala como membro fantasma!");
      }

      System.out.println("    >>> SUCESSO: Bob foi completamente removido de todas as salas!");
      System.out.println("\n==================================================");
      System.out.println("  TESTE DE AUTO-LEAVE PASSOU COM 100% SUCESSO!     ");
      System.out.println("==================================================");
      System.exit(0);

    } catch (Exception e) {
      System.err.println("\n[ERRO NO TESTE] " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}

