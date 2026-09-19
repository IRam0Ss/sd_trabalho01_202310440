/**
 * Teste de Reprodução: Conexão do Cliente do Luan com o Servidor EDEN
 */
public class TesteLuanLogin {
  public static void main(String[] args) {
    try {
      // 1. Inicia Servidor
      model.Servidor servidor = new model.Servidor();
      Thread t = new Thread(() -> servidor.iniciar());
      t.setDaemon(true);
      t.start();
      Thread.sleep(1000);

      // 2. Simula Luan fazendo login exatamente como em Model.Cliente.java do Luan
      Model.Cliente luan = new Model.Cliente("Luan", "127.0.0.1");
      boolean loginAprovado = luan.fazerLogin();
      System.out.println(">>> Resultado do login do Luan: " + loginAprovado);

      if (!loginAprovado) {
        System.err.println("FALHA: Login do Luan foi rejeitado!");
      } else {
        System.out.println("SUCESSO: Login do Luan aprovado!");
      }
      System.exit(loginAprovado ? 0 : 1);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}

