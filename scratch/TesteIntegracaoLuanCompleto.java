/**
 * Teste Completo de Integracao e Interoperabilidade: Cliente Luan <-> Servidor EDEN
 */
import Model.Cliente;
import Network.Descobridor;

public class TesteIntegracaoLuanCompleto {
  public static void main(String[] args) {
    try {
      System.setProperty("java.net.preferIPv4Stack", "true");

      // 1. Inicia o Servidor
      model.Servidor servidor = new model.Servidor();
      Thread tServidor = new Thread(() -> servidor.iniciar());
      tServidor.setDaemon(true);
      tServidor.start();
      Thread.sleep(1000);

      // 2. Testa Descoberta UDP (Broadcast Discovery)
      System.out.println("\n--- [TESTE 1] Descoberta UDP via Descobridor ---");
      String ipDescoberto = Descobridor.descobrirServidor();
      System.out.println("IP descoberto pelo cliente: " + ipDescoberto);
      if (ipDescoberto == null) {
        System.err.println("FALHA: Nao descobriu o IP do servidor via broadcast UDP!");
        System.exit(1);
      }

      // 3. Testa Login do Luan
      System.out.println("\n--- [TESTE 2] Login do Cliente Luan ---");
      Cliente luan = new Cliente("Luan", ipDescoberto);
      boolean loginOk = luan.fazerLogin();
      System.out.println("Login status: " + loginOk);
      if (!loginOk) {
        System.err.println("FALHA: Login do Luan rejeitado!");
        System.exit(1);
      }

      // 4. Testa Entrada em Grupo (JOIN)
      System.out.println("\n--- [TESTE 3] Entrar no Grupo (JOIN) ---");
      boolean joinOk = luan.entrarGrupo("devs");
      System.out.println("JOIN status: " + joinOk);
      if (!joinOk) {
        System.err.println("FALHA: JOIN do Luan rejeitado!");
        System.exit(1);
      }

      // 5. Testa Saida do Grupo (LEAVE)
      System.out.println("\n--- [TESTE 4] Sair do Grupo (LEAVE) ---");
      boolean leaveOk = luan.sairGrupo("devs");
      System.out.println("LEAVE status: " + leaveOk);
      if (!leaveOk) {
        System.err.println("FALHA: LEAVE do Luan rejeitado!");
        System.exit(1);
      }

      // 6. Testa Logout (LOGOUT)
      System.out.println("\n--- [TESTE 5] Logout do Cliente Luan (LOGOUT) ---");
      luan.fazerLogout();
      Thread.sleep(500);

      // 7. Testa Relogin com mesmo nome logo apos logout
      System.out.println("\n--- [TESTE 6] Relogin apos LOGOUT (Verificacao de nome duplicado) ---");
      Cliente luan2 = new Cliente("Luan", ipDescoberto);
      boolean reloginOk = luan2.fazerLogin();
      System.out.println("Relogin status: " + reloginOk);
      if (!reloginOk) {
        System.err.println("FALHA: Relogin com mesmo nome rejeitado apos logout!");
        System.exit(1);
      }

      luan.desligarCliente();
      luan2.desligarCliente();

      System.out.println("\n==========================================");
      System.out.println(" TODOS OS TESTES DE INTEGRACAO PASSARAM! ");
      System.out.println("==========================================\n");
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
